(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.events-queue :as events-queue]
            [flow-storm.debugger.watchdog]
            [flow-storm.debugger.runtime-api]
            [flow-storm.debugger.websocket]
            [flow-storm.debugger.repl.core]
            [flow-storm.debugger.config]
            [mount.core :as mount]))

(def flow-storm-core-ns 'flow-storm.core)

(def local-debugger-mount-vars

  "All defstate vars should be registered here.
  This exists so `start-debugger`, `stop-debugger` don't mess with other
  states when the debugger is used inside a application that uses mount."

  [#'flow-storm.debugger.config/config
   #'flow-storm.debugger.events-queue/events-queue
   #'flow-storm.debugger.ui.state-vars/ui-objs
   #'flow-storm.debugger.ui.state-vars/flows-ui-objs
   #'flow-storm.debugger.ui.state-vars/tasks-subscriptions
   #'flow-storm.debugger.state/state
   #'flow-storm.debugger.ui.main/ui
   #'flow-storm.debugger.runtime-api/rt-api])

(def remote-debugger-mount-vars

  "Same as `local-debugger-mount-vars` but for remote debugger"

  (into local-debugger-mount-vars
        [#'flow-storm.debugger.websocket/websocket-server
         #'flow-storm.debugger.repl.core/repl
         #'flow-storm.debugger.watchdog/watchdog]))

(defn stop-debugger []
  (-> (mount/only (into local-debugger-mount-vars remote-debugger-mount-vars))
      (mount/stop)))

(defn start-debugger

  "Run a standalone debugger.

   `config` should be a map containing :
        - `:local?` when false will also start a websocket server and listen for connections
        - `:theme` can be one of `:light`, `:dark` or `:auto`
        - `:styles` a string path to a css file if you want to override some started debugger styles

   When `:local?` is false you can also provide `:runtime-host` `:debugger-host` and `:port` for the nrepl server.
  `:runtime-host` should be the ip of the debuggee (defaults to localhost)
  `:debugger-host` shoud be the ip where the debugger is running, since the debuggee needs to connect back to it (defaults to localhost)"


  [{:keys [local?] :as config}]

  #_(.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn []
              (log "Shutting down VM")
              (stop-debugger)
              (log "Done. Bye."))))

  (if local?

    ;; start components for local debugging
    (-> (mount/with-args (assoc config
                                :show-message ui-main/show-message))
        (mount/only local-debugger-mount-vars)
        (mount/start))

    ;; else, start components for remote debugging
    (-> (mount/with-args (assoc config
                                :env-kind (if (#{:shadow} (:repl-type config))
                                            :cljs
                                            :clj)
                                :connect-to-repl? (boolean (:port config))
                                :repl-kind :nrepl
                                :show-message ui-main/show-message
                                :dispatch-event events-queue/enqueue-event!))
        (mount/only remote-debugger-mount-vars)
        (mount/start))))
