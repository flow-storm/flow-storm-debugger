(ns flow-storm.debugger.watchdog
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.repl.connection :as repl-conn]
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
  :start (start-watchdog)
  :stop  (stop-watchdog))

(defn clear-ui []
  (taps-screen/clear-all-taps)

  (doseq [fid (dbg-state/all-flows-ids)]
    (dbg-state/remove-flow fid)
    (ui-utils/run-later (flows-screen/remove-flow fid)))

  (ui-utils/run-later (browser-screen/clear-instrumentation-list)))

(defn start-watchdog []
  (utils/log "[Starting Connection watchdog subsystem]")
  (let [repl-watch-stop-ch (async/promise-chan)
        websocket-watch-stop-ch (async/promise-chan)
        ui-cleared (atom false)]

    ;; start the repl watchdog loop
    ;; this will check for the repl connection to be ready and also
    ;; that everything as been loaded
    (when (:connect-to-repl? config)
      (utils/log "Starting the repl watchdog loop")
      (async/go-loop []
        (let [[_ ch] (async/alts! [(async/timeout repl-watchdog-interval)
                                   repl-watch-stop-ch])]
          (when-not (= ch repl-watch-stop-ch)
            (let [repl-ok? (= 42 (repl-conn/eval-code-str "42"))]
              (if repl-ok?
                (ui-main/set-repl-status-lbl :ok)

                (do
                  (ui-main/set-repl-status-lbl :fail)
                  (utils/log "[WATCHDOG] repl looks down, trying to reconnect ...")
                  (mount/stop (mount/only [#'flow-storm.debugger.repl.connection/connection]))
                  (try
                    (mount/start (mount/only [#'flow-storm.debugger.repl.connection/connection]))
                    (catch Exception _
                      (utils/log (format "Couldn't restart repl, retrying in %d ms" repl-watchdog-interval)))))))
            (recur)))))

    ;; start the websocket watchdog loop
    (utils/log "Starting the websocket watchdog loop")
    (async/go-loop []
      (let [[_ ch] (async/alts! [(async/timeout websocket-watchdog-interval)
                                 websocket-watch-stop-ch])]
        (when-not (= ch websocket-watch-stop-ch)
          (let [r (websocket/sync-remote-api-request :ping [] 1000)
                ws-ok? (= :pong r)]

            (if ws-ok?
              (do
                (ui-main/set-runtime-status-lbl :ok)
                (reset! ui-cleared false))

              (do
                (utils/log "[WATCHDOG] websocket looks down, trying to reconnect ...")

                ;; signal the fail on the ui
                (ui-main/set-runtime-status-lbl :fail)

                ;; clear the ui
                ;; if we lost the connection we can assume we lost the state on the runtime side
                ;; so get rid of flows, taps, and browser instrumentation
                (when-not @ui-cleared
                  (utils/log "Clearing the UI because of websocket connection down")
                  (clear-ui)
                  (reset! ui-cleared true))

                (try
                  ;; if we lost the connection to the runtime, and we are connected to a repl,
                  ;; we also lost runtime initialization, so we need to re init the runtime bef
                  (when (:connect-to-repl? config)
                    (doseq [{:keys [code ns]} (repl-conn/make-general-repl-init-sequence config)]
                      (repl-conn/eval-code-str code ns)))

                  (repl-conn/eval-code-str (repl-conn/remote-connect-code config) (repl-conn/default-repl-ns config))
                  ;; if we started, lets wait some time before checking again
                  (async/<! (async/timeout 5000))
                  (catch Exception _
                    (utils/log (format "Couldn't restart the websocket server, retrying in %d ms" websocket-watchdog-interval))))))
            (recur)))))

    {:repl-watch-stop-ch repl-watch-stop-ch
     :websocket-watch-stop-ch websocket-watch-stop-ch}))

(defn stop-watchdog []
  (when (:connect-to-repl? config)
    (utils/log "[Stopping Connection watchdog subsystem]")
    (when-let [stop-ch (:repl-watch-stop-ch watchdog)]
      (async/>!! stop-ch true))
    (when-let [stop-ch (:websocket-watch-stop-ch watchdog)]
      (async/>!! stop-ch true)))
  )
