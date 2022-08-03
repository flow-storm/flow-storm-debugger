(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.utils :refer [log-error]]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.events-processor :as events-processor]
            [flow-storm.debugger.trace-processor :as trace-processor]
            [flow-storm.debugger.values]
            [flow-storm.debugger.target-commands :as target-commands]
            [flow-storm.debugger.websocket :as websocket]
            [mount.core :as mount]))

(def flow-storm-core-ns 'flow-storm.core)

(def local-debugger-mount-vars

  "All defstate vars should be registered here.
  This exists so `start-debugger`, `stop-debugger` don't mess with other
  states when the debugger is used inside a application that uses mount."

  [#'flow-storm.debugger.ui.state-vars/long-running-task-thread
   #'flow-storm.debugger.ui.state-vars/ui-objs
   #'flow-storm.debugger.ui.state-vars/flows-ui-objs
   #'flow-storm.debugger.state/state
   #'flow-storm.debugger.state/fn-call-stats-map
   #'flow-storm.debugger.state/flow-thread-indexers
   #'flow-storm.debugger.ui.main/ui])

(def remote-debugger-mount-vars

  "Same as `local-debugger-mount-vars` but for remote debugger"

  (into local-debugger-mount-vars
        [#'flow-storm.debugger.websocket/websocket-server]))

(defn stop-debugger []
  (-> (mount/only (into local-debugger-mount-vars remote-debugger-mount-vars))
      (mount/stop)))

(defn setup-commands-executor [{:keys [local?]}]

  (let [run-command (if local?
                      (fn [method args-map]
                        (require [flow-storm-core-ns])
                        (let [runc (resolve (symbol (str flow-storm-core-ns) "run-command"))]
                          (runc nil method args-map)))

                      (fn [method args-map]
                        (let [p (promise)]
                          (websocket/async-command-request method
                                                           args-map
                                                           (fn [ret-val]
                                                             ;; TODO: add remote error reporting
                                                             (deliver p [nil [nil ret-val]])))
                          @p)))]
    (alter-var-root #'target-commands/run-command
                    (constantly
                     (fn run-command-fn [method args-map & [callback]]
                       ;; run the function on a different thread so we don't block the ui
                       ;; while running commands
                       (.start
                        (Thread.
                         (fn []
                           (ui-main/set-in-progress true)
                           (let [res (run-command method args-map)
                                 [_ cmd-res] res]
                             (ui-main/set-in-progress false)

                             (if (= cmd-res :error)

                               (log-error "Error running command")

                               (let [[_ ret-val] cmd-res]
                                 (when callback
                                   (callback ret-val)))))))))))))

(defn start-debugger

  "Run a standalone debugger.

   `config` should be a map containing :
        - `:local?` when false will also start a websocket server and listen for connections
        - `:theme` can be one of `:light`, `:dark` or `:auto`
        - `:styles` a string path to a css file if you want to override some started debugger styles

   When `:local?` is false you can also provide `:host` and `:port` for the web socket server."

  [{:keys [local?] :as config}]

  (setup-commands-executor config)

  #_(.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn []
              (log "Shutting down VM")
              (stop-debugger)
              (log "Done. Bye."))))

  (if local?

    ;; start components for local debugging
    (-> (mount/with-args config)
        (mount/only local-debugger-mount-vars)
        (mount/start))

    ;; else, start components for remote debugging
    (-> (mount/with-args (assoc config
                                :event-dispatcher events-processor/process-event
                                :trace-dispatcher trace-processor/dispatch-trace
                                :show-error ui-main/show-error))
        (mount/only remote-debugger-mount-vars)
        (mount/start))))
