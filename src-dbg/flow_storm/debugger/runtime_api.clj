(ns flow-storm.debugger.runtime-api

  "Component that implements the api that the debugger
  uses to call the runtime.

  All debugger functionality is implemented agains this API.

  The api is declared as a protocol `RuntimeApiP` and has two possible
  instantiations :
  - `LocalRuntimeApi` directly call functions, since we are on the same process
  - `RemoteRuntimeApi` call funcitons through a websocket and a repl

  All this is implemented runtime part in `flow-storm.runtime.debuggers-api` which is
  the interface exposed by the runtime to debuggers."

  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.utils :as utils :refer [log log-error]]
            [flow-storm.debugger.repl.core :refer [safe-eval-code-str safe-cljs-eval-code-str stop-repl]]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.flows.general :refer [show-message]]
            [clojure.string :as str])
  (:import [java.io Closeable]))

(declare ->LocalRuntimeApi)
(declare ->RemoteRuntimeApi)
(declare rt-api)
(declare api-call)

(def api-call-timeout 4000)

(def ^:dynamic *cache-disabled?* false)

(defstate rt-api
  :start (fn [{:keys [local?]}]
           (let [api-cache (atom {})]
             (if local?

               (->LocalRuntimeApi api-cache)

               (->RemoteRuntimeApi api-cache))))

  :stop (fn []
          (when-let [cc (:close-connection rt-api)]
           (cc))))

(defprotocol RuntimeApiP
  (runtime-config [_])
  (val-pprint [_ v opts])
  (data-window-push-val-data [_ dw-id val-ref extra])
  (get-form [_ form-id])
  (timeline-count [_ flow-id thread-id])
  (timeline-entry [_ flow-id thread-id idx drift])
  (multi-thread-timeline-count [_ flow-id])
  (frame-data [_ flow-id thread-id idx opts])
  (bindings [_ flow-id thread-id idx opts])
  (callstack-tree-root-node [_ flow-id thread-id])
  (callstack-node-childs [_ node])
  (callstack-node-frame [_ node])
  (fn-call-stats [_ flow-id thread-id])

  (interrupt-all-tasks [_])
  (start-task [_ task-id])
  (collect-fn-frames-task [_ flow-id thread-id fn-ns fn-name form-id render-args render-ret?])
  (find-expr-entry-task [_ criteria])
  (total-order-timeline-task [_ opts])
  (thread-prints-task [_ print-cfg])
  (search-collect-timelines-entries-task [_ criteria opts])

  (discard-flow [_ flow-id])

  (def-value [_ var-symb val-ref])
  (tap-value [_ v])

  (get-all-namespaces [_])
  (get-all-vars-for-ns [_ nsname])
  (get-var-meta [_ var-ns var-name])

  (vanilla-instrument-var [_ var-ns var-name opts])
  (vanilla-uninstrument-var [_ var-ns var-name opts])
  (vanilla-instrument-namespaces [_ nsnames opts])
  (vanilla-uninstrument-namespaces [_ nanames opts])
  (modify-storm-instrumentation [_ operation opts])
  (get-storm-instrumentation [_])

  (reload-namespace [_ ns-info])

  (eval-form [_ form-str opts])

  (clear-runtime-state [_])
  (clear-api-cache [_])
  (clear-outputs [_])
  (all-flows-threads [_])
  (flow-threads-info [_ flow-id])
  (unblock-thread [_ thread-id])
  (unblock-all-threads [_])

  (add-breakpoint [_ fq-fn-symb opts])
  (remove-breakpoint [_ fq-fn-symb opts])

  (stack-for-frame [_ flow-id thread-id fn-call-idx])
  (toggle-recording [_])
  (toggle-multi-timeline-recording [_])
  (switch-record-to-flow [_ flow-id])
  (find-fn-call-task [_ fq-fn-call-symb from-idx opts])
  (set-thread-trace-limit [_ limit]))

(defn cached-apply [cache cache-key f args]
  (let [res (get @cache cache-key :flow-storm/cache-miss)]
    (if (or *cache-disabled?*
            (= res :flow-storm/cache-miss))

      ;; miss or disabled, we need to call
      (let [new-res (apply f args)]
        (when (:debug-mode? (dbg-state/debugger-config)) (log (utils/colored-string "CALLED" :red)))
        (swap! cache assoc cache-key new-res)
        new-res)

      ;; hit, return cached
      (do
        (when (:debug-mode? (dbg-state/debugger-config)) (log "CACHED"))
        res))))

