(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.events-queue :as events-queue]
            [flow-storm.debugger.watchdog]
            [flow-storm.debugger.docs]
            [flow-storm.debugger.runtime-api]
            [flow-storm.debugger.websocket]
            [flow-storm.debugger.repl.core]
            [flow-storm.debugger.config]
            [flow-storm.state-management :as state-management])
  (:import [javafx.embed.swing JFXPanel]))

(def flow-storm-core-ns 'flow-storm.core)

(def local-debugger-state-vars

  [#'flow-storm.debugger.config/config
   #'flow-storm.debugger.events-queue/events-queue
   #'flow-storm.debugger.ui.state-vars/ui-objs
   #'flow-storm.debugger.ui.state-vars/tasks-subscriptions
   #'flow-storm.debugger.docs/fn-docs
   #'flow-storm.debugger.state/state
   #'flow-storm.debugger.ui.main/ui
   #'flow-storm.debugger.runtime-api/rt-api])

(def remote-debugger-state-vars

  (into local-debugger-state-vars
        [#'flow-storm.debugger.websocket/websocket-server
         #'flow-storm.debugger.repl.core/repl
         #'flow-storm.debugger.watchdog/watchdog]))

(defn stop-debugger []
  (state-management/stop {}))

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

  ;; Ensure a task bar icon is shown on MacOS.
  (System/setProperty "apple.awt.UIElement" "false")
  ;; Initialize the JavaFX toolkit
  (JFXPanel.)

  (if local?

    ;; start components for local debugging
    (state-management/start {:only local-debugger-state-vars
                             :config config})

    ;; else, start components for remote debugging
    (state-management/start {:only remote-debugger-state-vars
                             :config (assoc config
                                            :env-kind (if (#{:shadow} (:repl-type config))
                                                        :cljs
                                                        :clj)
                                            :connect-to-repl? (boolean (:port config))
                                            :repl-kind :nrepl
                                            :dispatch-event events-queue/enqueue-event!)}))
  (dbg-state/set-sytem-fully-started))
