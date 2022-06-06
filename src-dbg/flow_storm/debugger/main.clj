(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.websocket :as websocket]
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
                        (fn run-command-fn [_ method args-map]
                          ;; run the function on a different thread so we don't block the ui
                          ;; while running commands
                          (.start (Thread. (fn [] (run-command method args-map))))))))

    (do

      (websocket/start-websocket-server
       (assoc config
              :trace-dispatcher trace-processor/remote-dispatch-trace
              :show-error ui-main/show-error))

      (alter-var-root #'target-commands/run-command
                        (constantly
                         ;; remote command executor (via websockets)
                         (fn run-command-fn [flow-id method args-map]
                           (let [command-conn (:flow/origin-connection (dbg-state/get-flow flow-id))]
                             (websocket/async-command-request command-conn method args-map (fn [_])))))))))
