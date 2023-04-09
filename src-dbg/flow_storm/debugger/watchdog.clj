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
            [flow-storm.debugger.ui.utils :as ui-utils]
            [clojure.core.async :as async]))

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

(defn websocket-reconnect-loop [websocket-watch-stop-ch]
  (utils/log "[WATCHDOG] starting websocket reconnect loop")
  (async/go
    (loop []
      (let [[_ ch] (async/alts! [(async/timeout websocket-watchdog-interval)
                                 websocket-watch-stop-ch])]
        (when-not (= ch websocket-watch-stop-ch)
          (let [r (websocket/sync-remote-api-request :ping [] 1000)
                ws-ok? (= :pong r)]

            (if ws-ok?

              (ui-main/set-conn-status-lbl :runtime :ok)

              (do
                (try
                  ;; if we lost the connection to the runtime, and we are connected to a repl,
                  ;; we also lost runtime initialization, so we need to re init the runtime
                  (when (:connect-to-repl? config)
                    (repl-core/init-repl config))

                  ;; if we started, lets wait some time before checking again
                  (async/<! (async/timeout 5000))
                  (catch Exception _
                    (utils/log (format "Couldn't restart the websocket server, retrying in %d ms" websocket-watchdog-interval))))
                (recur)))))))))

(defn start-watchdog []
  (let [repl-watch-stop-ch (async/promise-chan)
        websocket-watch-stop-ch (async/promise-chan)]

    ;; WebSocket watchdog

    (websocket/register-event-callback :connection-open (fn [] (ui-main/set-conn-status-lbl :runtime :ok)))

    (websocket/register-event-callback
     :connection-going-away
     (fn []
       (utils/log "WebSocket connection went away")
       (ui-main/set-conn-status-lbl :runtime :fail)

       (utils/log "Clearing the UI because of websocket connection down")
       ;; clear the ui
       ;; if we lost the connection we can assume we lost the state on the runtime side
       ;; so get rid of flows, taps, and browser instrumentation
       (clear-ui)

       (websocket-reconnect-loop websocket-watch-stop-ch)))

    (websocket-reconnect-loop websocket-watch-stop-ch)

    ;; Repl watchdog

    ;; start the repl watchdog loop
    ;; this will check for the repl connection to be ready and also
    ;; that everything as been loaded
    (when (:connect-to-repl? config)
      (utils/log "Starting the repl watchdog loop")
      (async/go-loop []
        (let [[_ ch] (async/alts! [(async/timeout repl-watchdog-interval)
                                   repl-watch-stop-ch])]
          (when-not (= ch repl-watch-stop-ch)
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
                    (catch Exception _
                      (utils/log (format "Couldn't restart repl, retrying in %d ms" repl-watchdog-interval)))))))
            (recur)))))

    {:repl-watch-stop-ch repl-watch-stop-ch
     :websocket-watch-stop-ch websocket-watch-stop-ch}))

(defn stop-watchdog []
  (when (:connect-to-repl? config)
    (when-let [stop-ch (:repl-watch-stop-ch watchdog)]
      (async/>!! stop-ch true))
    (when-let [stop-ch (:websocket-watch-stop-ch watchdog)]
      (async/>!! stop-ch true))))
