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
            [flow-storm.debugger.ui.outputs.screen :as outputs-screen]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.ui.flows.bookmarks :as bookmarks]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.state :as dbg-state :refer [obj-lookup store-obj]]
            [flow-storm.utils :as utils :refer [log log-error]]
            [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.docs]
            [flow-storm.debugger.tutorials.basics :as tut-basics]
            [flow-storm.debugger.user-guide :as user-guide]
            [flow-storm.debugger.ui.plugins :as plugins]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [com.jthemedetecor OsThemeDetector]
           [java.awt Taskbar Toolkit Taskbar$Feature]
           [javafx.scene Scene]
           [javafx.scene.image Image]
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
  (ui-utils/run-later
   (outputs-screen/clear-outputs-ui)

   (doseq [fid (dbg-state/all-flows-ids)]
     (flows-screen/clear-debugger-flow fid))))

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
  (when (pos? max-heap-bytes)
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
                          :content (flows-screen/main-pane)
                          :id "tool-flows")
        browser-tab (ui/tab :text "Browser"
                            :class "vertical-tab"
                            :content (browser-screen/main-pane)
                            :id "tool-browser")
        outputs-tab (ui/tab :text "Outputs"
                            :class "vertical-tab"
                            :content (outputs-screen/main-pane)
                            :on-selection-changed (event-handler [_])
                            :id "tool-outputs")
        docs-tab (ui/tab :text "Docs"
                         :class "vertical-tab"
                         :content (docs-screen/main-pane)
                         :on-selection-changed (event-handler [_])
                         :id "tool-docs")
        plugins-tabs (->> (plugins/plugins)
                          (mapv (fn [p]
                                  (ui/tab :text (:plugin/label p)
                                          :class "vertical-tab"
                                          :content (ui/border-pane
                                                    :center (:fx/node (plugins/create-plugin (:plugin/key p)))
                                                    :class (name (:plugin/key p))
                                                    :paddings [10 10 10 10])
                                          :id (:plugin/key p)))))
        tabs-p (ui/tab-pane :tabs (into [flows-tab browser-tab outputs-tab docs-tab] plugins-tabs)
                            :rotate? true
                            :closing-policy :unavailable
                            :side :left
                            :on-tab-change (fn [_ to-tab]
                                             (dbg-state/set-selected-tool (keyword (.getId to-tab)))
                                             (cond
                                               (= to-tab browser-tab) (browser-screen/get-all-namespaces)
                                               :else (let [p (some (fn [p]
                                                                     (when (= (.getId to-tab) (str (:plugin/key p)))
                                                                       p))
                                                                   (plugins/plugins))]
                                                       (when-let [{:keys [plugin/on-focus plugin/create-result]} p]
                                                         (when on-focus
                                                           (on-focus create-result)))))))
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

(defn- ask-and-set-heap-limit []
  (let [{:keys [text bool]} (ui/ask-text-and-bool-dialog
                             :header "Set heap limit. FlowStorm will stop recording when this heap limit in MBs is reached."
                             :body "Limit :"
                             :width  500
                             :height 100
                             :center-on-stage (dbg-state/main-jfx-stage)
                             :bool-msg "Throw on limit?")]

    (when-not (str/blank? text)
      (runtime-api/set-heap-limit rt-api {:limit (Integer/parseInt text) :break? bool}))))

