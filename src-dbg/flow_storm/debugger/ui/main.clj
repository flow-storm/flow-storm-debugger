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
             :refer [event-handler key-combo-match?]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.general :as ui-general :refer [show-message]]
            [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.tasks :as tasks]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.ui.timeline.screen :as timeline-screen]
            [flow-storm.debugger.ui.printer.screen :as printer-screen]
            [flow-storm.debugger.ui.flows.bookmarks :as bookmarks]
            [flow-storm.debugger.ui.flows.search :as search]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.state :as dbg-state :refer [obj-lookup store-obj]]
            [flow-storm.utils :as utils :refer [log log-error]]
            [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.docs]
            [flow-storm.debugger.tutorials.basics :as tut-basics]
            [flow-storm.debugger.user-guide :as user-guide]
            [clojure.string :as str])
  (:import [com.jthemedetecor OsThemeDetector]
           [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.application Platform]
           [javafx.scene.input KeyCode KeyEvent]
           [javafx.scene.control Button ProgressBar]
           [javafx.scene.layout HBox]))


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
  (let [progress-box (ui/h-box :childs [])
        ^ProgressBar heap-bar (ui/progress-bar :width 100)
        _ (.setProgress heap-bar 0)
        heap-max-lbl (ui/label :text "")
        heap-box (ui/h-box :childs [heap-bar heap-max-lbl])
        repl-status-lbl (ui/label :text "REPL")
        runtime-status-lbl (ui/label :text "RUNTIME")
        box (ui/h-box :childs [progress-box repl-status-lbl runtime-status-lbl heap-box]
                      :class "main-bottom-bar-box"
                      :spacing 5
                      :align :center-right
                      :pref-height 20)
        ]
    (store-obj "progress-box" progress-box)
    (store-obj "repl-status-lbl" repl-status-lbl)
    (store-obj "runtime-status-lbl" runtime-status-lbl)
    (store-obj "heap-bar" heap-bar)
    (store-obj "heap-max-lbl" heap-max-lbl)
    box))

(defn update-heap-indicator [{:keys [max-heap-bytes heap-size-bytes heap-free-bytes]}]
  (when max-heap-bytes
    (ui-utils/run-later
     (let [[^ProgressBar heap-bar] (obj-lookup "heap-bar")
           [heap-max-lbl] (obj-lookup "heap-max-lbl")
           occupied-bytes (- heap-size-bytes heap-free-bytes)
           occ-perc (float (/ occupied-bytes max-heap-bytes))
           max-gb (float (/ max-heap-bytes 1024 1024 1024))]
       (.setProgress heap-bar occ-perc)
       (ui-utils/set-text heap-max-lbl (format "%.2f Gb" max-gb))))))

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
    (let [[^HBox box] (obj-lookup "progress-box")]
      (if in-progress?

        (let [prog-ind (ui/progress-indicator :size 20)]
          (-> box .getChildren .clear)
          (ui-utils/observable-add-all (.getChildren box) [prog-ind]))

        (-> box .getChildren ui-utils/observable-clear)))))


(defn- main-tabs-pane []
  (let [flows-tab (ui/tab :text "Flows"
                          :class "vertical-tab"
                          :content (flows-screen/main-pane))
        browser-tab (ui/tab :text "Browser"
                            :class "vertical-tab"
                            :content (browser-screen/main-pane))
        taps-tab (ui/tab :text "Taps"
                         :class "vertical-tab"
                         :content (taps-screen/main-pane)
                         :on-selection-changed (event-handler [_]))
        docs-tab (ui/tab :text "Docs"
                         :class "vertical-tab"
                         :content (docs-screen/main-pane)
                         :on-selection-changed (event-handler [_]))
        timeline-tab (ui/tab :text "Timeline"
                             :class "vertical-tab"
                             :content (timeline-screen/main-pane)
                             :on-selection-changed (event-handler [_]))
        printer-tab (ui/tab :text "Printer"
                            :class "vertical-tab"
                            :content (printer-screen/main-pane)
                            :on-selection-changed (event-handler [_]))

        tabs-p (ui/tab-pane :tabs [flows-tab browser-tab taps-tab docs-tab timeline-tab printer-tab]
                            :rotate? true
                            :closing-policy :unavailable
                            :side :left
                            :on-tab-change (fn [_ to-tab]
                                             (cond
                                               (= to-tab browser-tab) (browser-screen/get-all-namespaces)
                                               (= to-tab printer-tab) (printer-screen/update-prints-controls))))
        _ (store-obj "main-tools-tab" tabs-p)]

    tabs-p))

(defn- toggle-debug-mode []
  (dbg-state/toggle-debug-mode)
  (log (format "DEBUG MODE %s" (if (:debug-mode? (dbg-state/debugger-config)) "ENABLED" "DISABLED"))))

(defn- ask-and-set-threads-limit []
  (let [{:keys [text bool]} (ui/ask-text-and-bool-dialog
                             :header "Set threads trace limit. FlowStorm will stop recording threads which hit the provided trace limit."
                             :body "Limit :"
                             :width  500
                             :height 100
                             :center-on-stage (dbg-state/main-jfx-stage)
                             :bool-msg "Throw on limit?")]

    (when-not (str/blank? text)
      (runtime-api/set-thread-trace-limit rt-api {:limit (Integer/parseInt text) :break? bool}))))

