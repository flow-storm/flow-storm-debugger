(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler label icon tab-pane tab list-view]]
            [flow-storm.debugger.state :as dbg-state])
  (:import [javafx.scene.input MouseButton]
           [javafx.scene.control SingleSelectionModel SplitPane Tab]
           [javafx.geometry Orientation]))

(declare create-or-focus-thread-tab)

(defn remove-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        [flow-tab] (obj-lookup flow-id "flow_tab")]

    (when flow-tab

      ;; remove the tab from flows_tabs_pane
      (-> flows-tabs-pane
          .getTabs
          (.remove flow-tab)))

    ;; clean ui state vars
    (ui-vars/clean-objs flow-id)))

(defn fully-remove-flow [flow-id]
  ;; remove it from our state
  (dbg-state/remove-flow flow-id)

  ;; let runtime know we are not interested in this flow anymore
  (runtime-api/discard-flow rt-api flow-id)

  ;; remove it from the ui
  (remove-flow flow-id))

(defn create-thread [{:keys [flow-id thread-id thread-name]}]
  (dbg-state/create-thread flow-id thread-id)
  (dbg-state/set-idx flow-id thread-id 0)
  (create-or-focus-thread-tab flow-id thread-id thread-name)
  (flow-code/jump-to-coord flow-id thread-id 0))

(defn update-threads-list [flow-id]
  (let [[{:keys [add-all clear] :as lv-data}] (obj-lookup flow-id "flow_threads_list")]
    (when lv-data
      (clear)
      (add-all (runtime-api/flow-threads-info rt-api flow-id)))))

(defn create-empty-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        threads-tab-pane (tab-pane {:closing-policy :all-tabs
                                    :drag-policy :reorder})
        flow-split-pane (doto (SplitPane.)
                          (.setOrientation (Orientation/HORIZONTAL)))
        flow-tab (if (nil? flow-id)
                   (tab {:graphic (icon "mdi-filter") :content flow-split-pane})
                   (tab {:text (str "flow-" flow-id) :content flow-split-pane}))
        {:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :selection-mode :single
                    :cell-factory-fn (fn [list-cell {:keys [thread/name]}]
                                       (.setText list-cell nil)
                                       (.setGraphic list-cell (label name)))
                    :on-click (fn [mev sel-items _]
                                (when (and (= MouseButton/PRIMARY (.getButton mev))
                                           (= 2 (.getClickCount mev)))
                                  (let [{:keys [thread/id thread/name]} (first sel-items)]
                                    (create-thread {:thread-id id :thread-name name}))))})]

    (-> flow-split-pane
        .getItems
        (.addAll [list-view-pane threads-tab-pane]))

    (.setDividerPosition flow-split-pane 0 0.2)

    (.setOnCloseRequest flow-tab
                        (event-handler
                         [ev]
                         (fully-remove-flow flow-id)
                         ;; since we are destroying this tab, we don't need
                         ;; this event to propagate anymore
                         (.consume ev)))

    (store-obj flow-id "threads_tabs_pane" threads-tab-pane)
    (store-obj flow-id "flow_tab" flow-tab)
    (store-obj flow-id "flow_threads_list" lv-data)

    (update-threads-list flow-id)

    (-> flows-tabs-pane
        .getTabs
        (.addAll [flow-tab]))))

(defn select-code-tools-tab [flow-id thread-id tool]
  (let [[tools-tab] (obj-lookup flow-id thread-id "thread_tool_tab_pane_id")
        sel-model (.getSelectionModel tools-tab)]
    (case tool
      :tree (.select sel-model 0)
      :code (.select sel-model 1)
      :functions (.select sel-model 2))))

(defn- create-thread-pane [flow-id thread-id]
  (let [code-tab (tab {:graphic (icon "mdi-code-parentheses")
                       :content (flow-code/create-code-pane flow-id thread-id)
                       :tooltip "Coode tool. Allows you to step over the traced code."})

        callstack-tree-tab (tab {:graphic (icon "mdi-file-tree")
                                 :content (flow-tree/create-call-stack-tree-pane flow-id thread-id)
                                 :on-selection-changed (event-handler [_] (flow-tree/update-call-stack-tree-pane flow-id thread-id))
                                 :tooltip "Call tree tool. Allows you to explore the recorded execution tree."})

        instrument-tab (tab {:graphic (icon "mdi-format-list-numbers")
                             :content (flow-fns/create-functions-pane flow-id thread-id)
                             :on-selection-changed (event-handler [_] (flow-fns/update-functions-pane flow-id thread-id))
                             :tooltip "Functions list tool. Gives you a list of all function calls and how many time they have been called."})
        thread-tools-tab-pane (tab-pane {:tabs [callstack-tree-tab code-tab instrument-tab]
                                         :side :bottom
                                         :closing-policy :unavailable})]

    (store-obj flow-id thread-id "thread_tool_tab_pane_id" thread-tools-tab-pane)

    thread-tools-tab-pane))

(defn create-or-focus-thread-tab [flow-id thread-id thread-name]
  (let [[threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")
        sel-model (.getSelectionModel threads-tabs-pane)
        thread-tab-pane (create-thread-pane flow-id thread-id)
        all-tabs (.getTabs threads-tabs-pane)
        tab-for-thread (some (fn [^Tab t]
                               (when (= (.getId t) (str thread-id))
                                 t))
                             all-tabs)]

    (if tab-for-thread
      (.select ^SingleSelectionModel sel-model ^Tab tab-for-thread)

      (let [thread-tab (tab {:text (or thread-name (str "thread-" thread-id))
                             :content thread-tab-pane
                             :id (str thread-id)})]
        (.setOnCloseRequest thread-tab
                            (event-handler
                             [ev]
                             (ui-vars/clean-objs flow-id thread-id)))
        (-> all-tabs
            (.addAll [thread-tab]))))))

(defn main-pane []
  (let [t-pane (tab-pane {:closing-policy :all-tabs
                          :side :top})]
    (store-obj "flows_tabs_pane" t-pane)
    t-pane))
