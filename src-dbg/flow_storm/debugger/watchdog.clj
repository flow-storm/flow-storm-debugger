(ns flow-storm.debugger.watchdog
  (:require [flow-storm.state-management :as state-management :refer [defstate]]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.repl.core :as repl-core]
            [flow-storm.debugger.config :refer [config]]
            [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.ui.utils :as ui-utils]))

(declare watchdog)
(declare start-watchdog)
(declare stop-watchdog)

(def repl-watchdog-interval 3000)
(def websocket-watchdog-interval 1000)

(defstate watchdog
  :start (fn [_] (start-watchdog))
  :stop  (fn [] (stop-watchdog)))

(defn clear-ui []
  (taps-screen/clear-all-taps)

  (doseq [fid (dbg-state/all-flows-ids)]
    (dbg-state/remove-flow fid)
    (ui-utils/run-later (flows-screen/remove-flow fid)))

  (ui-utils/run-later (browser-screen/clear-instrumentation-list)))

(defn websocket-reconnect-loop []
  (utils/log "[WATCHDOG] starting websocket reconnect loop")
  (let [ws-reconnect-thread
        (Thread.
         (fn []
           (loop []
             (when-not (.isInterrupted (Thread/currentThread))
               (let [r (websocket/sync-remote-api-request :ping [] 1000)
                     ws-ok? (= :pong r)]

                 (if ws-ok?

                   (do
                     (ui-main/set-conn-status-lbl :runtime :ok)
                     (when-let [on-reconnect (:on-websocket-reconnect config)]
                       (on-reconnect)))

                   (do
                     (try
                       ;; if we lost the connection to the runtime, and we are connected to a repl,
                       ;; we also lost runtime initialization, so we need to re init the runtime
                       (when (:connect-to-repl? config)
                         (repl-core/init-repl config))

                       ;; if we started, lets wait some time before checking again
                       (Thread/sleep 5000)
                       (catch Exception _
                         (utils/log (format "Couldn't restart the websocket server, retrying in %d ms" websocket-watchdog-interval))
                         (Thread/sleep websocket-watchdog-interval)))
                     (recur))))))))
        ws-reconnect-interrupt (fn [] (.interrupt ws-reconnect-thread))]

    (.setName ws-reconnect-thread "FlowStorm Websocket reconnect")
    (.start ws-reconnect-thread)

    ws-reconnect-interrupt))

(defn repl-watchdog-loop []
  ;; start the repl watchdog loop
  ;; this will check for the repl connection to be ready and also
  ;; that everything as been loaded
  (when (:connect-to-repl? config)
    (let [repl-watchdog-thread
          (Thread.
           (fn []
             (utils/log "Starting the repl watchdog loop")
             (try
               (loop []
                 (let [repl-ok? (try
                                  (= :watch-dog-ping (repl-core/eval-code-str ":watch-dog-ping"))
                                  (catch clojure.lang.ExceptionInfo ei
                                    (let [{:keys [error/type] :as exd} (ex-data ei)]
                                      (utils/log (format "[WATCHDOG] error executing ping. %s" exd))
                                      (not= type :repl/socket-exception))))]

                   (if repl-ok?

                     (ui-main/set-conn-status-lbl :repl :ok)

                     (do
                       (ui-main/set-conn-status-lbl :repl :fail)

                       (utils/log "[WATCHDOG] repl looks down, trying to reconnect ...")
                       (state-management/stop {:only [#'flow-storm.debugger.repl.core/repl]})
                       (try
                         (state-management/start {:only [#'flow-storm.debugger.repl.core/repl]})
                         (catch Exception e
                           (utils/log (format "Couldn't restart repl (%s), retrying in %d ms" (.getMessage e) repl-watchdog-interval))))))
                   (Thread/sleep repl-watchdog-interval))
                 (recur))
               (catch java.lang.InterruptedException _
                 (utils/log "FlowStorm Repl Watchdog thread interrupted")))))
          repl-watchdog-interrupt (fn [] (.interrupt repl-watchdog-thread))]

      (.setName repl-watchdog-thread "FlowStorm Repl Watchdog")
      (.start repl-watchdog-thread)
      repl-watchdog-interrupt)))

(defn start-watchdog []
  (websocket/register-event-callback :connection-open (fn [] (ui-main/set-conn-status-lbl :runtime :ok)))

  (websocket/register-event-callback
   :connection-going-away
   (fn []
     ;; When connection goes away:
     ;; - clear the ui, if we lost the connection we can assume we lost the state on the runtime side
     ;; so get rid of flows, taps, and browser instrumentation
     ;; - start the reconnect try loop
     (utils/log "WebSocket connection went away")
     (ui-main/set-conn-status-lbl :runtime :fail)

     (utils/log "Clearing the UI because of websocket connection down")

     (clear-ui)

     (let [websocket-reconnect-loop-stop (websocket-reconnect-loop)]
       ;; setup the new stopping fn
       (swap! watchdog assoc :websocket-reconnect-loop-stop websocket-reconnect-loop-stop))))

  (let [websocket-reconnect-loop-stop (websocket-reconnect-loop)
        repl-watchdog-stop (repl-watchdog-loop)]

    (atom {:repl-watchdog-stop repl-watchdog-stop
           :websocket-reconnect-loop-stop websocket-reconnect-loop-stop})))

(defn stop-watchdog []
  (when (:connect-to-repl? config)
    (when-let [repl-watchdog-stop (:repl-watchdog-stop watchdog)]
      (repl-watchdog-stop))
    (when-let [reconnect-loop-stop (:websocket-reconnect-loop-stop watchdog)]
      (reconnect-loop-stop))))
