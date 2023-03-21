(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler label icon tab-pane tab list-view]]
            [flow-storm.debugger.state :as dbg-state])
  (:import [javafx.scene.input MouseButton]
           [javafx.scene.control SplitPane]
           [javafx.geometry Orientation]))

(declare create-empty-thread)

(defn remove-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        [flow-tab] (obj-lookup flow-id "flow_tab")]

    (when flow-tab

      ;; remove the tab from flows_tabs_pane
      (-> flows-tabs-pane
          .getTabs
          (.remove flow-tab)))

    ;; clean ui state vars
    (ui-vars/clean-flow-objs flow-id)))

(defn fully-remove-flow [flow-id]
  ;; remove it from our state
  (dbg-state/remove-flow flow-id)

  ;; let runtime know we are not interested in this flow anymore
  (runtime-api/discard-flow rt-api flow-id)

  ;; remove it from the ui
  (remove-flow flow-id))

(defn create-thread [{:keys [flow-id thread-id]}]
  (dbg-state/create-thread flow-id thread-id)
  (dbg-state/set-idx flow-id thread-id 0)
  (create-empty-thread flow-id thread-id)
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

    (.setDividerPosition flow-split-pane 0 0.3)

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

(defn- create-thread-pane [flow-id thread-id]
  (let [code-tab (tab {:graphic (icon "mdi-code-parentheses")
                       :content (flow-code/create-code-pane flow-id thread-id)})

        callstack-tree-tab (tab {:graphic (icon "mdi-file-tree")
                                 :content (flow-tree/create-call-stack-tree-pane flow-id thread-id)
                                 :on-selection-changed (event-handler [_] (flow-tree/update-call-stack-tree-pane flow-id thread-id))})

        instrument-tab (tab {:graphic (icon "mdi-format-list-numbers")
                             :content (flow-fns/create-functions-pane flow-id thread-id)
                             :on-selection-changed (event-handler [_] (flow-fns/update-functions-pane flow-id thread-id))})
        thread-tools-tab-pane (tab-pane {:tabs [callstack-tree-tab code-tab instrument-tab]
                                         :side :bottom
                                         :closing-policy :unavailable})]

    (store-obj flow-id (ui-vars/thread-tool-tab-pane-id thread-id) thread-tools-tab-pane)

    thread-tools-tab-pane))

(defn create-empty-thread [flow-id thread-id]
  (let [[threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")
        thread-tab-pane (create-thread-pane flow-id thread-id)
        thread-tab (tab {:text (str "thread-" thread-id)
                         :content thread-tab-pane})]
    (-> threads-tabs-pane
        .getTabs
        (.addAll [thread-tab]))))

(defn main-pane []
  (let [t-pane (tab-pane {:closing-policy :all-tabs
                          :side :top})]
    (store-obj "flows_tabs_pane" t-pane)
    t-pane))
