(ns flow-storm.debugger.watchdog
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.debugger.websocket :as websocket]
            [flow-storm.debugger.repl.connection :as repl-conn]
            [flow-storm.debugger.config :refer [config]]
            [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.utils :as utils]
            [clojure.core.async :as async]))

(declare watchdog)
(declare start-watchdog)
(declare stop-watchdog)

(def repl-watchdog-interval 3000)
(def websocket-watchdog-interval 1000)

(defstate watchdog
  :start (start-watchdog)
  :stop  (stop-watchdog))

(defn start-watchdog []
  (when (:connect-to-repl? config)
    (utils/log "[Starting Connection watchdog subsystem]")
    (let [repl-watch-stop-ch (async/promise-chan)
          websocket-watch-stop-ch (async/promise-chan)]

      ;; start the repl watchdog loop
      (async/go-loop []
        (let [[_ ch] (async/alts! [(async/timeout repl-watchdog-interval)
                                   repl-watch-stop-ch])]
          (when-not (= ch repl-watch-stop-ch)
            (let [repl-ok? (= true (repl-conn/eval-code-str "flow-storm.api/api-loaded?"))]
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
            (recur))))

      ;; start the websocket watchdog loop
      (async/go-loop []
        (let [[_ ch] (async/alts! [(async/timeout websocket-watchdog-interval)
                                   websocket-watch-stop-ch])]
          (when-not (= ch websocket-watch-stop-ch)
            (let [r (websocket/sync-remote-api-request :ping [] 1000)
                  ws-ok? (= :pong r)]

              (if ws-ok?
                (ui-main/set-runtime-status-lbl :ok)

                (do
                  (ui-main/set-runtime-status-lbl :fail)
                  (utils/log "[WATCHDOG] websocket looks down, trying to reconnect ...")
                  (mount/stop (mount/only [#'flow-storm.debugger.websocket/websocket-server]))
                  (try
                    (mount/start (mount/only [#'flow-storm.debugger.websocket/websocket-server]))
                    (repl-conn/eval-code-str repl-conn/remote-connect-code (repl-conn/default-repl-ns config))
                    ;; if we started, lets wait some time before checking again
                    (async/<! (async/timeout 5000))
                    (catch Exception _
                      (utils/log (format "Couldn't restart the websocket server, retrying in %d ms" websocket-watchdog-interval))))))
              (recur)))))

      {:repl-watch-stop-ch repl-watch-stop-ch
       :websocket-watch-stop-ch websocket-watch-stop-ch})))

(defn stop-watchdog []
  (when (:connect-to-repl? config)
    (utils/log "[Stopping Connection watchdog subsystem]")
    (when-let [stop-ch (:repl-watch-stop-ch watchdog)]
      (async/>!! stop-ch true))
    (when-let [stop-ch (:websocket-watch-stop-ch watchdog)]
      (async/>!! stop-ch true)))
  )
