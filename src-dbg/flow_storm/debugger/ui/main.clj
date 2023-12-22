(ns flow-storm.debugger.ui.main

  "Main UI sub-component which renders the GUI using JavaFX.

  Defines a pretty standard JavaFX application which uses
  `flow-storm.debugger.state` for mem state storage.

  The main entry point is `start-ui` and `stop-ui` can be used for
  stopping the component gracefully.

  One peculiarity of this JavaFX application is that it use a custom
  index (defined in `flow-storm.debugger.state`) for storing references
  to differet javafx Nodes instead of javafx own component ID system.
  Reference are stored and retrieved using `store-obj` and `obj-lookup`
  respectively.

  This namespace defines the outer window with the top bar and tools tabs.
  All tools screens are defined inside flow-storm.debugger.ui.*.screen.clj
  "

  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [label icon-button event-handler h-box progress-indicator progress-bar tab tab-pane border-pane
                     key-combo-match?]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.ui.timeline.screen :as timeline-screen]
            [flow-storm.debugger.ui.printer.screen :as printer-screen]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.state :as dbg-state :refer [obj-lookup store-obj]]
            [flow-storm.utils :as utils :refer [log log-error]]
            [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.docs])
  (:import [com.jthemedetecor OsThemeDetector]
           [javafx.scene Scene Node]
           [javafx.stage Stage]
           [javafx.geometry Pos]
           [javafx.scene.control ToolBar]
           [javafx.application Platform]
           [javafx.scene.input KeyCode]))

(declare start-ui)
(declare stop-ui)
(declare ui)

(def ^:dynamic *killing-ui-from-window-close?* false)

(defstate ui
  :start (fn [config] (start-ui config))
  :stop  (fn [] (stop-ui)))

(defn clear-ui []
  (taps-screen/clear-all-taps)

  (doseq [fid (dbg-state/all-flows-ids)]
    (dbg-state/remove-flow fid)
    (ui-utils/run-later (flows-screen/remove-flow fid)))

  (ui-utils/run-later
    (browser-screen/clear-instrumentation-list)
    (timeline-screen/clear-timeline)
    (printer-screen/clear-prints)))

(defn clear-all []
  ;; CAREFULL the order here matters
  (taps-screen/clear-all-taps)

  (doseq [fid (dbg-state/all-flows-ids)]
    (flows-screen/fully-remove-flow fid))

  (runtime-api/clear-recordings rt-api)
  (runtime-api/clear-api-cache rt-api)

  (timeline-screen/clear-timeline)
  (printer-screen/clear-prints))

(defn bottom-box []
  (let [progress-box (h-box [])
        heap-bar (doto (progress-bar 100)
                   (.setProgress 0))
        heap-max-lbl (label "")
        heap-box (h-box [heap-bar heap-max-lbl])
        repl-status-lbl (label "REPL")
        runtime-status-lbl (label "RUNTIME")
        box (doto (h-box [progress-box repl-status-lbl runtime-status-lbl heap-box] "main-bottom-bar-box")
              (.setSpacing 5)
              (.setAlignment Pos/CENTER_RIGHT)
              (.setPrefHeight 20))]
    (store-obj "progress-box" progress-box)
    (store-obj "repl-status-lbl" repl-status-lbl)
    (store-obj "runtime-status-lbl" runtime-status-lbl)
    (store-obj "heap-bar" heap-bar)
    (store-obj "heap-max-lbl" heap-max-lbl)
    box))

(defn update-heap-indicator [{:keys [max-heap-bytes heap-size-bytes heap-free-bytes]}]
  (ui-utils/run-later
   (let [[heap-bar] (obj-lookup "heap-bar")
         [heap-max-lbl] (obj-lookup "heap-max-lbl")
         occupied-bytes (- heap-size-bytes heap-free-bytes)
         occ-perc (float (/ occupied-bytes max-heap-bytes))
         max-gb (float (/ max-heap-bytes 1024 1024 1024))]
     (.setProgress heap-bar occ-perc)
     (.setText heap-max-lbl (format "%.2f Gb" max-gb)))))

(defn set-conn-status-lbl [lbl-key status]
  (ui-utils/run-later
    (try
      (let [[status-lbl] (obj-lookup (case lbl-key
                                       :ws "runtime-status-lbl"
                                       :repl    "repl-status-lbl"))]
        (when status-lbl
          (ui-utils/clear-classes status-lbl)
          (ui-utils/add-class status-lbl "conn-status-lbl")
          (if status
            (ui-utils/add-class status-lbl "ok")
            (ui-utils/add-class status-lbl "fail"))))
      (catch Exception e
        (.printStackTrace e)))))

(defn set-in-progress [in-progress?]
  (ui-utils/run-later
   (let [[box] (obj-lookup "progress-box")]
     (if in-progress?

       (let [prog-ind (progress-indicator 20)]
         (-> box .getChildren .clear)
         (.addAll (.getChildren box) [prog-ind]))

       (-> box .getChildren .clear)))))