(defn api-call
  ([call-type fname args] (api-call call-type fname args {}))
  ([call-type fname args {:keys [cache timeout]}]
   (let [f (case call-type
             :local (requiring-resolve (symbol "flow-storm.runtime.debuggers-api" fname))
             :remote (fn [& args] (websocket/sync-remote-api-request (keyword fname) args)))
         debug-mode? (:debug-mode? (dbg-state/debugger-config))]

     (when debug-mode? (log (format "%s API-CALL %s %s" call-type fname (pr-str args))))

     ;; make the calls in a future so we can have a timeout and don't block the UI thread
     ;; forever
     (let [call-resp-fut (future
                           (if cache

                             (let [cache-key (into [(keyword fname)] args)]
                               (cached-apply cache cache-key f args))

                             (do
                               (when debug-mode? (log (utils/colored-string "CALLED" :red)))
                               (apply f args))))
           call-resp (if timeout
                       (deref call-resp-fut timeout :flow-storm/call-time-out)
                       (deref call-resp-fut))]
       (if (= call-resp :flow-storm/call-time-out)
         (do
           (show-message (format "A call to %s timed out" fname) :warning)
           nil)
         call-resp)))))

(defrecord LocalRuntimeApi [api-cache]

  RuntimeApiP

  (runtime-config [_] (api-call :local "runtime-config" []))
  (val-pprint [_ v opts] (api-call :local "val-pprint" [v opts] {:cache api-cache :timeout api-call-timeout})) ;; CACHED
  (data-window-push-val-data [_ dw-id val-ref extra] (api-call :local "data-window-push-val-data" [dw-id val-ref extra]))
  (get-form [_ form-id] (api-call :local "get-form" [form-id] {:cache api-cache}))  ;; CACHED
  (timeline-count [_ flow-id thread-id] (api-call :local "timeline-count" [flow-id thread-id]))
  (timeline-entry [_ flow-id thread-id idx drift] (api-call :local "timeline-entry" [flow-id thread-id idx drift]))
  (multi-thread-timeline-count [_ flow-id] (api-call :local "multi-thread-timeline-count" [flow-id]))
  (frame-data [_ flow-id thread-id idx opts] (api-call :local "frame-data" [flow-id thread-id idx opts]))
  (bindings [_ flow-id thread-id idx opts] (api-call :local "bindings" [flow-id thread-id idx opts]))
  (callstack-tree-root-node [_ flow-id thread-id] (api-call :local "callstack-tree-root-node" [flow-id thread-id]))
  (callstack-node-childs [_ node] (api-call :local "callstack-node-childs" [node]))
  (callstack-node-frame [_ node] (api-call :local "callstack-node-frame" [node]))
  (fn-call-stats [_ flow-id thread-id] (api-call :local "fn-call-stats" [flow-id thread-id]))

  (collect-fn-frames-task [_ flow-id thread-id fn-ns fn-name form-id render-args render-ret?] (api-call :local "collect-fn-frames-task" [flow-id thread-id fn-ns fn-name form-id render-args render-ret?]))
  (start-task [_ task-id] (api-call :local "start-task" [task-id]))
  (interrupt-all-tasks [_] (api-call :local "interrupt-all-tasks" []))

  (find-expr-entry-task [_ criteria] (api-call :local "find-expr-entry-task" [criteria]))
  (total-order-timeline-task [_ opts] (api-call :local "total-order-timeline-task" [opts]))
  (thread-prints-task [_ print-cfg] (api-call :local "thread-prints-task" [print-cfg]))
  (search-collect-timelines-entries-task [_ criteria opts] (api-call :local "search-collect-timelines-entries-task" [criteria opts]))
  (discard-flow [_ flow-id] (api-call :local "discard-flow" [flow-id]))
  (def-value [_ var-symb val-ref] (api-call :local "def-value" [(or (namespace var-symb) "user") (name var-symb) val-ref]))
  (tap-value [_ vref] (api-call :local "tap-value" [vref]))

  (get-all-namespaces [_] (mapv (comp str ns-name) (all-ns)))
  (get-all-vars-for-ns [_ nsname] (->> (ns-interns (symbol nsname)) keys (map str)))
  (get-var-meta [_ var-ns var-name]
    (-> (meta (resolve (symbol var-ns var-name)))
        (update :ns (comp str ns-name))))

  (vanilla-instrument-var [_ var-ns var-name opts]
    (api-call :local "vanilla-instrument-var" [:clj (symbol var-ns var-name) opts]))

  (vanilla-uninstrument-var [_ var-ns var-name opts]
    (api-call :local "vanilla-uninstrument-var" [:clj (symbol var-ns var-name) opts]))

  (vanilla-instrument-namespaces [_ nsnames {:keys [profile] :as opts}]
    (let [disable-set (utils/disable-from-profile profile)]
      (api-call :local "vanilla-instrument-namespaces" [:clj nsnames (assoc opts :disable disable-set)])))

  (vanilla-uninstrument-namespaces [_ nsnames opts]
    (api-call :local "vanilla-uninstrument-namespaces" [:clj nsnames opts]))

  (modify-storm-instrumentation [_ operation opts]
    (api-call :local "modify-storm-instrumentation" [:clj operation opts]))

  (get-storm-instrumentation [_]
    (api-call :local "get-storm-instrumentation" [:clj]))

  (reload-namespace [_ ns-info]
    (require (symbol (:namespace-name ns-info)) :reload))

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

  (clear-runtime-state [_]
    (api-call :local "clear-runtime-state" []))

  (clear-api-cache [_]
    (reset! api-cache {}))

  (clear-outputs [_]
    (api-call :local "clear-outputs" []))

  (flow-threads-info [_ flow-id]
    (api-call :local "flow-threads-info" [flow-id]))

  (unblock-thread [_ thread-id]
    (api-call :local "unblock-thread" [thread-id]))

  (unblock-all-threads [_]
    (api-call :local "unblock-all-threads" []))

  (add-breakpoint [_ fq-fn-symb opts]
    (api-call :local "add-breakpoint!" [fq-fn-symb opts]))

  (remove-breakpoint [_ fq-fn-symb opts]
    (api-call :local "remove-breakpoint!" [fq-fn-symb opts]))

  (all-flows-threads [_]
    (api-call :local "all-flows-threads" []))

  (stack-for-frame [_ flow-id thread-id fn-call-idx]
    (api-call :local "stack-for-frame" [flow-id thread-id fn-call-idx]))

  (toggle-recording [_]
    (api-call :local "toggle-recording" []))

  (toggle-multi-timeline-recording [_]
    (api-call :local "toggle-multi-timeline-recording" []))

  (switch-record-to-flow [_ flow-id]
    (api-call :local "switch-record-to-flow" [flow-id]))

  (find-fn-call-task [_ fq-fn-call-symb from-idx opts]
    (api-call :local "find-fn-call-task" [fq-fn-call-symb from-idx opts]))

  (set-thread-trace-limit [_ limit]
    (api-call :local "set-thread-trace-limit" [limit])))

