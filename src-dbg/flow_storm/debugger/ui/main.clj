(ns flow-storm.debugger.ui.main
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler h-box alert-dialog progress-indicator tab tab-pane border-pane]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.target-commands :as target-commands]
            [flow-storm.debugger.websocket :as websocket]
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

(def flow-storm-core-ns 'flow-storm.core)

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
    (when styles (.add stylesheets (str (io/as-url (io/file styles)))))
    nil))

(defn reset-styles
  "Get current scene and a map config, containing (optionally)
  `:styles` — string path to a css file with styles;
  `:theme` — one of #{:light :dark :auto}, :auto by default

  Sets default style, overrides with extention `:styles` if provided.
  Tries to remove a theme-listener. Sets new one if `:theme` is `:auto`.

  If `:auto` is true, returns theme-listener, `nil` otherwise."

  [^Scene scene {:keys [styles theme] :or {theme :auto}}]
  (let [detector (OsThemeDetector/getDetector)
        theme-listener (:theme-listener ui)]
    (when theme-listener (.removeListener detector theme-listener))
    (case theme
      :auto (let [detector (OsThemeDetector/getDetector)
                  dark? (.isDark detector)
                  _ (update-styles scene dark? styles)
                  listener (reify java.util.function.Consumer
                             (accept [_ dark?]
                               (Platform/runLater
                                #(update-styles scene dark? styles))))]
              (.registerListener detector listener)
              theme-listener)
      :light (update-styles scene false styles)
      :dark (update-styles scene true styles)
      (log-error "wrong `:theme`, should be one of: #{:light :dark :auto}"))))

(defn setup-commands-executor [{:keys [local?]}]
  (if local?

    ;; set up the local command-executor
    (let [_ (require [flow-storm-core-ns])
          run-command (resolve (symbol (str flow-storm-core-ns) "run-command"))]
      (alter-var-root #'target-commands/run-command
                      (constantly
                       ;; local command executor (just call command functions)
                       (fn run-command-fn [method args-map & [callback]]
                         ;; run the function on a different thread so we don't block the ui
                         ;; while running commands
                         (.start (Thread. (fn []
                                            (set-in-progress true)
                                            (let [[_ cmd-res] (run-command nil method args-map)]
                                              (set-in-progress false)
                                              (if (= cmd-res :error)

                                                (log-error "Error running command")

                                                (let [[_ ret-val] cmd-res]
                                                  (when callback
                                                    (callback ret-val))))))))))))

    ;; else, set up the remote command-executor
    (alter-var-root #'target-commands/run-command
                    (constantly
                     ;; remote command executor (via websockets)
                     (fn run-command-fn [method args-map & [callback]]
                       (set-in-progress true)
                       (websocket/async-command-request method
                                                        args-map
                                                        (fn [ret-val]
                                                          (set-in-progress false)
                                                          (when callback
                                                            (callback ret-val)))))))))

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
           theme-listener (reset-styles scene config)]

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

       (reset-styles scene config)

       (setup-commands-executor config)
       (-> stage .show)

       {:scene scene
        :stage stage
        :theme-listener theme-listener})

     (catch Exception e
       (log-error "UI Thread exception" e)))))

(defn stop-ui []
  (when-let [stage (:stage ui)]
    (ui-utils/run-now
     (.close stage))))