(defn- goto-file-line []
  (let [file-and-line (ui/ask-text-dialog :header "Goto file and line"
                                          :body   "<classpath-file-path>:<line>"
                                          :width  800
                                          :height 200
                                          :center-on-stage (dbg-state/main-jfx-stage))
        [file line] (when file-and-line
                      (str/split file-and-line #":"))]
    (when file-and-line
      (tasks/submit-task runtime-api/find-expr-entry-task
                         [{:file file
                           :line (Integer/parseInt line)}]
                         {:on-finished (fn [{:keys [result]}]
                                         (if result
                                           (let [{:keys [flow-id thread-id idx]} result]
                                             (flows-screen/goto-location {:flow-id   flow-id
                                                                          :thread-id thread-id
                                                                          :idx       idx}))
                                           (show-message (format "No recordings found for file %s at line %s" file line) :info)))}))))

(defn- build-menu-bar []
  (let [view-menu (ui/menu :label "_View"
                           :items [{:text "Bookmarks"
                                    :on-click (fn [] (bookmarks/show-bookmarks))}
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
                                   {:text "Toggle log mode (for debugging FlowStorm)"
                                    :on-click (fn [] (toggle-debug-mode))
                                    :accel {:mods [:ctrl]
                                            :key-code KeyCode/D}}])
        actions-menu (ui/menu :label "_Actions"
                              :items [{:text "Clear all"
                                       :on-click (fn []
                                                   ;; this will cause the runtime to fire back flow discarded events
                                                   ;; that will get rid of the flows UI side of things
                                                   (runtime-api/clear-runtime-state rt-api)
                                                   (runtime-api/clear-api-cache rt-api)
                                                   (outputs-screen/clear-outputs-ui))}
                                      {:text "Clear current tool"
                                       :on-click (fn []
                                                   (case (dbg-state/selected-tool)
                                                     :tool-flows   (flows-screen/discard-all-flows)
                                                     :tool-browser nil
                                                     :tool-outputs (outputs-screen/clear-outputs)
                                                     :tool-docs    nil
                                                     ;; TODO: execute clear on the selected plugin ??
                                                     nil))
                                       :accel {:mods [:ctrl]
                                               :key-code KeyCode/L}}
                                      {:text "Unblock all threads"
                                       :on-click (fn [] (runtime-api/unblock-all-threads rt-api))
                                       :accel {:mods [:ctrl]
                                               :key-code KeyCode/U}}
                                      {:text "Goto file:line"
                                       :on-click (fn [] (goto-file-line))}])
        config-menu (ui/menu :label "_Config"
                             :items [{:text "Set threads limit"
                                      :on-click (fn [] (ask-and-set-threads-limit))}
                                     {:text "Set heap limit"
                                      :on-click (fn [] (ask-and-set-heap-limit))}
                                     {:text "Auto jump to exceptions"
                                      :check-item? true
                                      :checked? (:auto-jump-on-exception? (dbg-state/debugger-config))
                                      :on-click (fn [enable?] (dbg-state/set-auto-jump-on-exception enable?))}
                                     {:text "Auto update UI"
                                      :check-item? true
                                      :checked? (:auto-update-ui? (dbg-state/debugger-config))
                                      :on-click (fn [enable?] (dbg-state/set-auto-update-ui enable?))}
                                     {:text "Pretty print previews"
                                      :check-item? true
                                      :checked? (:pprint-previews? (dbg-state/debugger-config))
                                      :on-click (fn [enable?] (dbg-state/set-pprint-previews enable?))}
                                     {:text "Call tree update"
                                      :check-item? true
                                      :checked? (:call-tree-update? (dbg-state/debugger-config))
                                      :on-click (fn [enable?] (dbg-state/set-call-tree-update enable?))}])
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
  (let [task-cancel-btn (ui/icon-button :icon-name "mdi-playlist-remove"
                                        :tooltip "Cancel current running task (search, etc) (Ctrl-g)"
                                        :on-click (fn [] (runtime-api/interrupt-all-tasks rt-api))
                                        :disable true)
        inst-toggle (ui/toggle-button {:label "Inst Enable"
                                       :on-change (fn [on?]
                                                    (if (dbg-state/clojure-storm-env?)
                                                      (runtime-api/turn-storm-instrumentation rt-api on?)
                                                      (show-message "This functionality is only available in Storm modes" :warning)))})

        tools [task-cancel-btn inst-toggle]]
    (store-obj "task-cancel-btn" task-cancel-btn)
    (store-obj "inst-toggle-btn" inst-toggle)
    (ui/toolbar :childs tools)))

(defn set-instrumentation-ui [enable?]
  (let [[inst-toggle] (obj-lookup "inst-toggle-btn")]
    (.setSelected inst-toggle enable?)))

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
    (if *killing-ui-from-window-close?*
      ;; if we are comming from window-close close all the stages but
      ;; the first (the one being closed) in the javafx thread
      (doseq [stage (rest (dbg-state/jfx-stages))]
        (.close ^Stage stage))

      ;; if we are not comming from a windows close, like a stop-system
      ;; then block until we close all stages on the javafx thread
      (ui-utils/run-now
        (doseq [stage (dbg-state/jfx-stages)]
          (.close ^Stage stage))))))

(defn create-flow [{:keys [flow-id timestamp]}]
  ;; lets clear the entire cache every time a flow gets created, just to be sure
  ;; we don't reuse old flows values on this flow
  (runtime-api/clear-api-cache rt-api)

  ;; make sure with discard any previous flow for the same id
  (flows-screen/clear-debugger-flow flow-id)

  (dbg-state/create-flow flow-id timestamp)
  (flows-screen/create-empty-flow flow-id)
  (ui-general/select-main-tools-tab "tool-flows")
  (flows-screen/update-threads-list flow-id))

(defn setup-ui-from-runtime-config
  "This function is meant to be called after all the system has started,
  to configure the part of UI that depends on runtime state."
  []
  (ui-utils/run-later
   (when-let [{:keys [storm? recording? total-order-recording? flow-storm-nrepl-middleware?] :as runtime-config} (runtime-api/runtime-config rt-api)]
     (log (str "Runtime config retrieved :" runtime-config))
     (let [all-flows-ids (->> (runtime-api/all-flows-threads rt-api)
                              (map first)
                              (into #{}))]
       (dbg-state/set-runtime-config runtime-config)
       (flows-screen/set-recording-btn recording?)
       (flows-screen/set-multi-timeline-recording-btn total-order-recording?)

       (when storm?
         (let [storm-prefixes (runtime-api/get-storm-instrumentation rt-api)]
           (browser-screen/enable-storm-controls)
           (browser-screen/update-storm-instrumentation storm-prefixes)))

       (when-not flow-storm-nrepl-middleware?
         (outputs-screen/set-middleware-not-available))

       (doseq [fid all-flows-ids]
         (create-flow {:flow-id fid}))))))

(defn setup-instrumentation-ui []
  (ui-utils/run-later
    (when (dbg-state/clojure-storm-env?)
      (let [inst-enable? (runtime-api/storm-instrumentation-enable? rt-api)]
        (set-instrumentation-ui inst-enable?)))))

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
                       (binding [*killing-ui-from-window-close?* true]
                         ((resolve 'flow-storm.debugger.main/stop-debugger))))))

            theme-listener (when (= :auto (:theme config))
                             (start-theme-listener
                              (fn [dark?]
                                (dbg-state/set-theme (if dark? :dark :light))
                                (dbg-state/reset-theming))))]

        (dbg-state/register-jfx-stage! stage)
        (dbg-state/reset-theming)

        ;; set icon on application bar
        (.add (.getIcons stage) (Image. (io/input-stream (io/resource "flowstorm/icons/icon.png"))))

        ;; set icon on taskbar/dock
        (when (Taskbar/isTaskbarSupported)
          (let [taskbar (Taskbar/getTaskbar)]
            (when (.isSupported taskbar Taskbar$Feature/ICON_IMAGE)
              (.setIconImage taskbar
                             (.getImage (Toolkit/getDefaultToolkit)
                                        (io/resource "flowstorm/icons/icon.png"))))))

        (doto scene
          (.setOnKeyPressed (event-handler
                                [^KeyEvent kev]
                              (let [key-name (.getName (.getCode kev))]

                                (cond

                                  (key-combo-match? kev "g" [:ctrl])
                                  (runtime-api/interrupt-all-tasks rt-api)

                                  (key-combo-match? kev "f" [:shift]) (ui-general/select-main-tools-tab "tool-flows")
                                  (key-combo-match? kev "b" [:shift]) (ui-general/select-main-tools-tab "tool-browser")
                                  (key-combo-match? kev "o" [:shift]) (ui-general/select-main-tools-tab "tool-outputs")
                                  (key-combo-match? kev "d" [:shift]) (ui-general/select-main-tools-tab "tool-docs")
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
