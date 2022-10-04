(ns flow-storm.debugger.runtime-api
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.utils :as utils :refer [log-error log]]
            [flow-storm.debugger.ui.state-vars :refer [show-message]]
            [flow-storm.debugger.repl.core :refer [safe-eval-code-str stop-repl]]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.config :refer [config debug-mode]]
            [clojure.string :as str])
  (:import [java.io Closeable]))

(declare ->LocalRuntimeApi)
(declare ->RemoteRuntimeApi)
(declare rt-api)

(def ^:dynamic *cache-disabled?* false)

(defstate rt-api
  :start (let [{:keys [local? env-kind]} config
               api-cache (atom {})
               repl-cache (atom {})]
           (log "[Starting Runtime Api subsystem]")
           (if local?

             (->LocalRuntimeApi api-cache repl-cache)

             (->RemoteRuntimeApi env-kind api-cache repl-cache)))

  :stop (do
          (log "[Stopping Runtime Api subsystem]")
          (when-let [cc (:close-connection rt-api)]
           (cc))))

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
  (clear-values-references [_])
  (clear-api-cache [_]))

(defn cached-apply [cache cache-key f args]
  (let [res (get @cache cache-key :flow-storm/cache-miss)]
    (if (or *cache-disabled?*
            (= res :flow-storm/cache-miss))

      ;; miss or disabled, we need to call
      (let [new-res (apply f args)]
        (when debug-mode (log (utils/colored-string "CALLED" :red)))
        (swap! cache assoc cache-key new-res)
        new-res)

      ;; hit, return cached
      (do
        (when debug-mode (log "CACHED"))
        res))))

(defn api-call
  ([call-type fn-name args] (api-call call-type nil fn-name args))
  ([call-type cache fname args]
   (let [f (case call-type
             :local (requiring-resolve (symbol "flow-storm.runtime.debuggers-api" fname))
             :remote (fn [& args] (websocket/sync-remote-api-request (keyword fname) args)))]

     (when debug-mode (log (format "%s API-CALL %s %s" call-type fname (pr-str args))))

     (if cache

       (let [cache-key (into [(keyword fname)] args)]
         (cached-apply cache cache-key f args))

       (do
         (when debug-mode (log (utils/colored-string "CALLED" :red)))
         (apply f args))))))

(defn eval-code-str
  ([form] (eval-code-str nil form nil))
  ([form ns] (eval-code-str nil form ns))
  ([cache form ns]
   (let [cache-key form]
     (if cache
       (let [val (get @cache cache-key :flow-storm/cache-miss)]
         (if (= val :flow-storm/cache-miss)

           ;; re eval the form if it is a miss
           (let [val (safe-eval-code-str form ns)]
             (swap! cache assoc cache-key val)
             val)

           ;; if a hit, return the cached val
           val))

       (safe-eval-code-str form ns)))))


