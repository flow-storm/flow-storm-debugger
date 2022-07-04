(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.events-processor :as events-processor]
            [flow-storm.debugger.trace-processor :as trace-processor]
            [flow-storm.debugger.trace-types]
            [flow-storm.debugger.target-commands :as target-commands]))

(def flow-storm-core-ns 'flow-storm.core)

(defn stop-debugger []
  (websocket/stop-websocket-servers))

(defn start-debugger [{:keys [local?] :as config}]
  (dbg-state/init-state!)
  (ui-vars/reset-state!)
  (ui-main/start-ui config)

  (if local?

    ;; set up the command-executor

    (let [_ (require [flow-storm-core-ns])
          run-command (resolve (symbol (str flow-storm-core-ns) "run-command"))]
      (alter-var-root #'target-commands/run-command
                       (constantly
                        ;; local command executor (just call command functions)
                        (fn run-command-fn [method args-map & [callback]]
                          ;; run the function on a different thread so we don't block the ui
                          ;; while running commands
                          (.start (Thread. (fn []
                                             (ui-main/set-in-progress true)
                                             (let [[_ [_ ret-val]] (run-command nil method args-map)]
                                               (ui-main/set-in-progress false)
                                               (when callback
                                                 (callback ret-val))))))))))

    (do

      (websocket/start-websocket-server
       (assoc config
              :event-dispatcher events-processor/process-event
              :trace-dispatcher trace-processor/remote-dispatch-trace
              :show-error ui-main/show-error
              :on-connection-open (fn [conn] (reset! dbg-state/remote-connection conn))))

      (alter-var-root #'target-commands/run-command
                        (constantly
                         ;; remote command executor (via websockets)
                         (fn run-command-fn [method args-map & [callback]]
                           (ui-main/set-in-progress true)
                           (websocket/async-command-request @dbg-state/remote-connection
                                                            method
                                                            args-map
                                                            (fn [ret-val]
                                                              (ui-main/set-in-progress false)
                                                              (when callback
                                                                (callback ret-val))))))))))
