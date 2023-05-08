(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler label icon tab-pane tab list-view icon-button h-box v-box]]
            [flow-storm.debugger.state :as dbg-state])
  (:import [javafx.scene.input MouseButton]
           [javafx.scene.control SingleSelectionModel SplitPane Tab]
           [javafx.geometry Orientation]
           [ javafx.beans.value ChangeListener]))

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

(defn update-threads-list [flow-id]
  (let [[{:keys [add-all clear] :as lv-data}] (obj-lookup flow-id "flow_threads_list")]
    (when lv-data
      (clear)
      (add-all (runtime-api/flow-threads-info rt-api flow-id)))))

(defn- setup-thread-keybindngs [flow-id thread-id pane]
  (.setOnKeyPressed
   pane
   (event-handler
    [kev]
    (let [key-txt (.getText kev)]
      (cond
        (= key-txt "t") (ui-general/select-thread-tool-tab flow-id thread-id :call-tree)
        (= key-txt "c") (ui-general/select-thread-tool-tab flow-id thread-id :code)
        (= key-txt "f") (ui-general/select-thread-tool-tab flow-id thread-id :functions)
        (= key-txt "p") (flow-code/step-prev flow-id thread-id)
        (= key-txt "n") (flow-code/step-next flow-id thread-id)
        (= key-txt "^") (flow-code/step-out flow-id thread-id)
        (= key-txt "<") (flow-code/step-first flow-id thread-id)
        (= key-txt ">") (flow-code/step-last flow-id thread-id))))))

(defn open-thread [thread-info]
  (let [flow-id (:flow/id thread-info)
        thread-id (:thread/id thread-info)
        thread-name (:thread/name thread-info)]

    (when-not (dbg-state/get-thread flow-id thread-id)
      (dbg-state/create-thread flow-id thread-id)
      (dbg-state/set-idx flow-id thread-id 0))

    (create-or-focus-thread-tab flow-id thread-id thread-name)
    (flow-code/jump-to-coord flow-id thread-id 0)
    (ui-general/select-thread-tool-tab flow-id (:thread/id thread-info) :call-tree)))

(defn create-empty-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        threads-tab-pane (tab-pane {:closing-policy :all-tabs
                                    :drag-policy :reorder})
        flow-split-pane (doto (SplitPane.)
                          (.setOrientation (Orientation/HORIZONTAL)))
        flow-tab (if (nil? flow-id)
                   (tab {:id (str "flow-tab-" flow-id) :graphic (icon "mdi-filter") :content flow-split-pane})
                   (tab {:id (str "flow-tab-" flow-id) :text (str "flow-" flow-id) :content flow-split-pane}))

        {:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :selection-mode :single
                    :cell-factory-fn (fn [list-cell {:keys [thread/name thread/blocked thread/id]}]
                                       (.setText list-cell nil)
                                       (.setGraphic
                                        list-cell
                                        (if blocked
                                          ;; build blocked thread node
                                          (let [[bp-var-ns bp-var-name] blocked
                                                thread-unblock-btn (icon-button :icon-name "mdi-play"
                                                                                :on-click (fn []
                                                                                            (runtime-api/unblock-thread rt-api id))
                                                                                :class "thread-continue-btn")
                                                ctx-menu-unblock-all-threads {:text "Unblock all threads"
                                                                              :on-click (fn []
                                                                                          (runtime-api/unblock-all-threads rt-api))}
                                                ctx-menu (ui-utils/make-context-menu [ctx-menu-unblock-all-threads])
                                                blocked-thread (h-box [(v-box [(label name "thread-blocked")
                                                                               (label (format "%s/%s" bp-var-ns bp-var-name) "light")])
                                                                       thread-unblock-btn])]
                                            (doto blocked-thread
                                              (.setSpacing 5)
                                              (.setOnContextMenuRequested
                                               (event-handler
                                                [mev]
                                                (.show ctx-menu
                                                       blocked-thread
                                                       (.getScreenX mev)
                                                       (.getScreenY mev))))))

                                          ;; if not blocked just render a label
                                          (label name))))
                    :on-click (fn [mev sel-items _]
                                (when (and (= MouseButton/PRIMARY (.getButton mev))
                                           (= 2 (.getClickCount mev)))
                                  (open-thread (first sel-items))))
                    :on-enter (fn [sel-items]
                                (open-thread (first sel-items)))})]

    ;; this is to keep the divider positions if the user
    ;; resize the window
    (.addListener (.widthProperty flows-tabs-pane)
                  (proxy [ChangeListener] []
                    (changed [_ _ _]
                      (.setDividerPosition flow-split-pane 0 0.2))))

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

(defn- create-thread-pane [flow-id thread-id]
  (let [code-tab (tab {:graphic (icon "mdi-code-parentheses")
                       :content (flow-code/create-code-pane flow-id thread-id)
                       :tooltip "Coode tool. Allows you to step over the traced code."})

        callstack-tree-tab (tab {:graphic (icon "mdi-file-tree")
                                 :content (flow-tree/create-call-stack-tree-pane flow-id thread-id)
                                 :tooltip "Call tree tool. Allows you to explore the recorded execution tree."})

        instrument-tab (tab {:graphic (icon "mdi-format-list-numbers")
                             :content (flow-fns/create-functions-pane flow-id thread-id)
                             :tooltip "Functions list tool. Gives you a list of all function calls and how many time they have been called."})
        thread-tools-tab-pane (tab-pane {:tabs [callstack-tree-tab code-tab instrument-tab]
                                         :side :bottom
                                         :closing-policy :unavailable})]

    (store-obj flow-id thread-id "thread_tool_tab_pane_id" thread-tools-tab-pane)

    thread-tools-tab-pane))

(defn create-or-focus-thread-tab [flow-id thread-id thread-name]
  (let [[threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")
        sel-model (.getSelectionModel threads-tabs-pane)
        all-tabs (.getTabs threads-tabs-pane)
        tab-for-thread (some (fn [^Tab t]
                               (when (= (.getId t) (str thread-id))
                                 t))
                             all-tabs)]

    (if tab-for-thread

      (.select ^SingleSelectionModel sel-model ^Tab tab-for-thread)


      (let [thread-tab-pane (create-thread-pane flow-id thread-id)
            thread-tab (tab {:text (or thread-name (str "thread-" thread-id))
                             :content thread-tab-pane
                             :id (str thread-id)})]

        (setup-thread-keybindngs flow-id thread-id thread-tab-pane)

        (.setOnCloseRequest thread-tab
                            (event-handler
                             [ev]
                             (ui-vars/clean-objs flow-id thread-id)))
        (-> all-tabs
            (.addAll [thread-tab]))))))

(defn select-flow-tab [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        sel-model (.getSelectionModel flows-tabs-pane)
        all-tabs (.getTabs flows-tabs-pane)
        tab-for-flow (some (fn [^Tab t]
                             (when (= (.getId t) (str "flow-tab-" flow-id))
                               t))
                           all-tabs)]

    ;; select the flow tab
    (when tab-for-flow
      (.select ^SingleSelectionModel sel-model ^Tab tab-for-flow)

      ;; focus the threads list
      (let [[{:keys [list-view]}] (obj-lookup flow-id "flow_threads_list")]
        (when list-view
          (let [list-selection (.getSelectionModel list-view)]
            (.requestFocus list-view)
            (.selectFirst list-selection)))))))

(defn main-pane []
  (let [t-pane (tab-pane {:closing-policy :all-tabs
                          :side :top})]
    (store-obj "flows_tabs_pane" t-pane)
    t-pane))