(defn- main-tabs-pane []
  (let [flows-tab (tab {:text "Flows"
                        :class "vertical-tab"
                        :content (flows-screen/main-pane)})
        browser-tab (tab {:text "Browser"
                          :class "vertical-tab"
                          :content (browser-screen/main-pane)})
        taps-tab (tab {:text "Taps"
                       :class "vertical-tab"
                       :content (taps-screen/main-pane)
                       :on-selection-changed (event-handler [_])})
        docs-tab (tab {:text "Docs"
                       :class "vertical-tab"
                       :content (docs-screen/main-pane)
                       :on-selection-changed (event-handler [_])})
        timeline-tab (tab {:text "Timeline"
                           :class "vertical-tab"
                           :content (timeline-screen/main-pane)
                           :on-selection-changed (event-handler [_])})
        printer-tab (tab {:text "Printer"
                          :class "vertical-tab"
                          :content (printer-screen/main-pane)
                          :on-selection-changed (event-handler [_])})

        tabs-p (tab-pane {:tabs [flows-tab browser-tab taps-tab docs-tab timeline-tab printer-tab]
                          :rotate? true
                          :closing-policy :unavailable
                          :side :left
                          :on-tab-change (fn [_ to-tab]
                                           (cond
                                             (= to-tab browser-tab) (browser-screen/get-all-namespaces)
                                             (= to-tab printer-tab) (printer-screen/update-prints-controls)))})
        _ (store-obj "main-tools-tab" tabs-p)]

    tabs-p))

(defn- build-tool-bar-pane []
  (let [record-btn (icon-button :icon-name "mdi-record"
                                :tooltip "Start/Stop recording"
                                :on-click (fn [] (runtime-api/toggle-recording rt-api))
                                :classes ["record-btn"])
        task-cancel-btn (icon-button :icon-name "mdi-playlist-remove"
                                     :tooltip "Cancel current running task (search, etc) (Ctrl-g)"
                                     :on-click (fn [] (runtime-api/interrupt-all-tasks rt-api))
                                     :disable true)
        clear-btn (icon-button :icon-name  "mdi-delete-forever"
                               :tooltip "Clean all debugger and runtime values references (Ctrl-l)"
                               :on-click (fn [] (clear-all)))
        unblock-threads-btn (icon-button :icon-name "mdi-run"
                                         :tooltip "Unblock all blocked threads if any (Ctrl-u)"
                                         :on-click (fn [] (runtime-api/unblock-all-threads rt-api)))
        quick-jump-textfield (doto (h-box [(label "Quick jump:")
                                           (ui-utils/autocomplete-textfield
                                            (fn []
                                              (into []
                                                    (map (fn [[fq-fn-name cnt]]
                                                           {:text (format "%s (%d)" fq-fn-name cnt)
                                                            :on-select (fn []
                                                                         (let [fn-call (runtime-api/find-fn-call rt-api (symbol fq-fn-name) 0 {})]
                                                                           (flows-screen/goto-location fn-call)))}))
                                                    (runtime-api/all-fn-call-stats rt-api))))])
                               (.setAlignment Pos/CENTER_LEFT))
        tools [clear-btn
               task-cancel-btn
               record-btn
               unblock-threads-btn
               quick-jump-textfield]]

    (store-obj "task-cancel-btn" task-cancel-btn)
    (store-obj "record-btn" record-btn)
    (ToolBar. (into-array Node tools))))

(defn set-task-cancel-btn-enable [enable?]
  (ui-utils/run-later
   (let [[task-cancel-btn] (obj-lookup "task-cancel-btn")]
     (.setDisable task-cancel-btn (not enable?)))))

(defn set-recording-btn [recording?]
  (ui-utils/run-later
   (let [[record-btn] (obj-lookup "record-btn")]
     (ui-utils/update-button-icon
      record-btn
      (if recording?
        "mdi-pause"
        "mdi-record")))))

(defn- build-main-pane []
  (let [mp (border-pane {:top (build-tool-bar-pane)
                         :center (main-tabs-pane)
                         :bottom (bottom-box)})]
    (ui-utils/add-class mp "main-pane")
    mp))

(defn- start-theme-listener [on-theme-change]
  (try
    (let [detector (OsThemeDetector/getDetector)
          listener (reify java.util.function.Consumer
                     (accept [_ dark?]
                       (ui-utils/run-later
                        (on-theme-change dark?))))]
      (log "Registering os theme-listener")
      (.registerListener detector listener)
      listener)
    (catch Exception e
      (log-error "Couldn't start theme listener" e))))

(defn reset-theming [stages]
  (let [new-stylesheets (dbg-state/current-stylesheets)]
    (doseq [stage stages]
      (let [scene (.getScene stage)
            scene-stylesheets (.getStylesheets scene)]
        (.clear scene-stylesheets)
        (.addAll scene-stylesheets (into-array String new-stylesheets))))))

(defn- toggle-debug-mode []
  (dbg-state/toggle-debug-mode)
  (log (format "DEBUG MODE %s" (if (:debug-mode? (dbg-state/debugger-config)) "ENABLED" "DISABLED"))))

