(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.ui.flows.bookmarks :as bookmarks]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler key-combo-match?]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup clean-objs]])
  (:import [javafx.scene.control Tab TabPane ListView]
           [javafx.scene.layout Pane]
           [javafx.scene.input KeyEvent]))


(declare create-or-focus-thread-tab)
(declare update-exceptions-combo)

(defn remove-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        [flow-tab] (obj-lookup flow-id "flow_tab")]

    (when (and flows-tabs-pane flow-tab)
      (ui-utils/rm-tab-pane-tab flows-tabs-pane flow-tab))

    ;; clean ui state objects
    (clean-objs flow-id)))

(defn fully-remove-flow [flow-id]
  ;; remove it from our state
  (dbg-state/remove-flow flow-id)

  ;; let runtime know we are not interested in this flow anymore
  (runtime-api/discard-flow rt-api flow-id)

  ;; remove it from the ui
  (remove-flow flow-id)

  ;; remove all bookmarks associated to this flow
  (bookmarks/remove-bookmarks flow-id)

  (dbg-state/remove-unwinds flow-id)
  (ui-utils/run-later (update-exceptions-combo)))

(defn- setup-thread-keybindngs [flow-id thread-id pane]
  (.setOnKeyPressed
   ^Pane pane
   (event-handler
       [^KeyEvent kev]
     (let [key-txt (.getText kev)]
       (cond
         (= key-txt "t") (ui-general/select-thread-tool-tab flow-id thread-id :call-tree)
         (= key-txt "c") (ui-general/select-thread-tool-tab flow-id thread-id :code)
         (= key-txt "f") (ui-general/select-thread-tool-tab flow-id thread-id :functions)

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

    (ui-general/select-thread-tool-tab flow-id (:thread/id thread-info) :call-tree)))

(defn update-threads-list [flow-id]
  (let [[{:keys [set-items] :as menu-data}] (obj-lookup flow-id "flow_threads_menu")]
    (when menu-data
      (let [threads-info (runtime-api/flow-threads-info rt-api flow-id)
            [threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")]

        (doseq [tinfo threads-info]
          (dbg-state/update-thread-info (:thread/id tinfo) tinfo))

        (when (and (seq threads-info)
                   threads-tabs-pane
                   (zero? (count (.getTabs threads-tabs-pane))))
          (open-thread (first threads-info)))

        (set-items threads-info)))))

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
        flow-box (ui/anchor-pane
                  :childs [{:node threads-tab-pane
                            :top-anchor 5.0
                            :left-anchor 5.0
                            :right-anchor 5.0
                            :bottom-anchor 5.0}
                           {:node menu-button
                            :top-anchor 8.0
                            :left-anchor 10.0}])

        flow-tab (ui/tab :id (str "flow-tab-" flow-id)
                         :text (str "flow-" flow-id)
                         :content flow-box)]

    (.setOnCloseRequest ^Tab flow-tab
                        (event-handler
                            [ev]
                          (fully-remove-flow flow-id)
                          ;; since we are destroying this tab, we don't need
                          ;; this event to propagate anymore
                          (ui-utils/consume ev)))

    (store-obj flow-id "threads_tabs_pane" threads-tab-pane)
    (store-obj flow-id "flow_tab" flow-tab)
    (store-obj flow-id "flow_threads_menu" menu-btn-data)

    (update-threads-list flow-id)

    (ui-utils/add-tab-pane-tab flows-tabs-pane flow-tab)))

(defn- create-thread-pane [flow-id thread-id]
  (let [code-tab (ui/tab :graphic (ui/icon :name "mdi-code-parentheses")
                         :content (flow-code/create-code-pane flow-id thread-id)
                         :tooltip "Code tool. Allows you to step over the traced code.")

        callstack-tree-tab (ui/tab :graphic (ui/icon :name "mdi-file-tree")
                                   :content (flow-tree/create-call-stack-tree-pane flow-id thread-id)
                                   :tooltip "Call tree tool. Allows you to explore the recorded execution tree.")

        instrument-tab (ui/tab :graphic (ui/icon :name "mdi-format-list-numbers")
                               :content (flow-fns/create-functions-pane flow-id thread-id)
                               :tooltip "Functions list tool. Gives you a list of all function calls and how many time they have been called.")
        thread-tools-tab-pane (ui/tab-pane :tabs [callstack-tree-tab code-tab instrument-tab]
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

        (ui-utils/selection-select-obj sel-model thread-tab)))))

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
  (ui-general/select-main-tools-tab :flows)
  (select-flow-tab flow-id)
  (open-thread (assoc (dbg-state/get-thread-info thread-id)
                      :flow/id flow-id))
  (ui-general/select-thread-tool-tab flow-id thread-id :code)
  (flow-code/jump-to-coord flow-id
                           thread-id
                           (runtime-api/timeline-entry rt-api flow-id thread-id idx :at)))

(defn update-exceptions-combo []
  (let [unwinds (dbg-state/get-fn-unwinds)
        [{:keys [set-items]}] (obj-lookup "exceptions-menu-data")
        [ex-box] (obj-lookup "exceptions-box")]
    (ui-utils/clear-classes ex-box)
    (when (zero? (count unwinds))
      (ui-utils/add-class ex-box "hidden-pane"))

    (set-items (mapv (fn [{:keys [flow-id thread-id idx fn-ns fn-name ex-type ex-message]}]
                       {:text (format "%d - %s/%s %s" idx fn-ns fn-name ex-type)
                        :tooltip ex-message
                        :flow-id flow-id
                        :thread-id thread-id
                        :idx idx})
                     unwinds))))

(defn main-pane []
  (let [flows-tpane (ui/tab-pane :closing-policy :all-tabs
                                 :side :top
                                 :class "flows-tab-pane")
        flows-combo (ui/combo-box :items (into [] (range 10))
                                  :button-factory (fn [_ i] (ui/label :text (str "Rec on flow-" i)))
                                  :cell-factory (fn [_ i] (ui/label :text (str "flow-" i)))
                                  :on-change (fn [_ new-flow-id]
                                               (runtime-api/switch-record-to-flow rt-api new-flow-id))
                                  :class "hl-combo")
        flow-anchor (ui/anchor-pane
                     :childs [{:node flows-tpane
                               :top-anchor 5.0
                               :left-anchor 5.0
                               :right-anchor 5.0
                               :bottom-anchor 5.0}
                              {:node flows-combo
                               :top-anchor 8.0
                               :left-anchor 10.0}])]

    (store-obj "flows_tabs_pane" flows-tpane)
    flow-anchor))