(defrecord LocalRuntimeApi [api-cache repl-cache]

  RuntimeApiP

  (val-pprint [_ v opts] (api-call :local api-cache "val-pprint" [v opts])) ;; CACHED
  (shallow-val [_ v] (api-call :local api-cache "shallow-val" [v]))  ;; CACHED
  (get-form [_ flow-id thread-id form-id] (api-call :local api-cache "get-form" [flow-id thread-id form-id]))  ;; CACHED
  (timeline-count [_ flow-id thread-id] (api-call :local "timeline-count" [flow-id thread-id]))
  (timeline-entry [_ flow-id thread-id idx] (api-call :local "timeline-entry" [flow-id thread-id idx]))
  (frame-data [_ flow-id thread-id idx] (api-call :local "frame-data" [flow-id thread-id idx]))
  (bindings [_ flow-id thread-id idx] (api-call :local "bindings" [flow-id thread-id idx]))
  (callstack-tree-root-node [_ flow-id thread-id] (api-call :local "callstack-tree-root-node" [flow-id thread-id]))
  (callstack-node-childs [_ node] (api-call :local "callstack-node-childs" [node]))
  (callstack-node-frame [_ node] (api-call :local "callstack-node-frame" [node]))
  (fn-call-stats [_ flow-id thread-id] (api-call :local "fn-call-stats" [flow-id thread-id]))
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id] (api-call :local "find-fn-frames-light" [flow-id thread-id fn-ns fn-name form-id]))
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts] (api-call :local "search-next-frame-idx" [flow-id thread-id query-str from-idx opts]))
  (discard-flow [_ flow-id] (api-call :local "discard-flow" [flow-id]))
  (def-value [_ v-name vref] (api-call :local "def-value" [v-name vref]))

  (get-var-form-str [_ var-ns var-name] (utils/source-fn (symbol var-ns var-name)))
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

                (try
                  (eval form)
                  (catch Exception ex
                    (log-error "Error instrumenting form" ex)
                    (if (and (.getCause ex) (str/includes? (.getMessage (.getCause ex)) "Method code too large!"))
                      (show-message "The form you are trying to instrument exceeds the method limit after instrumentation. Try intrumenting it without bindings." :error)
                      (show-message (.getMessage ex) :error))))

                ;; when we evaluate a function from the repl we lose all meta
                ;; so when re-evaluating a var (probably a function) store and restore its meta
                (when v (reset-meta! v vmeta))))))))))


  (get-all-namespaces [_] (mapv (comp str ns-name) (all-ns)))
  (get-all-vars-for-ns [_ nsname] (->> (ns-interns (symbol nsname)) keys (map str)))

  (get-var-meta [_ var-ns var-name]
    (-> (meta (resolve (symbol var-ns var-name)))
        (update :ns (comp str ns-name))))

  (instrument-namespaces [_ nsnames profile]
    (let [disable-set (utils/disable-from-profile profile)]
      (api-call :local "instrument-namespaces" [nsnames {:disable disable-set}])))

  (uninstrument-namespaces [_ nsnames]
    (api-call :local "uninstrument-namespaces" [nsnames]))

  (interrupt-all-tasks [_]
    (api-call :local "interrupt-all-tasks" []))

  (clear-values-references [_]
    (api-call :local "clear-values-references" []))

  (clear-api-cache [_]
    (reset! api-cache {})))

(defmulti make-repl-expression (fn [env-kind fn-symb & _] [env-kind fn-symb]))

(defmethod make-repl-expression :default [& _] "")

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
        disable-set-str (pr-str (utils/disable-from-profile profile))]
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

(defn- re-eval-all-ns-forms [eval-fn ns-names {:keys [instrument?] :as eval-opts}]
  (let [all-ns-forms (eval-fn (format "(dbg-api/cljs-sorted-namespaces-sources %s)" (mapv symbol ns-names)))]
    (doseq [[ns-name ns-forms] all-ns-forms]
      (doseq [form-str ns-forms]
        (log (format "%s ns: %s form: %s"
                     (if instrument? "Instrumenting" "Uninstrumenting")
                     ns-name
                     (utils/elide-string form-str 50)))
        (eval-form rt-api form-str (assoc eval-opts :ns ns-name))))))

