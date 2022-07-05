(ns flow-storm.debugger.ui.main
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler h-box]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.state-vars :as ui-vars :refer [main-pane stage scene obj-lookup store-obj]]
            [flow-storm.utils :refer [log log-error]]
            [clojure.java.io :as io])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.layout BorderPane]
           [javafx.scene.control Alert ButtonType Alert$AlertType ProgressIndicator TabPane TabPane$TabClosingPolicy]
           [javafx.geometry Side Pos]
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

       (let [prog-ind (doto (ProgressIndicator.)
                        (.setPrefSize 20 20))]
         ;; @HACK this is super hacky, you can't use the bottom bar for anything
         ;; more than this progress-indicator
         (-> box .getChildren .clear)
         (.addAll (.getChildren box) [prog-ind]))

       (-> box .getChildren .clear)))))

(defn show-error [msg]
  (ui-utils/run-later
   (let [err-dialog (Alert. Alert$AlertType/ERROR msg (into-array ButtonType [ButtonType/CLOSE]))]
     (.show err-dialog))))

(defn select-main-tools-tab [tool]
  (let [[main-tools-tab] (obj-lookup "main-tools-tab")
        sel-model (.getSelectionModel main-tools-tab)]
    (case tool
      :flows (.select sel-model 0)
      :browser (.select sel-model 1))))

(defn main-tabs-pane []
  (let [tabs-p (TabPane.)
        _ (store-obj "main-tools-tab" tabs-p)
        tabs (.getTabs tabs-p)
        flows-tab (doto (ui-utils/tab "Flows" "vertical-tab")
                    (.setContent (flows-screen/main-pane)))
        browser-tab (doto (ui-utils/tab "Browser" "vertical-tab")
                      (.setContent (browser-screen/main-pane))
                      (.setOnSelectionChanged (event-handler [_] (browser-screen/get-all-namespaces))))
        ;; refs-tab (doto (ui-utils/tab "Refs")
        ;;            (.setContent (Label. "Refs comming soon"))
        ;;            (.setDisable true))
        ;; taps-tab (doto (ui-utils/tab "Taps")
        ;;            (.setContent (Label. "Taps comming soon"))
        ;;            (.setDisable true))
        ;; timeline-tab (doto (ui-utils/tab "Timeline")
        ;;                (.setContent (Label. "Timeline comming soon"))
        ;;                (.setDisable true))
        ;; docs-tab (doto (ui-utils/tab "Docs")
        ;;            (.setContent (Label. "Docs comming soon"))
        ;;            (.setDisable true))
        ]
    (doto tabs-p
      (.setTabClosingPolicy TabPane$TabClosingPolicy/UNAVAILABLE)

      (.setRotateGraphic true)
      (.setSide (Side/LEFT)))

    (.addAll tabs [flows-tab browser-tab #_refs-tab #_taps-tab
                   #_timeline-tab  #_docs-tab])

    tabs-p))

(defn close-stage []
  (when stage
    (ui-utils/run-now
     (.close stage))))

(defn build-main-pane []
  (let [mp (doto (BorderPane.)
             (.setCenter (main-tabs-pane))
             (.setBottom (bottom-box)))]
    (ui-utils/add-class mp "main-pane")
    mp))

(defn reset-scene-main-pane []
  (let [mp (build-main-pane)]
    (alter-var-root #'main-pane (constantly mp))
    (.setRoot scene mp)))

(defn start-ui [{:keys [styles]}]
  ;; Initialize the JavaFX toolkit
  (javafx.embed.swing.JFXPanel.)
  (Platform/setImplicitExit false)

  (ui-utils/run-now
   (try
     (let [scene (Scene. (build-main-pane) 1024 768)
           stylesheets (.getStylesheets scene)]

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


       (.add stylesheets (str (io/resource "styles.css")))
       (when styles (.add stylesheets (str (io/as-url (io/file styles)))))

       (alter-var-root #'scene (constantly scene))
       (alter-var-root #'stage (constantly (doto (Stage.)
                                             (.setTitle "Flowstorm debugger")
                                             (.setScene scene)))))

     (reset-scene-main-pane)

     (-> stage .show)

     (catch Exception e
       (log-error "UI Thread exception" e)))))