(defn- build-menu-bar []
  (let [view-menu (ui/menu :label "_View"
                           :items [{:text "Bookmarks"
                                    :on-click (fn [] (bookmarks/show-bookmarks))}
                                   {:text "Search"
                                    :on-click (fn [] (search/search-window))}
                                   {:text "Toggle theme"
                                    :on-click (fn []
                                                (dbg-state/rotate-theme)
                                                (dbg-state/reset-theming))
                                    :accel {:mods [:ctrl]
                                            :key-code KeyCode/T}}
                                   {:text "Increase font size"
                                    :on-click (fn []
                                                (dbg-state/inc-font-size)
                                                (dbg-state/reset-theming))
                                    :accel {:mods [:ctrl :shift]
                                            :key-code KeyCode/EQUALS}}
                                   {:text "Decrease font size"
                                    :on-click (fn []
                                                (dbg-state/dec-font-size)
                                                (dbg-state/reset-theming))
                                    :accel {:mods [:ctrl]
                                            :key-code KeyCode/MINUS}}
                                   {:text "Toggle debug mode"
                                    :on-click (fn [] (toggle-debug-mode))
                                    :accel {:mods [:ctrl]
                                            :key-code KeyCode/D}}])
        actions-menu (ui/menu :label "_Actions"
                              :items [{:text "Clear recordings"
                                       :on-click (fn [] (clear-all))
                                       :accel {:mods [:ctrl]
                                               :key-code KeyCode/L}}
                                      {:text "Unblock all threads"
                                       :on-click (fn [] (runtime-api/unblock-all-threads rt-api))
                                       :accel {:mods [:ctrl]
                                               :key-code KeyCode/U}}])
        config-menu (ui/menu :label "_Config"
                             :items [{:text "Set threads limit"
                                      :on-click (fn [] (ask-and-set-threads-limit))}])
        help-menu (ui/menu :label "_Help"
                           :items [{:text "Tutorial"
                                    :on-click (fn []
                                                (if (dbg-state/clojure-storm-env?)
                                                  (tut-basics/start-tutorials-ui)
                                                  (show-message "This tutorial is not available in vanilla mode" :warning)))}
                                   {:text "User Guide"
                                    :on-click (fn [] (user-guide/show-user-guide))}])]

    (ui/menu-bar :menues [view-menu actions-menu config-menu help-menu])))

