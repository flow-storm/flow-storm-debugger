(ns flow-storm.debugger.ui.main
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler h-box alert-dialog progress-indicator tab tab-pane border-pane]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.state-vars
             :as ui-vars
             :refer [main-pane stage scene obj-lookup store-obj dark-listener]]
            [flow-storm.utils :refer [log log-error]]
            [clojure.java.io :as io])
  (:import [com.jthemedetecor OsThemeDetector]
           [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.geometry Pos]
           [javafx.application Platform]))

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

(defn main-tabs-pane []
  (let [flows-tab (tab {:text "Flows"
                        :class "vertical-tab"
                        :content (flows-screen/main-pane)})
        browser-tab (tab {:text "Browser"
                          :class "vertical-tab"
                          :content (browser-screen/main-pane)
                          :on-selection-changed (event-handler
                                                 [_]
                                                 (browser-screen/get-all-namespaces))})
        tabs-p (tab-pane {:tabs [flows-tab browser-tab]
                          :rotate? true
                          :closing-policy :unavailable
                          :side :left})
        _ (store-obj "main-tools-tab" tabs-p)]

    tabs-p))

(defn close-stage []
  (when stage
    (ui-utils/run-now
     (.close stage))))

(defn build-main-pane []
  (let [mp (border-pane {:center (main-tabs-pane)
                         :bottom (bottom-box)})]
    (ui-utils/add-class mp "main-pane")
    mp))

(defn update-styles [^Scene scene dark? styles]
  (let [stylesheets (.getStylesheets scene)
        default-styles (if dark? "styles_dark.css" "styles.css")]
    (.clear stylesheets)
    (.add stylesheets (str (io/resource default-styles)))
    (when styles (.add stylesheets (str (io/as-url (io/file styles)))))))

(comment
  (Platform/runLater #(update-styles scene true "styles_dark.css")))

(defn reset-styles
  "Get current scene and a map config, containing (optionally)
  `:styles` — string path to a css file with styles;
  `:theme` — one of #{:light :dark :auto}, :auto by default

  Sets default style, overrides with extention `:styles` if provided.
  Tries to remove a dark-listener. Sets new one if `:theme` is `:auto`."
  [^Scene scene {:keys [styles theme] :or {theme :auto}}]
  (let [detector (OsThemeDetector/getDetector)]
    (when dark-listener (.removeListener detector dark-listener))
    (case theme
      :auto (let [detector (OsThemeDetector/getDetector)
                  dark? (.isDark detector)
                  _ (update-styles scene dark? styles)
                  listener (reify java.util.function.Consumer
                             (accept [_ dark?]
                               (Platform/runLater
                                 #(update-styles scene dark? styles))))]
              (alter-var-root #'dark-listener (constantly listener))
              (.registerListener detector listener))
      :light (update-styles scene false styles)
      :dark (update-styles scene true styles)
      (log-error "wrong `:theme`, should be one of: #{:light :dark :auto}"))))

(defn reset-scene-main-pane []
  (let [mp (build-main-pane)]
    (alter-var-root #'main-pane (constantly mp))
    (.setRoot scene mp)))

(defn start-ui [config]
  ;; Initialize the JavaFX toolkit
  (javafx.embed.swing.JFXPanel.)
  (Platform/setImplicitExit false)

  (ui-utils/run-now
   (try
     (let [scene (Scene. (build-main-pane) 1024 768)]

       (doto scene
         (.setOnKeyPressed (event-handler
                            [kev]
                            (let [key-name (.getName (.getCode kev))]
                              (cond

                                (and (.isControlDown kev)
                                     (= key-name "G"))
                                (do
                                  (log "Interrupting long running task")
                                  (.interrupt @ui-vars/long-running-task-thread))

                                ;; :else
                                ;; (log (format "Unhandled keypress %s" key-name))
                                )))))

       (reset-styles scene config)

       (alter-var-root #'scene (constantly scene))
       (alter-var-root #'stage (constantly (doto (Stage.)
                                             (.setTitle "Flowstorm debugger")
                                             (.setScene scene)))))

     (reset-scene-main-pane)

     (-> stage .show)

     (catch Exception e
       (log-error "UI Thread exception" e)))))