(defrecord RemoteRuntimeApi [env-kind api-cache repl-cache]

  RuntimeApiP

  (val-pprint [_ v opts] (api-call :remote api-cache "val-pprint" [v opts])) ;; CACHED
  (shallow-val [_ v] (api-call :remote api-cache "shallow-val" [v]))  ;; CACHED
  (get-form [_ flow-id thread-id form-id] (api-call :remote api-cache "get-form" [flow-id thread-id form-id]))  ;; CACHED
  (timeline-count [_ flow-id thread-id] (api-call :remote "timeline-count" [flow-id thread-id]))
  (timeline-entry [_ flow-id thread-id idx] (api-call :remote "timeline-entry" [flow-id thread-id idx]))
  (frame-data [_ flow-id thread-id idx] (api-call :remote "frame-data" [flow-id thread-id idx]))
  (bindings [_ flow-id thread-id idx] (api-call :remote "bindings" [flow-id thread-id idx]))
  (callstack-tree-root-node [_ flow-id thread-id] (api-call :remote "callstack-tree-root-node" [flow-id thread-id]))
  (callstack-node-childs [_ node] (api-call :remote "callstack-node-childs" [node]))
  (callstack-node-frame [_ node] (api-call :remote "callstack-node-frame" [node]))
  (fn-call-stats [_ flow-id thread-id] (api-call :remote "fn-call-stats" [flow-id thread-id]))
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id] (api-call :remote "find-fn-frames-light" [flow-id thread-id fn-ns fn-name form-id]))
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts] (api-call :remote "search-next-frame-idx" [flow-id thread-id query-str from-idx opts]))
  (discard-flow [_ flow-id] (api-call :remote "discard-flow" [flow-id]))
  (def-value [_ v-name vref] (api-call :remote "def-value" [v-name vref]))

  ;; caching this not for performace reasons but because after we re-eval a form once we don't have
  ;; access to its source anymore because the meta get lost and can't be restored in ClojureScript
  (get-var-form-str [_ var-ns var-name] (eval-code-str repl-cache (make-repl-expression env-kind 'get-var-form-str var-ns var-name) nil)) ;; CACHED

  (eval-form [this form-str {:keys [instrument? instrument-options var-name ns]}]
    (let [var-meta (when var-name (select-keys (get-var-meta this ns var-name) [:file :column :end-column :line :end-line]))
          form-expr (if instrument?
                      (format "(flow-storm.runtime.debuggers-api/instrument* %s %s)" (pr-str instrument-options) form-str)
                      form-str)
          expr-res (eval-code-str (format "(do %s nil)" form-expr) ns)]
      (when var-meta
        ;; for vars restore the meta attributes that get lost when we re eval from the repl
        (eval-code-str (format "(alter-meta! #'%s/%s merge %s)" ns var-name (pr-str var-meta))))
      expr-res))

  (get-all-namespaces [_] (eval-code-str (make-repl-expression env-kind 'get-all-namespaces)))
  (get-all-vars-for-ns [_ nsname] (eval-code-str (make-repl-expression env-kind 'get-all-vars-for-ns nsname)))
  (get-var-meta [_ var-ns var-name] (eval-code-str (make-repl-expression env-kind 'get-var-meta var-ns var-name)))

  (instrument-namespaces [_ nsnames profile]
    (let [opts {:disable (utils/disable-from-profile profile)}]
      (case env-kind
        :cljs (re-eval-all-ns-forms eval-code-str nsnames {:instrument? true
                                                           :instrument-options opts})

        :clj (api-call :remote "instrument-namespaces" [nsnames opts]))))

  (uninstrument-namespaces [_ nsnames]
    (case env-kind
      :cljs (re-eval-all-ns-forms eval-code-str nsnames {:instrument? false})

      ;; for Clojure just call the api
      :clj (api-call :remote "uninstrument-namespaces" [nsnames])))

  (interrupt-all-tasks [_]
    (api-call :remote "interrupt-all-tasks" []))

  (clear-values-references [_]
    (api-call :remote "clear-values-references" []))

  (clear-api-cache [_]
    (reset! api-cache {}))

  Closeable
  (close [_] (stop-repl))
  )

(defn get-and-eval-form [api var-ns var-name instrument?]
  (let [var-form-str (get-var-form-str api var-ns var-name)]

    (if var-form-str
      (eval-form rt-api var-form-str {:instrument? instrument?
                                      :ns var-ns
                                      :var-name var-name})

      (let [err-msg (utils/format "Couldn't retrieve the source for #'%s/%s. It is a known issue in ClojureScript if you are trying to instrument a individual var after instrumenting its namespace. Instrument the entire namespace again or use #trace instead." var-ns var-name)]

        (log-error err-msg)
        (show-message err-msg :error)))))
