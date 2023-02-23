(ns flow-storm.debugger.runtime-api
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.utils :as utils :refer [log log-error]]
            [flow-storm.debugger.repl.core :refer [safe-eval-code-str safe-cljs-eval-code-str stop-repl]]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.ui.state-vars :refer [show-message]]
            [clojure.string :as str]
            [flow-storm.debugger.config :refer [config debug-mode]])
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

  (def-value [_ v-name v])
  (tap-value [_ v])

  (get-all-namespaces [_])
  (get-all-vars-for-ns [_ nsname])
  (get-var-meta [_ var-ns var-name])

  (instrument-var [_ var-ns var-name opts])
  (uninstrument-var [_ var-ns var-name opts])
  (instrument-namespaces [_ nsnames opts])
  (uninstrument-namespaces [_ nanames opts])

  (eval-form [_ form-str opts])

  (interrupt-all-tasks [_])
  (clear-values-references [_])
  (clear-api-cache [_])

  (flow-threads-info [_ flow-id]))

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
  (tap-value [_ vref] (api-call :local "tap-value" [vref]))

  (get-all-namespaces [_] (mapv (comp str ns-name) (all-ns)))
  (get-all-vars-for-ns [_ nsname] (->> (ns-interns (symbol nsname)) keys (map str)))
  (get-var-meta [_ var-ns var-name]
    (-> (meta (resolve (symbol var-ns var-name)))
        (update :ns (comp str ns-name))))

  (instrument-var [_ var-ns var-name opts]
    (api-call :local "instrument-var" [:clj (symbol var-ns var-name) opts]))

  (uninstrument-var [_ var-ns var-name opts]
    (api-call :local "uninstrument-var" [:clj (symbol var-ns var-name) opts]))

  (instrument-namespaces [_ nsnames {:keys [profile] :as opts}]
    (let [disable-set (utils/disable-from-profile profile)]
      (api-call :local "instrument-namespaces" [:clj nsnames (assoc opts :disable disable-set)])))

  (uninstrument-namespaces [_ nsnames opts]
    (api-call :local "uninstrument-namespaces" [:clj nsnames opts]))

  (eval-form [_ form-str {:keys [instrument? instrument-options var-name ns]}]
    (let [ns-to-eval (find-ns (symbol ns))]

      (binding [*ns* ns-to-eval]

        (let [form (read-string
                    (if instrument?
                      (format "(flow-storm.api/instrument* %s %s)" (pr-str instrument-options) form-str)
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

  (interrupt-all-tasks [_]
    (api-call :local "interrupt-all-tasks" []))

  (clear-values-references [_]
    (api-call :local "clear-values-references" []))

  (clear-api-cache [_]
    (reset! api-cache {}))

  (flow-threads-info [_ flow-id]
    (api-call :local "flow-threads-info" [flow-id])))

;;;;;;;;;;;;;;;;;;;;;;
;; For Clojure repl ;;
;;;;;;;;;;;;;;;;;;;;;;

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
  (tap-value [_ vref] (api-call :remote "tap-value" [vref]))

  (get-all-namespaces [_]
    (case env-kind
      :clj  (api-call :remote "all-namespaces" [:clj])
      :cljs (safe-eval-code-str (format "(dbg-api/all-namespaces :cljs %s)" (:build-id config)))))

  (get-all-vars-for-ns [_ nsname]
    (case env-kind
      :clj  (api-call :remote "all-vars-for-namespace" [:clj (symbol nsname)])
      :cljs (safe-eval-code-str (format "(dbg-api/all-vars-for-namespace :cljs '%s %s)" nsname (:build-id config)))))

  (get-var-meta [_ var-ns var-name]
    (case env-kind
      :clj  (api-call :remote "get-var-meta" [:clj (symbol var-ns var-name)])
      :cljs (safe-eval-code-str (format "(dbg-api/get-var-meta :cljs '%s/%s %s)" var-ns var-name (select-keys config [:build-id])))))

  (instrument-var [_ var-ns var-name opts]
    (case env-kind
      :clj (api-call :remote "instrument-var" [:clj (symbol var-ns var-name) opts])
      :cljs (let [opts (assoc opts :build-id (:build-id config))]
              (show-message "FlowStorm ClojureScript single var instrumentation is pretty limited. You can instrument them only once, and the only way of uninstrumenting them is by reloading your page or restarting your node process. Also deep instrumentation is missing some cases. So for most cases you are going to be better with [un]instrumenting entire namespaces." :warning)
              (safe-eval-code-str (format "(dbg-api/instrument-var :cljs '%s/%s %s)" var-ns var-name opts)))))

  (uninstrument-var [_ var-ns var-name opts]
    (case env-kind
      :clj (api-call :remote "uninstrument-var" [:clj (symbol var-ns var-name) opts])
      :cljs (let [_opts (assoc opts :build-id (:build-id config))]
              (show-message "FlowStorm currently can't uninstrument single vars in ClojureScript. You can only [un]instrument entire namespaces. If you want to get rid of the current vars instrumentation please reload your browser page, or restart your node process." :warning)
              #_(safe-eval-code-str (format "(dbg-api/uninstrument-var :cljs '%s/%s %s)" var-ns var-name opts)))))

  (instrument-namespaces [_ nsnames {:keys [profile] :as opts}]
    (let [opts (assoc opts :disable (utils/disable-from-profile profile))]
      (case env-kind
        :cljs (let [opts (assoc opts :build-id (:build-id config))]
                (safe-eval-code-str (format "(dbg-api/instrument-namespaces :cljs %s %s)" (into #{} nsnames) opts)))
        :clj (api-call :remote "instrument-namespaces" [:clj nsnames opts]))))

  (uninstrument-namespaces [_ nsnames opts]
    (case env-kind
      :cljs (let [opts (assoc opts :build-id (:build-id config))]
              (safe-eval-code-str (format "(dbg-api/uninstrument-namespaces :cljs %s %s)" (into #{} nsnames) opts)))

      ;; for Clojure just call the api
      :clj (api-call :remote "uninstrument-namespaces" [:clj nsnames opts])))

  (eval-form [this form-str {:keys [instrument? instrument-options var-name ns]}]
    (let [var-meta (when var-name (select-keys (get-var-meta this ns var-name) [:file :column :end-column :line :end-line]))
          form-expr (if instrument?
                      (format "(flow-storm.api/instrument* %s %s)" (pr-str instrument-options) form-str)
                      form-str)
          expr-res (case env-kind
                     :clj  (safe-eval-code-str form-expr ns)
                     :cljs (safe-cljs-eval-code-str form-expr ns) )]
      (when (and (= :clj env-kind)
                 var-meta)
        ;; for vars restore the meta attributes that get lost when we re eval from the repl
        (safe-eval-code-str (format "(alter-meta! #'%s/%s merge %s)" ns var-name (pr-str var-meta))))
      expr-res))

  (interrupt-all-tasks [_]
    (api-call :remote "interrupt-all-tasks" []))

  (clear-values-references [_]
    (api-call :remote "clear-values-references" []))

  (clear-api-cache [_]
    (reset! api-cache {}))

  (flow-threads-info [_ flow-id]
    (api-call :remote "flow-threads-info" [flow-id]))

  Closeable
  (close [_] (stop-repl))
  )
