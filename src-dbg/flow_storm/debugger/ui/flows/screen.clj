(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.ui.flows.search :as search]
            [flow-storm.debugger.ui.flows.bookmarks :as bookmarks]
            [flow-storm.debugger.ui.flows.multi-thread-timeline :as multi-thread-timeline]
            [flow-storm.debugger.ui.flows.printer :as printer]
            [flow-storm.debugger.ui.tasks :as tasks]
            [flow-storm.debugger.ui.plugins :as plugins]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler key-combo-match?]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup clean-objs]])
  (:import [javafx.scene.control Tab TabPane ListView]
           [javafx.scene.layout Pane VBox Priority]
           [javafx.scene.input KeyEvent]))


(declare create-or-focus-thread-tab)
(declare update-exceptions-combo)

(defn clear-debugger-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        [flow-tab] (obj-lookup flow-id "flow_tab")]

    (when (and flows-tabs-pane flow-tab)
      (ui-utils/rm-tab-pane-tab flows-tabs-pane flow-tab))

    ;; remove all bookmarks, mt-timeline and prints ui associated to this flow
    (bookmarks/remove-bookmarks flow-id)
    (multi-thread-timeline/clear-timeline-ui flow-id)
    (printer/clear-prints-ui flow-id)

    (update-exceptions-combo flow-id)

    (dbg-state/remove-flow flow-id)

    ;; clean ui state objects
    (clean-objs flow-id)

    ;; notify all plugins
    (doseq [{:keys [plugin/on-flow-clear plugin/create-result]} (plugins/plugins)]
      (when on-flow-clear
        (on-flow-clear flow-id create-result)))))

(defn discard-all-flows []
  (doseq [fid (dbg-state/all-flows-ids)]
    (runtime-api/discard-flow rt-api fid)))

(defn- setup-thread-keybindngs [flow-id thread-id pane]
  (.setOnKeyPressed
   ^Pane pane
   (event-handler
       [^KeyEvent kev]
     (let [key-txt (.getText kev)]
       (cond
         (= key-txt "t") (ui-general/select-thread-tool-tab flow-id thread-id "flows-call-tree")
         (= key-txt "c") (ui-general/select-thread-tool-tab flow-id thread-id "flows-code-stepper")
         (= key-txt "f") (ui-general/select-thread-tool-tab flow-id thread-id "flows-functions")

         (= key-txt "P") (flow-code/step-prev-over flow-id thread-id)
         (= key-txt "p") (flow-code/step-prev flow-id thread-id)
         (= key-txt "n") (flow-code/step-next flow-id thread-id)
         (= key-txt "N") (flow-code/step-next-over flow-id thread-id)
         (= key-txt "^") (flow-code/step-out flow-id thread-id)
         (= key-txt "<") (flow-code/step-first flow-id thread-id)
         (= key-txt ">") (flow-code/step-last flow-id thread-id)

         (key-combo-match? kev "f" [:ctrl :shift])  (flow-code/copy-current-frame-symbol flow-id thread-id true)
         (key-combo-match? kev "f" [:ctrl])         (flow-code/copy-current-frame-symbol flow-id thread-id false)

         (key-combo-match? kev "z" [:ctrl]) (flow-code/undo-jump flow-id thread-id)
         (key-combo-match? kev "r" [:ctrl]) (flow-code/redo-jump flow-id thread-id))))))

(defn open-thread [thread-info]
  (let [flow-id (:flow/id thread-info)
        thread-id (:thread/id thread-info)
        thread-name (:thread/name thread-info)]

    (when-not (dbg-state/get-thread flow-id thread-id)
      (dbg-state/create-thread flow-id thread-id))

    (create-or-focus-thread-tab flow-id thread-id thread-name)

    (when-let [tl-entry (runtime-api/timeline-entry rt-api flow-id thread-id 0 :at)]
      (flow-code/jump-to-coord flow-id thread-id tl-entry))

    (ui-general/select-thread-tool-tab flow-id (:thread/id thread-info) "flows-code-stepper")))

(defn update-outdated-thread-ui [flow-id thread-id]
  (flow-tree/update-call-stack-tree-pane flow-id thread-id)
  (flow-fns/update-functions-pane flow-id thread-id)
  (flow-code/update-thread-trace-count-lbl flow-id thread-id))

