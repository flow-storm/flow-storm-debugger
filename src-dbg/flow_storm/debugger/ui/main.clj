(ns flow-storm.debugger.ui.main
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler h-box]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.state-vars :as ui-vars :refer [main-pane stage scene]]
            [flow-storm.utils :refer [log log-error]]
            [clojure.java.io :as io])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.layout BorderPane]
           [javafx.scene.control Label TabPane TabPane$TabClosingPolicy Tab]
           [javafx.geometry Side]
           [javafx.application Platform]))

(defn bottom-box []
  (let [box (h-box [])]
    box))

(defn main-tabs-pane []
  (let [tabs-p (TabPane.)
        tabs (.getTabs tabs-p)
        flows-tab (doto (ui-utils/tab "Flows" "vertical-tab")
                    (.setContent (flows-screen/main-pane)))
        refs-tab (doto (Tab. "Refs")
                   (.setContent (Label. "Refs comming soon"))
                   (.setDisable true))
        taps-tab (doto (Tab. "Taps")
                   (.setContent (Label. "Taps comming soon"))
                   (.setDisable true))
        timeline-tab (doto (Tab. "Timeline")
                       (.setContent (Label. "Timeline comming soon"))
                       (.setDisable true))
        browser-tab (doto (Tab. "Browser")
                      (.setContent (Label. "Browser comming soon"))
                      (.setDisable true))
        docs-tab (doto (Tab. "Docs")
                   (.setContent (Label. "Docs comming soon"))
                   (.setDisable true))]
    (doto tabs-p
      (.setTabClosingPolicy TabPane$TabClosingPolicy/UNAVAILABLE)

      (.setRotateGraphic true)
      (.setSide (Side/LEFT)))

    (.addAll tabs [flows-tab refs-tab taps-tab
                   timeline-tab browser-tab docs-tab])

    tabs-p))

(defn close-stage []
  (when stage
    (ui-utils/run-now
     (.close stage))))

(defn build-main-pane []
  (let [mp (doto (BorderPane.)
             (.setCenter (main-tabs-pane))
             (.setBottom (bottom-box))
             (.setStyle "-fx-font-family: 'Roboto Medium';"))]
    mp))

(defn reset-scene-main-pane []
  (let [mp (build-main-pane)]
    (alter-var-root #'main-pane (constantly mp))
    (.setRoot scene mp)))

(defn start-ui []
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

                                :else
                                (log (format "Unhandled keypress %s" key-name)))))))

       (doto (.getStylesheets scene)
         (.add (str (io/resource "styles.css"))))

       (alter-var-root #'scene (constantly scene))
       (alter-var-root #'stage (constantly (doto (Stage.)
                                             (.setTitle "Flowstorm debugger")
                                             (.setScene scene)))))

     (reset-scene-main-pane)

     (-> stage .show)

     (catch Exception e
       (log-error "UI Thread exception" e)))))
