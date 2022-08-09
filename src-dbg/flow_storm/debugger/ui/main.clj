(ns flow-storm.debugger.ui.main
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler h-box alert-dialog progress-indicator tab tab-pane border-pane]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.state-vars
             :as ui-vars
             :refer [obj-lookup store-obj]]
            [flow-storm.utils :refer [log log-error]]
            [clojure.java.io :as io]
            [mount.core :as mount :refer [defstate]])
  (:import [com.jthemedetecor OsThemeDetector]
           [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.geometry Pos]
           [javafx.application Platform]))

(declare start-ui)
(declare stop-ui)
(declare ui)

(defstate ui
  :start (start-ui (mount/args))
  :stop  (stop-ui))

(defn bottom-box []
  (let [box (doto (h-box [] "main-bottom-bar-box")
              (.setAlignment Pos/CENTER_RIGHT)
              (.setPrefHeight 20))]
    (store-obj "main-bottom-bar-box" box)
    box))

(defn set-in-progress [in-progress?]
  (ui-utils/run-later
   (let [[box] (obj-lookup "main-bottom-bar-box")]
     (if in-progress?

       (let [prog-ind (progress-indicator 20)]
         ;; @HACK this is super hacky, you can't use the bottom bar for anything
         ;; more than this progress-indicator
         (-> box .getChildren .clear)
         (.addAll (.getChildren box) [prog-ind]))

       (-> box .getChildren .clear)))))

(defn show-error [msg]
  (ui-utils/run-later
   (let [err-dialog (alert-dialog {:type :error
                                   :message msg
                                   :buttons [:close]})]
     (.show err-dialog))))

(defn select-main-tools-tab [tool]
  (let [[main-tools-tab] (obj-lookup "main-tools-tab")
        sel-model (.getSelectionModel main-tools-tab)]
    (case tool
      :flows (.select sel-model 0)
      :browser (.select sel-model 1))))

(defn- main-tabs-pane []
  (let [flows-tab (tab {:text "Flows"
                        :class "vertical-tab"
                        :content (flows-screen/main-pane)})
        browser-tab (tab {:text "Browser"
                          :class "vertical-tab"
                          :content (browser-screen/main-pane)
                          :on-selection-changed (event-handler
                                                 [_]
                                                 (browser-screen/get-all-namespaces))})
        taps-tab (tab {:text "Taps"
                      :class "vertical-tab"
                      :content (taps-screen/main-pane)
                      :on-selection-changed (event-handler [_])})

        tabs-p (tab-pane {:tabs [flows-tab browser-tab taps-tab]
                          :rotate? true
                          :closing-policy :unavailable
                          :side :left})
        _ (store-obj "main-tools-tab" tabs-p)]

    tabs-p))

(defn- build-main-pane []
  (let [mp (border-pane {:center (main-tabs-pane)
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

    (update-scene-with-current-theme (-> @stages first (.getScene)) (or (= :dark theme)
                                                                        (.isDark (OsThemeDetector/getDetector))))

    (alter-var-root #'ui-vars/register-and-init-stage!
                    (constantly
                     (fn [stg]
                       (update-scene-with-current-theme (.getScene stg))
                       (swap! stages conj stg))))

    theme-listener))

(defn start-ui [config]
  ;; Initialize the JavaFX toolkit
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
                                  (log "Interrupting long running task")
                                  (ui-vars/interrupt-long-running-task-thread))

                                ;; :else
                                ;; (log (format "Unhandled keypress %s" key-name))
                                ))))
         (.setRoot (build-main-pane)))

       (-> stage .show)

       {:stages stages
        :theme-listener theme-listener})

     (catch Exception e
       (log-error "UI Thread exception" e)))))

(defn stop-ui []
  (let [{:keys [stages theme-listener]} ui]

    ;; remove the OS theme listener
    (when theme-listener
      (.removeListener (OsThemeDetector/getDetector) theme-listener))

    ;; close all stages
    (ui-utils/run-now
     (doseq [stage @stages]
       (.close stage)))))