(defn- build-top-tool-bar-pane []
  (let [record-btn (ui/icon-button :icon-name "mdi-record"
                                   :tooltip "Start/Stop recording"
                                   :on-click (fn [] (runtime-api/toggle-recording rt-api))
                                   :classes ["record-btn"])
        task-cancel-btn (ui/icon-button :icon-name "mdi-playlist-remove"
                                        :tooltip "Cancel current running task (search, etc) (Ctrl-g)"
                                        :on-click (fn [] (runtime-api/interrupt-all-tasks rt-api))
                                        :disable true)
        clear-btn (ui/icon-button :icon-name  "mdi-delete-forever"
                                  :tooltip "Clean all debugger and runtime values references (Ctrl-l)"
                                  :on-click (fn [] (clear-all)))
        search-btn (ui/icon-button :icon-name "mdi-magnify"
                                   :tooltip "Open the search window"
                                   :on-click (fn [] (search/search-window)))
        quick-jump-textfield (ui/h-box
                              :childs [(ui/label :text "Quick jump:")
                                       (ui/autocomplete-textfield
                                        :get-completions
                                        (fn []
                                          (into []
                                                (keep (fn [[fq-fn-name cnt]]
                                                        (when-not (re-find #"/fn--[\d]+$" fq-fn-name)
                                                            {:text (format "%s (%d)" fq-fn-name cnt)
                                                             :on-select (fn []
                                                                          (tasks/submit-task runtime-api/find-fn-call-task
                                                                                             [(symbol fq-fn-name) 0 {}]
                                                                                             {:on-finished (fn [{:keys [result]}]
                                                                                                             (when result
                                                                                                               (flows-screen/goto-location result)))}))})))
                                                (runtime-api/all-fn-call-stats rt-api))))]
                              :align :center-left)

        exceptions-menu-data (ui/menu-button
                              :title "Exceptions"
                              :on-action (fn [loc] (flows-screen/goto-location loc))
                              :items []
                              :class "important-combo")
        exceptions-box (ui/h-box :childs [(:menu-button exceptions-menu-data)]
                                 :class "hidden-pane"
                                 :align :center-left)

        tools [record-btn
               clear-btn
               task-cancel-btn
               search-btn
               quick-jump-textfield
               exceptions-box]]

    (store-obj "task-cancel-btn" task-cancel-btn)
    (store-obj "exceptions-box" exceptions-box)
    (store-obj "exceptions-menu-data" exceptions-menu-data)
    (store-obj "record-btn" record-btn)
    (ui/toolbar :childs tools)))

(defn- build-top-bar-pane []
  (ui/v-box
   :childs [(build-menu-bar)
            (build-top-tool-bar-pane)]
   :spacing 5))

(defn set-task-cancel-btn-enable [enable?]
  (ui-utils/run-later
    (let [[^Button task-cancel-btn] (obj-lookup "task-cancel-btn")]
      (.setDisable task-cancel-btn (not enable?))
      (if enable?
        (ui-utils/add-class task-cancel-btn "attention")
        (ui-utils/rm-class task-cancel-btn "attention")))))

(defn set-recording-btn [recording?]
  (ui-utils/run-later
    (let [[record-btn] (obj-lookup "record-btn")]
      (ui-utils/update-button-icon
       record-btn
       (if recording?
         "mdi-pause"
         "mdi-record")))))

(defn- build-main-pane []
  (ui/border-pane :top (build-top-bar-pane)
                  :center (main-tabs-pane)
                  :bottom (bottom-box)
                  :class "main-pane"))

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

(defn stop-ui []
  (let [{:keys [theme-listener]} ui]

    ;; remove the OS theme listener
    (when theme-listener
      (log "Removing os theme-listener")
      (.removeListener (OsThemeDetector/getDetector) theme-listener))

    ;; close all stages
    (when-not *killing-ui-from-window-close?*
      (doseq [stage (dbg-state/jfx-stages)]
        (ui-utils/run-now (.close ^Stage stage))))))

(defn create-flow [{:keys [flow-id timestamp]}]
  ;; lets clear the entire cache every time a flow gets created, just to be sure
  ;; we don't reuse old flows values on this flow
  (runtime-api/clear-api-cache rt-api)

  (dbg-state/create-flow flow-id timestamp)
  (flows-screen/remove-flow flow-id)
  (flows-screen/create-empty-flow flow-id)
  (ui-general/select-main-tools-tab :flows)
  (flows-screen/update-threads-list flow-id))

(defn setup-ui-from-runtime-config
  "This function is meant to be called after all the system has started,
  to configure the part of UI that depends on runtime state."
  []
  (ui-utils/run-later
   (when-let [{:keys [storm? recording? total-order-recording?] :as runtime-config} (runtime-api/runtime-config rt-api)]
     (log (str "Runtime config retrieved :" runtime-config))
     (let [all-flows-ids (->> (runtime-api/all-flows-threads rt-api)
                              (map first)
                              (into #{}))]
       (dbg-state/set-runtime-config runtime-config)
       (set-recording-btn recording?)
       (timeline-screen/set-recording-check total-order-recording?)
       (printer-screen/update-prints-controls)

       (when storm?
         (let [storm-prefixes (runtime-api/get-storm-instrumentation rt-api)]
           (browser-screen/enable-storm-controls)
           (browser-screen/update-storm-instrumentation storm-prefixes)))

       (doseq [fid all-flows-ids]
         (create-flow {:flow-id fid}))))))

(defn open-flow-threads-menu [flow-id]
  (flows-screen/select-flow-tab flow-id)
  (let [[{:keys [menu-button]}] (obj-lookup flow-id "flow_threads_menu")]
    (when menu-button
      (.show menu-button))))

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

            theme-listener (when (= :auto (:theme config))
                             (start-theme-listener
                              (fn [dark?]
                                (dbg-state/set-theme (if dark? :dark :light))
                                (dbg-state/reset-theming))))]

        (dbg-state/register-jfx-stage! stage)
        (dbg-state/reset-theming)

        (doto scene
          (.setOnKeyPressed (event-handler
                                [^KeyEvent kev]
                              (let [key-name (.getName (.getCode kev))]

                                (cond

                                  (key-combo-match? kev "g" [:ctrl])
                                  (runtime-api/interrupt-all-tasks rt-api)

                                  (key-combo-match? kev "f" [:shift]) (ui-general/select-main-tools-tab :flows)
                                  (key-combo-match? kev "b" [:shift]) (ui-general/select-main-tools-tab :browser)
                                  (key-combo-match? kev "t" [:shift]) (ui-general/select-main-tools-tab :taps)
                                  (key-combo-match? kev "d" [:shift]) (ui-general/select-main-tools-tab :docs)
                                  (= key-name "0")                    (open-flow-threads-menu 0)
                                  (= key-name "1")                    (open-flow-threads-menu 1)
                                  (= key-name "2")                    (open-flow-threads-menu 2)
                                  (= key-name "3")                    (open-flow-threads-menu 3)
                                  (= key-name "4")                    (open-flow-threads-menu 4)
                                  (= key-name "5")                    (open-flow-threads-menu 5)
                                  (= key-name "6")                    (open-flow-threads-menu 6)
                                  (= key-name "7")                    (open-flow-threads-menu 7)
                                  (= key-name "8")                    (open-flow-threads-menu 8)
                                  (= key-name "9")                    (open-flow-threads-menu 9)))))
          (.setRoot (build-main-pane)))

        (-> stage .show)

        {:theme-listener theme-listener})

      (catch Exception e
        (log-error "UI Thread exception" e)))))
