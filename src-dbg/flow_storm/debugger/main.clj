(ns flow-storm.debugger.main

  " This is the main namespace for the debugger itself, the graphical part of FlowStorm,
  being `start-debugger` the main entry point.

  The debugger system is made of varios stateful components orchestrated by a custom state system
  defined `flow-storm.state-management`, which is similar to `mount`.

  We are choosing a custom state system so we depend on as less libraries as we can
  to avoid version conflicts with users libraries.

  The debugger will start a different set of components depending if we are running a
  remote or local debugger.
  The debugger is a local debugger when it runs in the same process as the debuggee and can call
  all functions locally in contrast with remote debugging like when connecting to a ClojureScript system
  or a remote Clojure system where calls had to be made via websocket and repl connections.

  Look at each component namespace doc string for more details on them.
  "

  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.events-queue :as events-queue]
            [flow-storm.debugger.events-processor :as events-processor]
            [flow-storm.debugger.docs]
            [flow-storm.debugger.runtime-api]
            [flow-storm.debugger.websocket]
            [flow-storm.debugger.repl.core :as repl-core]
            [flow-storm.utils :as utils]
            [flow-storm.state-management :as state-management])
  (:import [javafx.embed.swing JFXPanel]))

(def flow-storm-core-ns 'flow-storm.core)

(def local-debugger-state-vars

  "References to sub-components for local debugger sessions"

  [#'flow-storm.debugger.events-queue/events-queue
   #'flow-storm.debugger.docs/fn-docs
   #'flow-storm.debugger.state/state
   #'flow-storm.debugger.ui.main/ui
   #'flow-storm.debugger.runtime-api/rt-api])

(def remote-debugger-state-vars

  "Extends `local-debugger-state-vars` with extra sub-components
  for remote debugger sessions"

  (into local-debugger-state-vars
        [#'flow-storm.debugger.websocket/websocket-server
         #'flow-storm.debugger.repl.core/repl]))

(defn stop-debugger

  "Gracefully stop the debugger. Useful for reloaded workflows."

  []
  (state-management/stop {}))

(defn start-debugger

  "Run the debugger.

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
    (do
      (state-management/start {:only local-debugger-state-vars
                               :config config})
      (ui-main/setup-ui-from-runtime-config))

    ;; else, start components for remote debugging
    (let [ws-connected? (promise)
          repl-connected? (promise)
          fully-started (atom false)
          signal-ws-connected (fn [conn?]
                                (ui-main/set-conn-status-lbl :ws conn?)
                                (dbg-state/set-connection-status :ws conn?))
          signal-repl-connected (fn [conn?]
                                  (dbg-state/set-connection-status :repl conn?)
                                  (ui-main/set-conn-status-lbl :repl conn?))]
      (state-management/start {:only remote-debugger-state-vars
                               :config (assoc config
                                              :on-ws-event events-queue/enqueue-event!
                                              :on-ws-down (fn []
                                                            (utils/log "WebSocket connection went away")
                                                            (signal-ws-connected false)
                                                            (ui-main/clear-ui))
                                              :on-ws-up (fn [_]
                                                          (signal-ws-connected true)
                                                          (deliver ws-connected? true)

                                                          ;; This is kind of hacky but will handle the ClojureScript page reload situation.
                                                          ;; After a page reload all the runtime part has been restarted, so
                                                          ;; we can re-init it through the repl and also re-setup the ui with whatever the
                                                          ;; runtime contains in terms of settings.
                                                          ;; But we need to skip this the first time the ws connection comes up
                                                          ;; since maybe the system ins't fully started yet, we maybe don't even have a UI
                                                          (when @fully-started
                                                            (when (dbg-state/repl-config)
                                                              (repl-core/init-repl (dbg-state/env-kind)))
                                                            (ui-main/setup-ui-from-runtime-config)))

                                              :on-repl-down (fn []
                                                              (signal-repl-connected false))
                                              :on-repl-up (fn []
                                                            (deliver repl-connected? true)
                                                            (signal-repl-connected true)))})

      (reset! fully-started true)

      ;; if there is a repl config wait for the connection before moving on
      (when (and (dbg-state/repl-config)
                 @repl-connected?)
        (signal-repl-connected true))

      ;; once we have both the UI started and the runtime-connected
      ;; initialize the UI with the info retrieved from the runtime
      (when @ws-connected?
        (signal-ws-connected true)
        (ui-main/setup-ui-from-runtime-config))))

  ;; for both, local and remote

  ;; we set the events dispatch-fn afater `state-management/start` returns because
  ;; we know the UI is ready to start processing events
  (events-queue/set-dispatch-fn events-processor/process-event))
