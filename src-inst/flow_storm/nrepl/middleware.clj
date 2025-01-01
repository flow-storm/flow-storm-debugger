(ns flow-storm.nrepl.middleware
  (:require [flow-storm.runtime.debuggers-api :as debuggers-api]
            [flow-storm.types :refer [make-value-ref value-ref?]]
            [flow-storm.runtime.outputs :as rt-outputs]
            [nrepl.misc :refer [response-for] :as misc]
            [nrepl.middleware :as middleware :refer [set-descriptor!]]
            [nrepl.transport :as t]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [flow-storm.form-pprinter :as form-pprinter]
            [nrepl.middleware.caught :as caught :refer [wrap-caught]]
            [nrepl.middleware.print :refer [wrap-print]]
            [nrepl.middleware.interruptible-eval :refer [*msg*]])

  (:import [nrepl.transport Transport]))

(defn value-ref->int [m k]
  (if-let [vr (get m k)]
    (cond

      (value-ref? vr)
      (update m k :vid)

      ;; this is because whatever reads values from the js runtime
      ;; (the piggieback path) doesn't use the data readers in data_readers.clj
      ;; so it will read flow-storm.types/value as default tagged-litterals
      (and (tagged-literal? vr) (= 'flow-storm.types/value-ref (:tag vr)))
      (update m k :form)

      :else m)
    m))

(defn trace-count [{:keys [flow-id thread-id]}]
  {:code `(debuggers-api/timeline-count ~(or flow-id 0)
                                        ~thread-id)
   :post-proc (fn [cnt]
                {:status :done
                 :trace-cnt cnt})})

(defn find-fn-call [{:keys [flow-id fq-fn-symb from-idx backward]}]
  {:code `(debuggers-api/find-fn-call-sync ~flow-id
                                           (symbol ~fq-fn-symb)
                                           ~from-idx
                                           ~(Boolean/parseBoolean backward))
   :post-proc (fn [fn-call]
                {:status :done
                 :fn-call (value-ref->int fn-call :fn-args)})})

(defn find-flow-fn-call [{:keys [flow-id]}]
  {:code `(debuggers-api/find-flow-fn-call ~(if (number? flow-id) flow-id nil))
   :post-proc (fn [fn-call]
                {:status :done
                 :fn-call (value-ref->int fn-call :fn-args)})})

(defn get-form [{:keys [form-id]}]
  {:code `(debuggers-api/get-form ~form-id)
   :post-proc (fn [form]
                (let [{:keys [form/id form/form form/ns form/def-kind form/file form/line]} form
                      file-path (when-let [f (when (and file
                                                        (not= file "NO_SOURCE_PATH"))
                                               (if (or (str/starts-with? file "/")          ;; it is a unix absolute path
                                                       (re-find #"^[a-zA-Z]:[\\/].+" file)) ;; it is a windows absolute path
                                                 (io/file file)

                                                 ;; if form/file is not an absolute path then it is a resource
                                                 (io/resource file)))]
                                  (.getPath f))]
                  {:status :done
                   :form {:id       id
                          :ns       ns
                          :def-kind def-kind
                          :line     line
                          :pprint   (form-pprinter/code-pprint form)
                          :file     file-path}}))})

(defn timeline-entry [{:keys [flow-id thread-id idx drift]}]
  {:code `(debuggers-api/timeline-entry ~(if (number? flow-id) flow-id nil)
                                        ~thread-id
                                        ~idx
                                        ~(keyword drift))
   :post-proc (fn [entry]
                {:status :done
                 :entry (-> entry
                            (value-ref->int :fn-args)
                            (value-ref->int :result))})})

(defn frame-data [{:keys [flow-id thread-id fn-call-idx]}]
  {:code `(debuggers-api/frame-data ~(if (number? flow-id) flow-id nil)
                                    ~thread-id
                                    ~fn-call-idx
                                    {})
   :post-proc (fn [frame]
                {:status :done
                 :frame (-> frame
                            (value-ref->int :args-vec)
                            (value-ref->int :ret))})})

(defn pprint-val-ref [{:keys [val-ref print-level print-length print-meta pprint]}]
  {:code `(debuggers-api/val-pprint (make-value-ref ~val-ref)
                                    {:print-length ~print-length
                                     :print-level  ~print-level
                                     :print-meta?  ~(Boolean/parseBoolean print-meta)
                                     :pprint?      ~(Boolean/parseBoolean pprint)})
   :post-proc (fn [pprint-str]
                {:status :done
                 :pprint pprint-str})})

(defn bindings [{:keys [flow-id thread-id idx all-frame]}]
  {:code `(debuggers-api/bindings ~(if (number? flow-id) flow-id nil)
                                  ~thread-id
                                  ~idx
                                  {:all-frame? ~(Boolean/parseBoolean all-frame)})
   :post-proc (fn [bindings]
                {:status :done
                 :bindings (reduce-kv (fn [r k vref]
                                        (assoc r k (:vid vref)))
                                      {}
                                      bindings)})})
(defn toggle-recording [_]
  {:code `(debuggers-api/toggle-recording)
   :post-proc (fn [_]
                {:status :done})})

(defn clear-recordings [_]
  {:code `(debuggers-api/clear-flows)
   :post-proc (fn [_]
                {:status :done})})

(defn recorded-functions [_]
  {:code `(debuggers-api/all-fn-call-stats)
   :post-proc (fn [stats]
                {:status :done
                 :functions (mapv (fn [[fq-fn-name cnt]]
                                    {:fq-fn-name fq-fn-name :cnt cnt})
                                  stats)})})

(defn cljs-transport
  [{:keys [^Transport transport] :as msg} post-proc]
  (reify Transport
    (recv [_this]
      (.recv transport))

    (recv [_this timeout]
      (.recv transport timeout))

    (send [this response]

      (cond (contains? response :value)
            (let [rsp-val (:value response)
                  ;; this is HACKY, but the ClojureScript middleware can
                  ;; return (:value response) as a Map/Vector/etc or the thing as a String
                  ;; if it contains things like [#flow-storm.types/value-ref 5]
                  rsp-val (if (string? rsp-val)
                            (read-string rsp-val)
                            rsp-val)
                  processed-val (post-proc rsp-val)
                  rsp (response-for msg processed-val)]
              (.send transport rsp))

            :else (.send transport response))
      this)))

(defn process-msg [next-handler {:keys [^Transport transport] :as msg} msg-proc-fn cljs?]
  (let [{:keys [code post-proc]} (msg-proc-fn msg)]

    (if cljs?
      ;; ClojureScript handling
      (let [tr (cljs-transport msg post-proc)
            cljs-code (pr-str code)]
        (next-handler (assoc msg
                             :transport tr
                             :op "eval"
                             :code cljs-code
                             :ns "cljs.user")))

      ;; Clojure handling
      (let [rsp (response-for msg (post-proc (eval code)))]
        (t/send transport rsp)))))

(defn- wrap-transport
  [transport op msg-id {:keys [on-eval on-out on-err]}]
  (reify Transport
    (recv [_this]
      (t/recv transport))
    (recv [_this timeout]
      (t/recv transport timeout))
    (send [this resp]
      (cond
        (:out resp)                                                       (on-out (:out resp))
        (:err resp)                                                       (on-err (:err resp))
        (and (= op "eval") (= msg-id (:id resp)) (contains? resp :value)) (on-eval (:value resp)))
      (.send transport resp)
      this)))

(def cider-piggieback?
  (try (require 'cider.piggieback) true
       (catch Throwable _ false)))

(def nrepl-piggieback?
  (try (require 'piggieback.core) true
       (catch Throwable _ false)))

(defn try-piggieback
  "If piggieback is loaded, returns `#'cider.piggieback/wrap-cljs-repl`, or
  false otherwise."
  []
  (cond
    cider-piggieback? (resolve 'cider.piggieback/wrap-cljs-repl)
    nrepl-piggieback? (resolve 'piggieback.core/wrap-cljs-repl)
    :else false))

(defn- maybe-piggieback
  [descriptor descriptor-key]
  (if-let [piggieback (try-piggieback)]
    (update-in descriptor [descriptor-key] #(set (conj % piggieback)))
    descriptor))

(defn expects-piggieback
  "If piggieback is loaded, returns the descriptor with piggieback's
  `wrap-cljs-repl` handler assoc'd into its `:expects` set."
  [descriptor]
  (maybe-piggieback descriptor :expects))

(defn wrap-flow-storm
  "Middleware that provides flow-storm functionality "
  [next-handler]
  (fn [{:keys [op id] :as msg}]
    (let [piggieback? (or cider-piggieback? nrepl-piggieback?)]
      (case op
        "flow-storm-trace-count"        (process-msg next-handler msg trace-count        piggieback?)
        "flow-storm-find-fn-call"       (process-msg next-handler msg find-fn-call       piggieback?)
        "flow-storm-find-flow-fn-call"  (process-msg next-handler msg find-flow-fn-call  piggieback?)
        "flow-storm-get-form"           (process-msg next-handler msg get-form           piggieback?)
        "flow-storm-timeline-entry"     (process-msg next-handler msg timeline-entry     piggieback?)
        "flow-storm-frame-data"         (process-msg next-handler msg frame-data         piggieback?)
        "flow-storm-pprint"             (process-msg next-handler msg pprint-val-ref     piggieback?)
        "flow-storm-toggle-recording"   (process-msg next-handler msg toggle-recording   piggieback?)
        "flow-storm-clear-recordings"   (process-msg next-handler msg clear-recordings   piggieback?)
        "flow-storm-bindings"           (process-msg next-handler msg bindings           piggieback?)
        "flow-storm-recorded-functions" (process-msg next-handler msg recorded-functions piggieback?)

        ;; if the message is not for us let it flow but with a transport
        ;; we control, so we can handle writes to out, err and eval results
        (let [msg (if piggieback?
                    ;; don't do anything for ClojureScript yet
                    msg

                    ;; update transport to capture *out*, *err* and eval resluts
                    (update msg :transport (fn [transport]
                                             (wrap-transport transport
                                                             op
                                                             id
                                                             {:on-eval rt-outputs/handle-eval-result
                                                              :on-out  rt-outputs/handle-out-write
                                                              :on-err  rt-outputs/handle-err-write}))))]
          ;; TODO: remove binding *msg*
          ;; this isn't needed anymore for nrepl >= 1.3.1 (https://github.com/nrepl/nrepl/issues/363)
          ;; Let's leave it for some time since doesn't seams to hurt and allows people to use *out* and *err*
          ;; with 1.3.0
          (binding [*msg* msg]
            (next-handler msg)))))))

(defn expects-shadow-cljs-middleware

  "If shadow-cljs middleware is on the classpath, make sure we set our middleware
  before it."

  [descriptor]
  (if-let [shadow-middleware-var (try
                                   (requiring-resolve 'shadow.cljs.devtools.server.nrepl/middleware)
                                   (catch Throwable _ false))]
    (update descriptor :expects #(set (conj % shadow-middleware-var)))
    descriptor))

(def descriptor
  (expects-piggieback
   (expects-shadow-cljs-middleware
    {:requires #{"clone" #'wrap-caught #'wrap-print}
     :expects #{"eval"}
     :handles {"flow-storm-trace-count"
               {:doc "Get the traces count for a thread"
                :requires {"flow-id" "The id of the flow"
                           "thread-id" "The id of the thread"}
                :optional {}
                :returns {"trace-cnt" "A number with the size of the recorded timeline"}}

               "flow-storm-find-fn-call"
               {:doc "Find the first FnCall for a symbol"
                :requires {"flow-id" "The id of the flow"
                           "fq-fn-symb" "The Fully qualified function symbol"
                           "from-idx" "The starting timeline idx to search from"
                           "backward" "When true, searches for a fn-call by walking the timeline backwards"}
                :optional {}
                :returns {"fn-call" "A map like {:keys [fn-name fn-ns form-id fn-args fn-call-idx idx parent-idx ret-idx flow-id thread-id]}"}}

               "flow-storm-find-flow-fn-call"
               {:doc "Find the first FnCall for a flow"
                :requires {"flow-id" "The id of the flow"}
                :optional {}
                :returns {"fn-call" "A map like {:keys [fn-name fn-ns form-id fn-args fn-call-idx idx parent-idx ret-idx]}"}}

               "flow-storm-get-form"
               {:doc "Return a registered form"
                :requires {"form-id" "The id of the form"}
                :optional {}
                :returns {"form" "A map with {:keys [id ns def-kind line pprint file]}"}}

               "flow-storm-timeline-entry"
               {:doc "Return a timeline entry"
                :requires {"flow-id" "The flow-id for the entry"
                           "thread-id" "The thread-id for the entry"
                           "idx" "The current timeline idx"
                           "drift" "The drift, one of next-out next-over prev-over next prev at"}
                :optional {}
                :returns {"entry" (str "One of : "
                                       "FnCall   {:keys [type fn-name fn-ns form-id fn-args fn-call-idx idx parent-idx ret-idx]}"
                                       "Expr     {:keys [type coord result fn-call-idx idx]}"
                                       "FnReturn {:keys [type coord result fn-call-idx idx]}")}}

               "flow-storm-frame-data"
               {:doc "Return a frame for a fn-call index"
                :requires {"flow-id" "The flow-id for the entry"
                           "thread-id" "The thread-id for the entry"
                           "fn-call-idx" "The fn-call timeline idx"}
                :optional {}
                :returns {"frame" "A map with {:keys [fn-ns fn-name args-vec ret form-id fn-call-idx parent-fn-call-idx]}"}}

               "flow-storm-pprint"
               {:doc "Return a pretty printing for a value reference id"
                :requires {"val-ref" "The value reference id"
                           "print-length" "A *print-length* val for pprint"
                           "print-level" "A *print-level* val for pprint"
                           "print-meta" "A *print-meta* val for pprint"
                           "pprint" "When true will pretty print, otherwise just print"}
                :optional {}
                :returns {"pprint" "A map with {:keys [val-str val-type]}"}}

               "flow-storm-bindings"
               {:doc "Return all bindings for the provided idx"
                :requires {"flow-id" "The id of the flow"
                           "thread-id" "The id of the thread"
                           "idx" "The current timeline index"
                           "all-frame" "When true return all the bindings for the frame, not just the current visible ones"}
                :optional {}
                :returns {"bindings" "A map with {:keys [bind-symb val-ref-id]}"}}

               "flow-storm-toggle-recording"
               {:doc "Toggles recording on/off"
                :requires {}
                :optional {}
                :returns {}}

               "flow-storm-clear-recordings"
               {:doc "Clears all flows recordings"
                :requires {}
                :optional {}
                :returns {}}

               "flow-storm-recorded-functions"
               {:doc "Return all the functions there are recordings for"
                :requires {}
                :optional {}
                :returns {"functions" "A collection of maps like {:keys [fq-fn-name cnt]}"}}}})))

(set-descriptor! #'wrap-flow-storm descriptor)


(comment
  ;; For testing middlewares
  #_:clj-kondo/ignore
  (let [flow-id 0
        thread-id 30
        p (promise)
        h (wrap-flow-storm (constantly true))]
    (with-redefs [t/send (fn [_ rsp] (deliver p rsp))]

      #_(h {:op "flow-storm-trace-count"
          :flow-id flow-id
          :thread-id thread-id})

      (h {:flow-id flow-id
          :op "flow-storm-find-fn-call"
          :fq-fn-symb "dev-tester/factorial"})

      #_(h {:op "flow-storm-find-flow-fn-call"
          :flow-id flow-id})

      #_(h {:op "flow-storm-get-form"
            :form-id -798068730})

      #_(h {:op "flow-storm-timeline-entry"
          :flow-id flow-id
          :thread-id thread-id
          :idx 3
            :drift "at"})

      #_(h {:op "flow-storm-frame-data"
          :flow-id flow-id
          :thread-id thread-id
            :fn-call-idx 0})

      #_(h {:op "flow-storm-pprint"
            :val-ref 5})

      #_(h {:op "flow-storm-bindings"
            :flow-id flow-id
            :thread-id thread-id
            :idx 8
            :all-frame "true"})

      #_(h {:op "flow-storm-toggle-recording"})

      #_(h {:op "flow-storm-recorded-functions"})

      #_(h {:op "flow-storm-clear-recordings"})

      (deref p 1000 :no-response)))
  )