;;;;;;;;;;;;;;;;;;;;;;
;; For Clojure repl ;;
;;;;;;;;;;;;;;;;;;;;;;

(defrecord RemoteRuntimeApi [api-cache]

  RuntimeApiP

  (runtime-config [_] (api-call :remote "runtime-config" []))
  (val-pprint [_ v opts] (api-call :remote "val-pprint" [v opts] {:cache api-cache :timeout api-call-timeout})) ;; CACHED
  (data-window-push-val-data [_ dw-id val-ref extra] (api-call :remote "data-window-push-val-data" [dw-id val-ref extra]))
  (get-form [_ form-id] (api-call :remote "get-form" [form-id] {:cache api-cache}))  ;; CACHED
  (timeline-count [_ flow-id thread-id] (api-call :remote "timeline-count" [flow-id thread-id]))
  (timeline-entry [_ flow-id thread-id idx drift] (api-call :remote "timeline-entry" [flow-id thread-id idx drift]))
  (multi-thread-timeline-count [_ flow-id] (api-call :remote "multi-thread-timeline-count" [flow-id]))
  (frame-data [_ flow-id thread-id idx opts] (api-call :remote "frame-data" [flow-id thread-id idx opts]))
  (bindings [_ flow-id thread-id idx opts] (api-call :remote "bindings" [flow-id thread-id idx opts]))
  (callstack-tree-root-node [_ flow-id thread-id] (api-call :remote "callstack-tree-root-node" [flow-id thread-id]))
  (callstack-node-childs [_ node] (api-call :remote "callstack-node-childs" [node]))
  (callstack-node-frame [_ node] (api-call :remote "callstack-node-frame" [node]))
  (fn-call-stats [_ flow-id thread-id] (api-call :remote "fn-call-stats" [flow-id thread-id]))

  (collect-fn-frames-task [_ flow-id thread-id fn-ns fn-name form-id render-args render-ret?] (api-call :remote "collect-fn-frames-task" [flow-id thread-id fn-ns fn-name form-id render-args render-ret?]))
  (start-task [_ task-id] (api-call :remote "start-task" [task-id]))
  (interrupt-all-tasks [_] (api-call :remote "interrupt-all-tasks" []))

  (find-expr-entry-task [_ criteria] (api-call :remote "find-expr-entry-task" [criteria]))
  (total-order-timeline-task [_ opts] (api-call :remote "total-order-timeline-task" [opts]))
  (thread-prints-task [_ print-cfg] (api-call :remote "thread-prints-task" [print-cfg]))
  (search-collect-timelines-entries-task [_ criteria opts] (api-call :remote "search-collect-timelines-entries-task" [criteria opts]))
  (discard-flow [_ flow-id] (api-call :remote "discard-flow" [flow-id]))
  (def-value [_ var-symb val-ref]
    (case (dbg-state/env-kind)
      :clj (api-call :remote "def-value" [(or (namespace var-symb) "user") (name var-symb) val-ref])
      :cljs (safe-cljs-eval-code-str (format "(def %s (flow-storm.runtime.values/deref-value (flow-storm.types/make-value-ref %d)))"
                                             (name var-symb)
                                             (:vid val-ref))
                                     (or (namespace var-symb) "cljs.user"))))
  (tap-value [_ vref] (api-call :remote "tap-value" [vref]))

  (get-all-namespaces [_]
    (case (dbg-state/env-kind)
      :clj  (api-call :remote "all-namespaces" [:clj])
      :cljs (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/all-namespaces :cljs %s)" (:repl.cljs/build-id (dbg-state/repl-config))))))

  (get-all-vars-for-ns [_ nsname]
    (case (dbg-state/env-kind)
      :clj  (api-call :remote "all-vars-for-namespace" [:clj (symbol nsname)])
      :cljs (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/all-vars-for-namespace :cljs '%s %s)" nsname (:repl.cljs/build-id (dbg-state/repl-config))))))

  (get-var-meta [_ var-ns var-name]
    (case (dbg-state/env-kind)
      :clj  (api-call :remote "get-var-meta" [:clj (symbol var-ns var-name)])
      :cljs (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/get-var-meta :cljs '%s/%s %s)" var-ns var-name {:build-id (:repl.cljs/build-id (dbg-state/repl-config))}))))

  (vanilla-instrument-var [_ var-ns var-name opts]
    (case (dbg-state/env-kind)
      :clj (api-call :remote "vanilla-instrument-var" [:clj (symbol var-ns var-name) opts])
      :cljs (let [opts (assoc opts :build-id (:repl.cljs/build-id (dbg-state/repl-config)))]
              (show-message "FlowStorm ClojureScript single var instrumentation is pretty limited. You can instrument them only once, and the only way of uninstrumenting them is by reloading your page or restarting your node process. Also deep instrumentation is missing some cases. So for most cases you are going to be better with [un]instrumenting entire namespaces." :warning)
              (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/vanilla-instrument-var :cljs '%s/%s %s)" var-ns var-name opts)))))

  (vanilla-uninstrument-var [_ var-ns var-name opts]
    (case (dbg-state/env-kind)
      :clj (api-call :remote "vanilla-uninstrument-var" [:clj (symbol var-ns var-name) opts])
      :cljs (let [_opts (assoc opts :build-id (:repl.cljs/build-id (dbg-state/repl-config)))]
              (show-message "FlowStorm currently can't uninstrument single vars in ClojureScript. You can only [un]instrument entire namespaces. If you want to get rid of the current vars instrumentation please reload your browser page, or restart your node process." :warning)
              #_(safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/uninstrument-var :cljs '%s/%s %s)" var-ns var-name opts)))))

  (vanilla-instrument-namespaces [_ nsnames {:keys [profile] :as opts}]
    (let [opts (assoc opts :disable (utils/disable-from-profile profile))]
      (case (dbg-state/env-kind)
        :cljs (let [opts (assoc opts :build-id (:repl.cljs/build-id (dbg-state/repl-config)))]
                (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/vanilla-instrument-namespaces :cljs %s %s)" (into #{} nsnames) opts)))
        :clj (api-call :remote "vanilla-instrument-namespaces" [:clj (into #{} nsnames) opts]))))

  (vanilla-uninstrument-namespaces [_ nsnames opts]
    (case (dbg-state/env-kind)
      :cljs (let [opts (assoc opts :build-id (:repl.cljs/build-id (dbg-state/repl-config)))]
              (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/vanilla-uninstrument-namespaces :cljs %s %s)" (into #{} nsnames) opts)))

      ;; for Clojure just call the api
      :clj (api-call :remote "vanilla-uninstrument-namespaces" [:clj (into #{} nsnames) opts])))

  (modify-storm-instrumentation [_ operation opts]
    (case (dbg-state/env-kind)
      :cljs (let [opts (assoc opts :build-id (:repl.cljs/build-id (dbg-state/repl-config)))]
              (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/modify-storm-instrumentation :cljs %s %s)" operation opts)))

      ;; for Clojure just call the api
      :clj (api-call :remote "modify-storm-instrumentation" [:clj operation opts])))

  (get-storm-instrumentation [_]
    (case (dbg-state/env-kind)
      :cljs (safe-eval-code-str (format "(flow-storm.runtime.debuggers-api/get-storm-instrumentation :cljs)"))

      ;; for Clojure just call the api
      :clj (api-call :remote "get-storm-instrumentation" [:clj])))

  (reload-namespace [_ ns-info]
    (let [reload-code (format "(require '%s :reload)" (:namespace-name ns-info))]
      (case (dbg-state/env-kind)
        :cljs (safe-cljs-eval-code-str reload-code "cljs.user")
        :clj  (safe-eval-code-str reload-code))))

  (eval-form [this form-str {:keys [instrument? instrument-options var-name ns]}]
    (let [var-meta (when var-name (select-keys (get-var-meta this ns var-name) [:file :column :end-column :line :end-line]))
          form-expr (if instrument?
                      (format "(flow-storm.api/instrument* %s %s)" (pr-str instrument-options) form-str)
                      form-str)
          expr-res (case (dbg-state/env-kind)
                     :clj  (safe-eval-code-str form-expr ns)
                     :cljs (safe-cljs-eval-code-str form-expr ns) )]
      (when (and (= :clj (dbg-state/env-kind))
                 var-meta)
        ;; for vars restore the meta attributes that get lost when we re eval from the repl
        (safe-eval-code-str (format "(alter-meta! #'%s/%s merge %s)" ns var-name (pr-str var-meta))))
      expr-res))

  (clear-runtime-state [_]
    (api-call :remote "clear-runtime-state" []))

  (clear-api-cache [_]
    (reset! api-cache {}))

  (clear-outputs [_]
    (api-call :remote "clear-outputs" []))

  (flow-threads-info [_ flow-id]
    (api-call :remote "flow-threads-info" [flow-id]))

  (unblock-thread [_ thread-id]
    (api-call :remote "unblock-thread" [thread-id]))

  (unblock-all-threads [_]
    (api-call :remote "unblock-all-threads" []))

  (add-breakpoint [_ fq-fn-symb opts]
    (case (dbg-state/env-kind)
      :clj (api-call :remote "add-breakpoint!" [fq-fn-symb opts])
      :cljs (show-message "Operation not supported for ClojureScript" :warning)))

  (remove-breakpoint [_ fq-fn-symb opts]
    (api-call :remote "remove-breakpoint!" [fq-fn-symb opts]))

  (all-flows-threads [_]
    (api-call :remote "all-flows-threads" []))

  (stack-for-frame [_ flow-id thread-id fn-call-idx]
    (api-call :remote "stack-for-frame" [flow-id thread-id fn-call-idx]))

  (toggle-recording [_]
    (api-call :remote "toggle-recording" []))

  (toggle-multi-timeline-recording [_]
    (api-call :remote "toggle-multi-timeline-recording" []))

  (switch-record-to-flow [_ flow-id]
    (api-call :remote "switch-record-to-flow" [flow-id]))

  (find-fn-call-task [_ fq-fn-call-symb from-idx opts]
    (api-call :remote "find-fn-call-task" [fq-fn-call-symb from-idx opts]))

  (set-thread-trace-limit [_ limit]
    (api-call :remote "set-thread-trace-limit" [limit]))

  Closeable
  (close [_] (stop-repl))
  )