(defn stop-ui []
  (let [{:keys [stages theme-listener]} ui]

    ;; remove the OS theme listener
    (when theme-listener
      (log "Removing os theme-listener")
      (.removeListener (OsThemeDetector/getDetector) theme-listener))

    ;; close all stages
    (when-not *killing-ui-from-window-close?*
      (doseq [stage @stages]

        (ui-utils/run-now (.close stage))))))

(defn create-flow [{:keys [flow-id form-ns form timestamp]}]
  ;; lets clear the entire cache every time a flow gets created, just to be sure
  ;; we don't reuse old flows values on this flow
  (runtime-api/clear-api-cache rt-api)

  (dbg-state/create-flow flow-id form-ns form timestamp)
  (flows-screen/remove-flow flow-id)
  (flows-screen/create-empty-flow flow-id)
  (ui-general/select-main-tools-tab :flows)
  (flows-screen/update-threads-list flow-id))

(defn setup-ui-from-runtime-config
  "This function is meant to be called after all the system has started,
  to configure the part of UI that depends on runtime state."
  []
  (ui-utils/run-later
   (when-let [{:keys [recording? total-order-recording?] :as runtime-config} (runtime-api/runtime-config rt-api)]
     (log (str "Runtime config retrieved :" runtime-config))
     (let [all-flows-ids (->> (runtime-api/all-flows-threads rt-api)
                              (map first)
                              (into #{}))]
       (dbg-state/set-runtime-config runtime-config)
       (set-recording-btn recording?)
       (timeline-screen/set-recording-check total-order-recording?)
       (printer-screen/update-prints-controls)


       (doseq [fid all-flows-ids]
         (create-flow {:flow-id fid}))))))


(defn start-ui [config]
  (Platform/setImplicitExit false)

  (ui-utils/run-now
   (try
     (let [scene (Scene. (build-main-pane) 1024 768)
           stage (doto (Stage.)
                   (.setTitle (or (:title config) "Flowstorm debugger"))
                   (.setScene scene)
                   (.setOnCloseRequest
                    (event-handler
                     [_]
                     ;; call with skip-ui-stop? true since if we are here
                     ;; we are already stopping the ui from closing the window
                     (binding [*killing-ui-from-window-close?* true]
                       (let [stop-config (when (utils/storm-env?)
                                           {:skip-index-stop? true})]
                         (if-let [stop-all (resolve 'flow-storm.api/stop)]
                           ;; if ui and runtime is running under the same jvm
                           ;; we can stop all
                           (stop-all stop-config)

                           ;; else stop just the debugger
                           ((resolve 'flow-storm.debugger.main/stop-debugger))))))))

           stages (atom #{stage})
           theme-listener (when (= :auto (:theme config))
                            (start-theme-listener
                             (fn [dark?]
                               (dbg-state/set-theme (if dark? :dark :light))
                               (reset-theming @stages))))]

       (alter-var-root #'dbg-state/register-and-init-stage!
                       (constantly
                        (fn [stg]
                          (swap! stages conj stg)
                          (reset-theming [stg]))))

       (reset-theming @stages)

       (doto scene
         (.setOnKeyPressed (event-handler
                            [kev]
                            (let [key-name (.getName (.getCode kev))
                                  shift? (.isShiftDown kev)]

                              (cond

                                (key-combo-match? kev "g" [:ctrl])
                                (runtime-api/interrupt-all-tasks rt-api)

                                (key-combo-match? kev "l" [:ctrl])
                                (clear-all)

                                (key-combo-match? kev "d" [:ctrl])
                                (toggle-debug-mode)

                                (key-combo-match? kev "t" [:ctrl])
                                (do
                                  (dbg-state/rotate-theme)
                                  (reset-theming @stages))

                                (or (= (.getCode kev) KeyCode/ADD)
                                    (and shift? (= (.getCode kev) KeyCode/EQUALS)))
                                (do
                                  (dbg-state/inc-font-size)
                                  (reset-theming @stages))

                                (= KeyCode/MINUS (.getCode kev))
                                (do
                                  (dbg-state/dec-font-size)
                                  (reset-theming @stages))

                                (key-combo-match? kev "u" [:ctrl])
                                (runtime-api/unblock-all-threads rt-api)

                                (key-combo-match? kev "f" [:shift]) (ui-general/select-main-tools-tab :flows)
                                (key-combo-match? kev "b" [:shift]) (ui-general/select-main-tools-tab :browser)
                                (key-combo-match? kev "t" [:shift]) (ui-general/select-main-tools-tab :taps)
                                (key-combo-match? kev "d" [:shift]) (ui-general/select-main-tools-tab :docs)
                                (= key-name "Esc") (flows-screen/select-flow-tab nil)
                                (= key-name "0")   (flows-screen/select-flow-tab 0)
                                ))))
         (.setRoot (build-main-pane)))

       (-> stage .show)

       {:stages stages
        :theme-listener theme-listener})

     (catch Exception e
       (log-error "UI Thread exception" e)))))
