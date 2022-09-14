(ns flow-storm.debugger.runtime-api
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.utils :as utils :refer [log-error]]
            [flow-storm.debugger.nrepl :as dbg-nrepl]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.config :refer [config]]
            [clojure.string :as str]
            [clojure.repl :as clj-repl])
  (:import [java.io Closeable]))

(declare ->LocalRuntimeApi)
(declare ->RemoteRuntimeApi)
(declare rt-api)

(def ^:dynamic *cache-enabled?* true)

(defstate rt-api
  :start (let [{:keys [local?]} config
               cache (atom {})]
           (if local?

             (->LocalRuntimeApi cache)

             (let [env-kind (if (#{:shadow} (:repl-type config))
                              :cljs
                              :clj)
                   {:keys [repl-eval close-connection]} (dbg-nrepl/connect config)]
               (->RemoteRuntimeApi repl-eval env-kind close-connection cache))))
  :stop (when-let [cc (:close-connection rt-api)]
          (cc)))

(defprotocol RuntimeApiP
  (val-pprint [_ v opts])
  (shallow-val [_ v])
  (get-form [_ flow-id thread-id form-id])
  (timeline-count [_ flow-id thread-id])
  (timeline-entry [_ flow-id thread-id idx])
  (frame-data [_ flow-id thread-id idx])
  (bindings [_ flow-id thread-id idx])
  (callstack-tree-root-node [_ flow-id thread-id])
  (callstack-node-childs [_ node])
  (callstack-node-frame [_ node])
  (fn-call-stats [_ flow-id thread-id])
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id])
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts])
  (discard-flow [_ flow-id])

  (get-var-form-str [_ var-ns var-name])
  (eval-form [_ form-str opts])
  (def-value [_ v-name v])
  (get-all-namespaces [_])
  (get-all-vars-for-ns [_ nsname])
  (get-var-meta [_ var-ns var-name])
  (instrument-namespaces [_ nsnames profile])
  (uninstrument-namespaces [_ nanames])
  (interrupt-all-tasks [_])
  (clear-values-references [_]))