(defn make-outdated-thread [flow-id thread-id]
  (when-let [[^Tab tab] (obj-lookup flow-id thread-id "tab")]
    (let [th-info (dbg-state/get-thread-info thread-id)
          thread-label (ui/thread-label (:thread/id th-info)  (:thread/name th-info))
          refresh-tab-content (ui/h-box
                               :childs [(ui/label :text thread-label)
                                        (ui/icon-button :icon-name "mdi-reload"
                                                        :tooltip "There are new recordings for this thread, click this button to update the UI."
                                                        :on-click (fn []
                                                                    (update-outdated-thread-ui flow-id thread-id)
                                                                    (doto tab
                                                                      (.setText thread-label)
                                                                      (.setGraphic nil)))
                                                        :classes ["thread-refresh" "btn-sm"])])]
      (doto tab
        (.setText nil)
        (.setGraphic refresh-tab-content)))))

(defn update-threads-list [flow-id]
  (let [[{:keys [set-items menu-button] :as menu-data}] (obj-lookup flow-id "flow_threads_menu")]
    (when menu-data
      (let [threads-info (runtime-api/flow-threads-info rt-api flow-id)
            [threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")]

        (doseq [tinfo threads-info]
          (dbg-state/update-thread-info (:thread/id tinfo) tinfo))

        (when (and (seq threads-info)
                   threads-tabs-pane
                   (zero? (count (.getTabs threads-tabs-pane))))
          (open-thread (first threads-info)))

        (set-items threads-info)
        (.setText menu-button (format "Threads [%d]" (count threads-info)))))))

(defn select-flow-tab [flow-id]
  (let [[^TabPane flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        sel-model (.getSelectionModel flows-tabs-pane)
        all-tabs (.getTabs flows-tabs-pane)
        tab-for-flow (some (fn [^Tab t]
                             (when (= (.getId t) (str "flow-tab-" flow-id))
                               t))
                           all-tabs)]

    ;; select the flow tab
    (when tab-for-flow
      (ui-utils/selection-select-obj sel-model tab-for-flow)

      ;; focus the threads list
      (let [[{:keys [^ListView list-view]}] (obj-lookup flow-id "flow_threads_list")]
        (when list-view
          (let [list-selection (.getSelectionModel list-view)]
            (.requestFocus list-view)
            (ui-utils/selection-select-first list-selection)))))))

(defn goto-location [{:keys [flow-id thread-id idx]}]
  (ui-general/select-main-tools-tab "tool-flows")
  (select-flow-tab flow-id)
  (open-thread (assoc (dbg-state/get-thread-info thread-id)
                      :flow/id flow-id))
  (ui-general/select-thread-tool-tab flow-id thread-id "flows-code-stepper")
  (flow-code/jump-to-coord flow-id
                           thread-id
                           (runtime-api/timeline-entry rt-api flow-id thread-id idx :at)))

(defn- build-flow-tool-bar-pane [flow-id]
  (let [quick-jump-textfield (ui/h-box
                              :childs [(ui/label :text "Quick jump:")
                                       (ui/autocomplete-textfield
                                        :get-completions
                                        (fn []
                                          (into []
                                                (keep (fn [{:keys [fn-ns fn-name cnt]}]
                                                        (when-not (re-find #"fn--[\d]+$" fn-name)
                                                          {:text (format "%s/%s (%d)" fn-ns fn-name cnt)
                                                           :on-select (fn []
                                                                        (tasks/submit-task runtime-api/find-fn-call-task
                                                                                           [(symbol fn-ns fn-name) 0 {:flow-id flow-id}]
                                                                                           {:on-finished (fn [{:keys [result]}]
                                                                                                           (when result
                                                                                                             (goto-location (assoc result
                                                                                                                              :flow-id flow-id))))}))})))
                                                (runtime-api/fn-call-stats rt-api flow-id nil))))]
                              :align :center-left)

        exceptions-menu-data (ui/menu-button
                              :title "Exceptions"
                              :on-action (fn [loc] (goto-location loc))
                              :items []
                              :class "important-combo")
        exceptions-box (ui/h-box :childs [(:menu-button exceptions-menu-data)]
                                 :class "hidden-pane"
                                 :align :center-left)
        tools-menu  (ui/menu-button :title "More tools"
                                    :items [{:key :search
                                             :text "Search"}
                                            {:key :multi-thread-timeline
                                             :text "Multi-thread timeline browser"}
                                            {:key :printers
                                             :text "Printers"}]

                                    :on-action (fn [item]
                                                 (case (:key item)
                                                   :search                (search/search-window flow-id)
                                                   :multi-thread-timeline (multi-thread-timeline/open-timeline-window flow-id)
                                                   :printers              (printer/open-printers-window flow-id)))
                                    :orientation :right-to-left)
        left-tools-box (ui/h-box :childs [quick-jump-textfield
                                          exceptions-box]
                                 :spacing 4)
        right-tools-box (ui/h-box :childs [(:menu-button tools-menu)]
                                  :spacing 4)]

    (store-obj flow-id "exceptions-box" exceptions-box)
    (store-obj flow-id "exceptions-menu-data" exceptions-menu-data)

    (ui/border-pane :left  left-tools-box
                    :right right-tools-box
                    :paddings [5 5 0 5])))

(defn create-empty-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        threads-tab-pane (ui/tab-pane :closing-policy :all-tabs
                                      :drag-policy :reorder
                                      :class "threads-tab-pane")

        {:keys [menu-button] :as menu-btn-data}
        (ui/menu-button
         :title "Threads"
         :on-action (fn [th] (open-thread th))
         :item-factory (fn [{:keys [thread/name thread/blocked thread/id]}]
                         (if blocked
                           ;; build blocked thread node
                           (let [[bp-var-ns bp-var-name] blocked
                                 thread-unblock-btn (ui/icon-button :icon-name "mdi-play"
                                                                    :on-click (fn []
                                                                                (runtime-api/unblock-thread rt-api id))
                                                                    :classes ["thread-continue-btn"
                                                                              "btn-xs"])]
                             (ui/h-box :childs [(ui/v-box :childs [(ui/label :text (ui/thread-label id name)
                                                                             :class "thread-blocked")
                                                                   (ui/label :text (format "%s/%s" bp-var-ns bp-var-name) :class "light")])
                                                thread-unblock-btn]
                                       :spacing 5))

                           ;; if not blocked just render a label
                           (ui/label :text (ui/thread-label id name))))
         :class "hl-combo")

        flow-toolbar (build-flow-tool-bar-pane flow-id)
        flow-anchor (ui/anchor-pane
                     :childs [{:node threads-tab-pane
                               :top-anchor 5.0
                               :left-anchor 5.0
                               :right-anchor 5.0
                               :bottom-anchor 5.0}
                              {:node menu-button
                               :top-anchor 8.0
                               :left-anchor 10.0}])

        flow-box (ui/v-box :childs [flow-toolbar
                                     flow-anchor])

        flow-tab (ui/tab :id (str "flow-tab-" flow-id)
                         :text (str "flow-" flow-id)
                         :content flow-box)]

    (VBox/setVgrow flow-anchor Priority/ALWAYS)
    (VBox/setVgrow flow-box Priority/ALWAYS)

    (.setOnCloseRequest ^Tab flow-tab
                        (event-handler
                            [ev]
                          (runtime-api/discard-flow rt-api flow-id)
                          ;; since we are destroying this tab, we don't need
                          ;; this event to propagate anymore
                          (ui-utils/consume ev)))

    (store-obj flow-id "threads_tabs_pane" threads-tab-pane)
    (store-obj flow-id "flow_tab" flow-tab)
    (store-obj flow-id "flow_threads_menu" menu-btn-data)

    (update-threads-list flow-id)

    (ui-utils/add-tab-pane-tab flows-tabs-pane flow-tab)))

(defn- create-thread-pane [flow-id thread-id]
  (let [code-stepper-tab (ui/tab :graphic (ui/icon :name "mdi-code-parentheses")
                                 :content (flow-code/create-code-pane flow-id thread-id)
                                 :tooltip "Code tool. Allows you to step over the traced code."
                                 :id "flows-code-stepper")

        callstack-tree-tab (ui/tab :graphic (ui/icon :name "mdi-file-tree")
                                   :content (flow-tree/create-call-stack-tree-pane flow-id thread-id)
                                   :tooltip "Call tree tool. Allows you to explore the recorded execution tree."
                                   :id "flows-call-tree")

        functions-tab (ui/tab :graphic (ui/icon :name "mdi-format-list-numbers")
                              :content (flow-fns/create-functions-pane flow-id thread-id)
                              :tooltip "Functions list tool. Gives you a list of all function calls and how many time they have been called."
                              :id "flows-functions")
        thread-tools-tab-pane (ui/tab-pane :tabs [code-stepper-tab callstack-tree-tab functions-tab]
                                           :side :bottom
                                           :closing-policy :unavailable)]

    (store-obj flow-id thread-id "thread_tool_tab_pane_id" thread-tools-tab-pane)

    thread-tools-tab-pane))

(defn create-or-focus-thread-tab [flow-id thread-id thread-name]
  (let [[^TabPane threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")
        sel-model (.getSelectionModel threads-tabs-pane)
        all-tabs (.getTabs threads-tabs-pane)
        tab-for-thread (some (fn [t]
                               (when (= (.getId ^Tab t) (str thread-id))
                                 t))
                             all-tabs)]

    (if tab-for-thread

      (ui-utils/selection-select-obj sel-model tab-for-thread)


      (let [thread-tab-pane (create-thread-pane flow-id thread-id)
            thread-tab (ui/tab :text (ui/thread-label thread-id thread-name)
                               :content thread-tab-pane
                               :id (str thread-id))]

        (setup-thread-keybindngs flow-id thread-id thread-tab-pane)

        (.setOnCloseRequest ^Tab thread-tab
                            (event-handler
                                [ev]
                              (clean-objs flow-id thread-id)
                              (dbg-state/remove-thread flow-id thread-id)))
        (ui-utils/add-tab-pane-tab threads-tabs-pane thread-tab)

        (store-obj flow-id thread-id "tab" thread-tab)

        (ui-utils/selection-select-obj sel-model thread-tab)))))

(defn update-exceptions-combo [flow-id]
  (let [exceptions (dbg-state/flow-exceptions flow-id)
        [{:keys [set-items]}] (obj-lookup flow-id "exceptions-menu-data")
        [ex-box] (obj-lookup flow-id "exceptions-box")]
    (when ex-box
      (ui-utils/clear-classes ex-box)
      (when (zero? (count exceptions))
        (ui-utils/add-class ex-box "hidden-pane"))

      (set-items (mapv (fn [{:keys [flow-id thread-id idx fn-ns fn-name ex-type ex-message]}]
                         {:text (format "%d - %s/%s %s" idx fn-ns fn-name ex-type)
                          :tooltip ex-message
                          :flow-id flow-id
                          :thread-id thread-id
                          :idx idx})
                       exceptions)))))

(defn set-recording-btn [recording?]
  (ui-utils/run-later
    (let [[record-btn] (obj-lookup "record-btn")]
      (ui-utils/update-button-icon
       record-btn
       (if recording?
         "mdi-pause"
         "mdi-record")))))

(defn set-multi-timeline-recording-btn [recording?]
  (ui-utils/run-later
    (let [[btn] (obj-lookup "multi-timeline-record-btn")]
      (ui-utils/update-button-icon
       btn
       (if recording?
         ["mdi-chart-timeline" "mdi-pause"]
         ["mdi-chart-timeline" "mdi-record"])))))

(defn main-pane []
  (let [flows-tpane (ui/tab-pane :closing-policy :all-tabs
                                 :side :top
                                 :class "flows-tab-pane")
        flows-combo (ui/combo-box :items (into [] (range 10))
                                  :button-factory (fn [_ i] (ui/label :text (str "Rec on flow-" i)))
                                  :cell-factory (fn [_ i] (ui/label :text (str "flow-" i)))
                                  :on-change (fn [_ new-flow-id]
                                               (runtime-api/switch-record-to-flow rt-api new-flow-id))
                                  :classes ["hl-combo" "flows-combo"])
        clear-btn (ui/icon-button :icon-name  "mdi-delete-forever"
                                  :tooltip "Clean all flows (Ctrl-l)"
                                  :on-click (fn [] (discard-all-flows)))
        record-btn (ui/icon-button :icon-name "mdi-record"
                                   :tooltip "Start/Stop recording"
                                   :on-click (fn [] (runtime-api/toggle-recording rt-api))
                                   :classes ["record-btn"])
        multi-timeline-record-btn (ui/icon-button
                                   :icon-name ["mdi-chart-timeline" "mdi-record"]
                                   :tooltip "Start/Stop recording of the multi-thread timeline"
                                   :on-click (fn [] (runtime-api/toggle-multi-timeline-recording rt-api)))
        record-controls (ui/h-box :childs [clear-btn
                                           record-btn
                                           multi-timeline-record-btn
                                           flows-combo]
                                  :paddings [4 4 4 4]
                                  :spacing 4)
        flow-anchor (ui/anchor-pane
                     :childs [{:node flows-tpane
                               :top-anchor 5.0
                               :left-anchor 5.0
                               :right-anchor 5.0
                               :bottom-anchor 5.0}
                              {:node record-controls
                               :top-anchor 8.0
                               :left-anchor 10.0}])
        flows-box (ui/v-box :childs [flow-anchor])]

    (VBox/setVgrow flow-anchor Priority/ALWAYS)
    (VBox/setVgrow flows-box Priority/ALWAYS)

    (store-obj "record-btn" record-btn)
    (store-obj "multi-timeline-record-btn" multi-timeline-record-btn)
    (store-obj "flows_tabs_pane" flows-tpane)
    flows-box))
