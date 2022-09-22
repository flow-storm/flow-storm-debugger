(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.events-processor :as events-processor]
            [flow-storm.debugger.watchdog]
            [flow-storm.debugger.runtime-api]
            [flow-storm.debugger.websocket]
            [flow-storm.debugger.repl.connection]
            [flow-storm.debugger.config]
            [mount.core :as mount]))

(def flow-storm-core-ns 'flow-storm.core)

(def local-debugger-mount-vars

  "All defstate vars should be registered here.
  This exists so `start-debugger`, `stop-debugger` don't mess with other
  states when the debugger is used inside a application that uses mount."

  [#'flow-storm.debugger.config/config
   #'flow-storm.debugger.events-processor/events-processor
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
         #'flow-storm.debugger.repl.connection/connection
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

   When `:local?` is false you can also provide `:host` and `:port` for the nrepl server."

  [{:keys [local?] :as config}]

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
                                :env-kind (if (#{:shadow} (:repl-type config))
                                            :cljs
                                            :clj)
                                :connect-to-repl? (:port config)
                                :repl-kind :nrepl
                                :show-error ui-main/show-error
                                :dispatch-event events-processor/enqueue-event!))
        (mount/only remote-debugger-mount-vars)
        (mount/start))))
