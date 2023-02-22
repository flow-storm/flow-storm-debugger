(ns flow-storm.debugger.ui.main
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [label icon-button event-handler h-box progress-indicator tab tab-pane border-pane]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars
             :as ui-vars
             :refer [obj-lookup store-obj]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.utils :refer [log log-error]]
            [clojure.java.io :as io]
            [flow-storm.debugger.config :refer [config] :as config]
            [mount.core :as mount :refer [defstate]]
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

(defstate ui
  :start (start-ui config)
  :stop  (stop-ui))

(defn clear-all []
  (log "Removing all taps")
  (taps-screen/clear-all-taps)

  (log "Removing all flows")
  (doseq [fid (dbg-state/all-flows-ids)]
    (flows-screen/fully-remove-flow fid))

  (log "Clearing values and api cache")
  (runtime-api/clear-values-references rt-api)
  (runtime-api/clear-api-cache rt-api))

(defn bottom-box []
  (let [progress-box (h-box [])
        repl-status-lbl (label "REPL" "conn-status-lbl")
        runtime-status-lbl (label "RUNTIME" "conn-status-lbl")
        box (doto (h-box [progress-box repl-status-lbl runtime-status-lbl] "main-bottom-bar-box")
              (.setSpacing 5)
              (.setAlignment Pos/CENTER_RIGHT)
              (.setPrefHeight 20))]
    (store-obj "progress-box" progress-box)
    (store-obj "repl-status-lbl" repl-status-lbl)
    (store-obj "runtime-status-lbl" runtime-status-lbl)
    box))

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

(defn select-main-tools-tab [tool]
  (let [[main-tools-tab] (obj-lookup "main-tools-tab")
        sel-model (.getSelectionModel main-tools-tab)]
    (case tool
      :flows (.select sel-model 0)
      :browser (.select sel-model 1)
      :taps (.select sel-model 2)
      :docs (.select sel-model 3))))

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

        tabs-p (tab-pane {:tabs [flows-tab browser-tab taps-tab docs-tab]
                          :rotate? true
                          :closing-policy :unavailable
                          :side :left
                          :on-tab-change (fn [_ to-tab]
                                           (when (= to-tab browser-tab)
                                             (browser-screen/get-all-namespaces)))})
        _ (store-obj "main-tools-tab" tabs-p)]

    tabs-p))

(defn- build-tool-bar-pane []
  (let [tools [(icon-button :icon-name "mdi-delete-forever"
                            :tooltip "Clean all debugger and runtime values references"
                            :on-click (fn [] (clear-all)))
               (icon-button :icon-name "mdi-stop-circle-outline"
                            :tooltip "Cancel current running task (search, etc)"
                            :on-click (fn [] (runtime-api/interrupt-all-tasks rt-api)))]]
    (ToolBar. (into-array Node tools))))

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

(defn start-ui [config]
  (log "[Starting UI subsystem]")
  ;; Initialize the JavaFX toolkit

  ;; Ensure a task bar icon is shown on MacOS.
  (System/setProperty "apple.awt.UIElement" "false")
  (javafx.embed.swing.JFXPanel.)
  (Platform/setImplicitExit false)

  (ui-utils/run-now
   (try
     (let [scene (Scene. (build-main-pane) 1024 768)
           stage (doto (Stage.)
                   (.setTitle "Flowstorm debugger")
                   (.setScene scene))

           stages (atom #{stage})
           theme-listener (start-theming-system config stages)]

       (doto scene
         (.setOnKeyPressed (event-handler
                            [kev]
                            (let [key-name (.getName (.getCode kev))]
                              (cond

                                (and (.isControlDown kev)
                                     (= key-name "G"))
                                (do
                                  (log "Interrupting task")
                                  (runtime-api/interrupt-all-tasks rt-api))

                                (and (.isControlDown kev)
                                     (= key-name "L"))
                                (clear-all)

                                (and (.isControlDown kev)
                                     (= key-name "D"))
                                (toggle-debug-mode)
                                ;; :else
                                ;; (log (format "Unhandled keypress %s" key-name))
                                ))))
         (.setRoot (build-main-pane)))

       (-> stage .show)

       {:stages stages
        :theme-listener theme-listener})

     (catch Exception e
       (log-error "UI Thread exception" e))))
  )

(defn stop-ui []
  (log "[Stopping UI subsystem]")
  (let [{:keys [stages theme-listener]} ui]

    ;; remove the OS theme listener
    (when theme-listener
      (.removeListener (OsThemeDetector/getDetector) theme-listener))

    ;; close all stages
    (ui-utils/run-now
     (doseq [stage @stages]
       (.close stage)))))