;; TODO: deduplicate the code of `cached-local-call` and `cached-remote-call`
(defmacro cached-local-call [cache f-symb & args]
  (let [cache-key (into [(name f-symb)] args)]
    `(let [f# (requiring-resolve '~(symbol "flow-storm.runtime.debuggers-api" (name f-symb)))
           res# (get (deref ~cache) ~cache-key :flow-storm/cache-miss)]
       (if (and *cache-enabled?* (= res# :flow-storm/cache-miss))

         ;; miss
         (let [new-res# (f# ~@args)]
           (swap! ~cache assoc ~cache-key new-res#)
           new-res#)

         ;; hit
         res#))))

(defn cached-remote-call [cache f-key args]
  (let [cache-key (into [f-key] args)
        res (get @cache cache-key :flow-storm/cache-miss)]
    (if (and *cache-enabled?* (= res :flow-storm/cache-miss))

      ;; miss
      (let [new-res (websocket/sync-remote-api-request f-key args)]
        (swap! cache assoc cache-key new-res)
        new-res)

      ;; hit
      res)))

(defrecord LocalRuntimeApi [cache]

  RuntimeApiP

  (val-pprint [_ v opts] (cached-local-call cache val-pprint v opts))
  (shallow-val [_ v] (cached-local-call cache shallow-val v))
  (get-form [_ flow-id thread-id form-id] (cached-local-call cache get-form flow-id thread-id form-id))
  (timeline-count [_ flow-id thread-id] (cached-local-call cache timeline-count flow-id thread-id))
  (timeline-entry [_ flow-id thread-id idx] (cached-local-call cache timeline-entry flow-id thread-id idx))
  (frame-data [_ flow-id thread-id idx] (cached-local-call cache frame-data flow-id thread-id idx))
  (bindings [_ flow-id thread-id idx] (cached-local-call cache bindings flow-id thread-id idx))
  (callstack-tree-root-node [_ flow-id thread-id] (cached-local-call cache callstack-tree-root-node flow-id thread-id))
  (callstack-node-childs [_ node] (cached-local-call cache callstack-node-childs node))
  (callstack-node-frame [_ node] (cached-local-call cache callstack-node-frame node))
  (fn-call-stats [_ flow-id thread-id] (cached-local-call cache fn-call-stats flow-id thread-id))
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id] (cached-local-call cache find-fn-frames-light flow-id thread-id fn-ns fn-name form-id))
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts] (cached-local-call cache search-next-frame-idx flow-id thread-id query-str from-idx opts))
  (discard-flow [_ flow-id] (cached-local-call cache discard-flow flow-id))

  (get-var-form-str [_ var-ns var-name] (clj-repl/source-fn (symbol var-ns var-name)))
  (eval-form [_ form-str {:keys [instrument? instrument-options var-name ns]}]
    (let [ns-to-eval (find-ns (symbol ns))]
      (binding [*ns* ns-to-eval]

        (let [form (read-string
                    (if instrument?
                      (format "(flow-storm.runtime.debuggers-api/instrument* %s %s)" (pr-str instrument-options) form-str)
                      form-str))
              [v vmeta] (when var-name
                          (let [v (find-var (symbol ns var-name))]
                            [v (meta v)]))]

          ;; Don't eval the form in the same UI thread or
          ;; it will deadlock because the same thread can generate
          ;; a event that will be waiting for the UI thread
          (.start
           (Thread.
            (fn []
              (binding [*ns* ns-to-eval]
                (eval form)))))

          ;; when we evaluate a function from the repl we lose all meta
          ;; so when re-evaluating a var (probably a function) store and restore its meta
          (when v (reset-meta! v vmeta))))))

  (def-value [_ v-name vref] (cached-local-call cache def-value v-name vref))
  (get-all-namespaces [_] (mapv (comp str ns-name) (all-ns)))
  (get-all-vars-for-ns [_ nsname] (->> (ns-interns (symbol nsname)) keys (map str)))

  (get-var-meta [_ var-ns var-name]
    (-> (meta (resolve (symbol var-ns var-name)))
        (update :ns (comp str ns-name))))

  (instrument-namespaces [_ nsnames profile]
    (let [disable-set-str (if (= profile :light)
                            #{:expr :anonymous-fn :binding}
                            #{})]
      (cached-local-call cache instrument-namespaces nsnames {:disable disable-set-str})))
  (uninstrument-namespaces [_ nsnames] (cached-local-call cache uninstrument-namespaces nsnames))

  (interrupt-all-tasks [_] (cached-local-call cache interrupt-all-tasks))
  (clear-values-references [_] (cached-local-call cache clear-values-references))
  )

(defmulti make-repl-expression (fn [env-kind fn-symb & _] [env-kind fn-symb]))

;;;;;;;;;;;;;;;;;;;;;;
;; For Clojure repl ;;
;;;;;;;;;;;;;;;;;;;;;;

(defmethod make-repl-expression [:clj 'get-var-form-str] [_ _ var-ns var-name] (format "(clojure.repl/source-fn (symbol %s %s))" (pr-str var-ns) (pr-str var-name)))
(defmethod make-repl-expression [:clj 'get-all-namespaces] [_ _] "(mapv (comp str ns-name) (all-ns))")
(defmethod make-repl-expression [:clj 'get-all-vars-for-ns] [_ _ nsname] (format "(->> (ns-interns '%s) keys (mapv str))" nsname))
(defmethod make-repl-expression '[:clj get-var-meta] [_ _ var-ns var-name] (format "(-> (meta #'%s/%s) (update :ns (comp str ns-name)))" var-ns var-name))

(defmethod make-repl-expression '[:clj instrument-namespaces]
  [_ _ nsnames profile]
  (let [nss-str (str/join " " nsnames)
        disable-set-str (if (= profile :light)
                          "#{:expr :anonymous-fn :binding}"
                          "#{}")]
    (format "(flow-storm.api/instrument-forms-for-namespaces #{%s} {:disable %s})" nss-str disable-set-str)))

(defmethod make-repl-expression '[:clj uninstrument-namespaces]
  [_ _ nsnames]
  (let [nss-str (str/join " " nsnames)]
    (format "(flow-storm.api/uninstrument-forms-for-namespaces #{%s})" nss-str)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; For ClojureScript repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod make-repl-expression [:cljs 'get-var-form-str] [_ _ var-ns var-name] (format "(dbg-api/cljs-source-fn %s/%s)" var-ns var-name))
(defmethod make-repl-expression [:cljs 'get-all-namespaces] [_ _] "(dbg-api/cljs-get-all-ns)")
(defmethod make-repl-expression [:cljs 'get-all-vars-for-ns] [_ _ nsname] (format "(dbg-api/cljs-get-ns-interns %s)" nsname))
(defmethod make-repl-expression [:cljs 'get-var-meta] [_ _ var-ns var-name] (format "(meta #'%s/%s)" var-ns var-name))

(defn- re-eval-all-ns-forms [eval-fn ns-names eval-opts]
  (let [all-ns-forms (eval-fn (format "(dbg-api/cljs-sorted-namespaces-sources %s)" (mapv symbol ns-names)))]
    (doseq [[ns-name ns-forms] all-ns-forms]
      (doseq [form-str ns-forms]
        (eval-form rt-api form-str (assoc eval-opts :ns ns-name))))))

(defrecord RemoteRuntimeApi [eval-str-expr env-kind close-connection cache]

  RuntimeApiP

  (val-pprint [_ ref-id opts] (cached-remote-call cache :val-pprint [ref-id opts]))
  (shallow-val [_ ref-id] (cached-remote-call cache :shallow-val [ref-id]))
  (get-form [_ flow-id thread-id form-id] (cached-remote-call cache :get-form [flow-id thread-id form-id]))
  (timeline-count [_ flow-id thread-id] (cached-remote-call cache :timeline-count [flow-id thread-id]))
  (timeline-entry [_ flow-id thread-id idx] (cached-remote-call cache :timeline-entry [flow-id thread-id idx]))
  (frame-data [_ flow-id thread-id idx] (cached-remote-call cache :frame-data [flow-id thread-id idx]))
  (bindings [_ flow-id thread-id idx] (cached-remote-call cache :bindings [flow-id thread-id idx]))
  (callstack-tree-root-node [_ flow-id thread-id] (cached-remote-call cache :callstack-tree-root-node [flow-id thread-id]))
  (callstack-node-childs [_ node] (cached-remote-call cache :callstack-node-childs [node]))
  (callstack-node-frame [_ node] (cached-remote-call cache :callstack-node-frame [node]))
  (fn-call-stats [_ flow-id thread-id] (cached-remote-call cache :fn-call-stats [flow-id thread-id]))
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id] (cached-remote-call cache :find-fn-frames-light [flow-id thread-id fn-ns fn-name form-id]))
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts] (cached-remote-call cache :search-next-frame-idx [flow-id thread-id query-str from-idx opts]))
  (discard-flow [_ flow-id] (cached-remote-call cache :discard-flow [flow-id]))
  (def-value [_ v-name v] (cached-remote-call cache :def-value [v-name v]))

  (get-var-form-str [_ var-ns var-name] (eval-str-expr env-kind (make-repl-expression env-kind 'get-var-form-str var-ns var-name)))

  (eval-form [this form-str {:keys [instrument? instrument-options var-name ns]}]
    (let [var-meta (when var-name (select-keys (get-var-meta this ns var-name) [:file :column :end-column :line :end-line]))
          form-expr (if instrument?
                      (format "(flow-storm.runtime.debuggers-api/instrument* %s %s)" (pr-str instrument-options) form-str)
                      form-str)
          expr-res (eval-str-expr env-kind (format "(do %s nil)" form-expr) ns)]
      (when var-meta
        ;; for vars restore the meta attributes that get lost when we re eval from the repl
        (eval-str-expr env-kind (format "(alter-meta! #'%s/%s merge %s)" ns var-name (pr-str var-meta))))
      expr-res))

  (get-all-namespaces [_] (eval-str-expr env-kind (make-repl-expression env-kind 'get-all-namespaces )))
  (get-all-vars-for-ns [_ nsname] (eval-str-expr env-kind (make-repl-expression env-kind 'get-all-vars-for-ns nsname)))
  (get-var-meta [_ var-ns var-name] (eval-str-expr env-kind (make-repl-expression env-kind 'get-var-meta var-ns var-name)))

  (instrument-namespaces [_ nsnames profile]
    (let [opts (if (= profile :light)
                 {:disable #{:expr :binding :anonymous-fn}}
                 {})]
      (case env-kind
        :cljs (re-eval-all-ns-forms (partial eval-str-expr env-kind) nsnames {:instrument? true
                                                                              :instrument-options opts})

       :clj (cached-remote-call cache :instrument-namespaces [nsnames opts]))))

  (uninstrument-namespaces [_ nsnames]
    (case env-kind
      :cljs (re-eval-all-ns-forms (partial eval-str-expr env-kind) nsnames {:instrument? false})

      ;; for Clojure just call the api
      :clj (cached-remote-call cache :uninstrument-namespaces [nsnames])))

  (interrupt-all-tasks [_] (cached-remote-call cache :interrupt-all-tasks []))

  (clear-values-references [_] (cached-remote-call cache :clear-values-references []))


  Closeable
  (close [_] (close-connection))
  )

(defn get-and-eval-form [api var-ns var-name instrument?]
  (let [var-form-str (get-var-form-str api var-ns var-name)]

    (if var-form-str
      (eval-form rt-api var-form-str {:instrument? instrument?
                                      :ns var-ns
                                      :var-name var-name})

      (log-error (utils/format "Couldn't retrieve the source for #'%s/%s" var-ns var-name) ))))
