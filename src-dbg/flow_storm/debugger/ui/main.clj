(ns flow-storm.debugger.ui.main
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [label icon-button event-handler h-box progress-indicator progress-bar tab tab-pane border-pane]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.ui.timeline.screen :as timeline-screen]
            [flow-storm.debugger.ui.printer.screen :as printer-screen]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars
             :as ui-vars
             :refer [obj-lookup store-obj]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.utils :as utils :refer [log log-error]]
            [clojure.java.io :as io]
            [flow-storm.debugger.config :refer [config] :as config]
            [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.docs])
  (:import [com.jthemedetecor OsThemeDetector]
           [javafx.scene Scene Node]
           [javafx.stage Stage]
           [javafx.geometry Pos]
           [javafx.scene.control ToolBar]
           [javafx.application Platform]))

(declare start-ui)
(declare stop-ui)
(declare ui)

(def ^:dynamic *killing-ui-from-window-close?* false)

(defstate ui
  :start (fn [_] (start-ui))
  :stop  (fn [] (stop-ui)))

(defn clear-all []
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
        repl-status-lbl (label "REPL" "conn-status-lbl")
        runtime-status-lbl (label "RUNTIME" "conn-status-lbl")
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
  (try
    (let [[status-lbl] (obj-lookup (case lbl-key
                                     :runtime "runtime-status-lbl"
                                     :repl    "repl-status-lbl"))]
      (case status
        :ok (do
              (ui-utils/rm-class status-lbl "fail")
              (ui-utils/add-class status-lbl "ok"))
        :fail (do
                (ui-utils/rm-class status-lbl "ok")
                (ui-utils/add-class status-lbl "fail"))))
    (catch Exception _))) ;; silently discarded because sometimes it is called one extra time when stopping the system

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
                                :class "record-btn")
        task-cancel-btn (icon-button :icon-name "mdi-playlist-remove"
                                     :tooltip "Cancel current running task (search, etc)"
                                     :on-click (fn [] (runtime-api/interrupt-all-tasks rt-api))
                                     :disable true)
        tools [(icon-button :icon-name "mdi-delete-forever"
                            :tooltip "Clean all debugger and runtime values references"
                            :on-click (fn [] (clear-all)))
               task-cancel-btn
               record-btn]]

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

(defn- calculate-styles [dark? styles]
  (let [theme-base-styles (str (io/resource (if dark?
                                              "theme_dark.css"
                                              "theme_light.css")))

        default-styles (str (io/resource "styles.css"))
        extra-styles (when styles
                       (str (io/as-url (io/file styles))))]
    (cond-> [theme-base-styles
             default-styles]
      extra-styles (conj extra-styles))))

(defn- update-scene-styles [scene styles]
  (let [stylesheets (.getStylesheets scene)]
    (.clear stylesheets)
    (.addAll stylesheets (into-array String styles))
    nil))

(defn- start-theme-listener [update-scenes-theme]
  (let [detector (OsThemeDetector/getDetector)
        listener (reify java.util.function.Consumer
                   (accept [_ dark?]
                     (ui-utils/run-later
                      (update-scenes-theme dark?))))]
    (.registerListener detector listener)
    listener))

(defn- start-theming-system [{:keys [theme styles] :or {theme :auto}} stages]
  (let [update-scene-with-current-theme (fn [scn dark?]
                                          (let [new-styles (calculate-styles dark? styles)]
                                            (update-scene-styles scn new-styles)))

        theme-listener (when (= :auto theme)
                         (start-theme-listener (fn [dark?]
                                                 (doseq [stg @stages]
                                                   (update-scene-with-current-theme (.getScene stg) dark?)))))]

    (update-scene-with-current-theme (-> @stages first (.getScene))
                                     (or (= :dark theme)
                                         (.isDark (OsThemeDetector/getDetector))))

    (alter-var-root #'ui-vars/register-and-init-stage!
                    (constantly
                     (fn [stg]
                       (update-scene-with-current-theme (.getScene stg)
                                                        (or (= :dark theme)

                                                            (try
                                                              (.isDark (OsThemeDetector/getDetector))
                                                              (catch Exception e
                                                                (log-error "Couldn't query OS theme" e)
                                                                false))))
                       (swap! stages conj stg))))

    theme-listener))

(defn- toggle-debug-mode []
  (alter-var-root #'config/debug-mode not)
  (log (format "DEBUG MODE %s" (if config/debug-mode "ENABLED" "DISABLED"))))

(defn stop-ui []
  (let [{:keys [stages theme-listener]} ui]

    ;; remove the OS theme listener
    (when theme-listener
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
  (ui-vars/select-main-tools-tab :flows)
  (flows-screen/update-threads-list flow-id))

(defn start-ui []
  (Platform/setImplicitExit false)

  (ui-utils/run-now
   (try
     (let [{:keys [recording? total-order-recording?] :as runtime-config} (runtime-api/runtime-config rt-api)
           ;; retrieve runtime configuration and configure before creating the UI
           ;; since some components creation depend on it
           _ (ui-vars/configure-environment runtime-config)
           scene (Scene. (build-main-pane) 1024 768)
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
           theme-listener (start-theming-system config stages)]

       (doto scene
         (.setOnKeyPressed (event-handler
                            [kev]
                            (let [key-name (.getName (.getCode kev))
                                  shift? (.isShiftDown kev)
                                  ctrl? (.isControlDown kev)]
                              (cond

                                (and ctrl? (= key-name "G"))
                                (runtime-api/interrupt-all-tasks rt-api)

                                (and ctrl? (= key-name "L"))
                                (clear-all)

                                (and ctrl? (= key-name "D"))
                                (toggle-debug-mode)

                                (and shift? (= key-name "F")) (ui-vars/select-main-tools-tab :flows)
                                (and shift? (= key-name "B")) (ui-vars/select-main-tools-tab :browser)
                                (and shift? (= key-name "T")) (ui-vars/select-main-tools-tab :taps)
                                (and shift? (= key-name "D")) (ui-vars/select-main-tools-tab :docs)
                                (= key-name "Esc") (flows-screen/select-flow-tab nil)
                                (= key-name "0")   (flows-screen/select-flow-tab 0)
                                ))))
         (.setRoot (build-main-pane)))

       (-> stage .show)

       ;; initialize the UI with the server state
       (let [all-flows-ids (->> (runtime-api/all-flows-threads rt-api)
                                (map first)
                                (into #{}))]

         (set-recording-btn recording?)
         (timeline-screen/set-recording-check total-order-recording?)
         (doseq [fid all-flows-ids]
           (create-flow {:flow-id fid})))

       {:stages stages
        :theme-listener theme-listener})

     (catch Exception e
       (log-error "UI Thread exception" e)))))
