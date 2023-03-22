(ns flow-storm.debugger.ui.flows.call-tree
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.utils :as utils :refer [log]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.state :as state]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon-button]])
  (:import [javafx.collections ObservableList]
           [javafx.scene.control SelectionModel SplitPane TreeCell TextField TreeView TreeItem]
           [javafx.scene.input KeyCode]
           [ javafx.beans.value ChangeListener]
           [javafx.geometry Insets Pos Orientation]
           [javafx.scene.layout HBox Priority VBox]))

(defn update-call-stack-tree-pane [flow-id thread-id]
  (let [lazy-tree-item (fn lazy-tree-item [tree-node]
                         (let [calls (runtime-api/callstack-node-childs rt-api tree-node)]
                           (proxy [TreeItem] [tree-node]
                             (getChildren []
                               (let [^ObservableList super-childrens (proxy-super getChildren)]
                                 (if (.isEmpty super-childrens)
                                   (let [new-children (->> calls
                                                           (remove (fn [child-node]
                                                                     (let [{:keys [fn-name fn-ns]} (runtime-api/callstack-node-frame rt-api child-node)]
                                                                       (state/callstack-tree-hidden? flow-id thread-id fn-name fn-ns))))
                                                           (map lazy-tree-item)
                                                           (into-array TreeItem))]
                                     (.setAll super-childrens new-children)
                                     super-childrens)
                                   super-childrens)))
                             (isLeaf [] (empty? calls)))))
        tree-root-node (runtime-api/callstack-tree-root-node rt-api flow-id thread-id)
        root-item (lazy-tree-item tree-root-node)
        [tree-view] (obj-lookup flow-id (ui-vars/thread-callstack-tree-view-id thread-id))]

    (.setRoot ^TreeView tree-view root-item)))

(defn format-tree-fn-call-args [args-vec]
  (let [v-str (-> (runtime-api/val-pprint rt-api args-vec {:print-length 3 :print-level 3 :pprint? false})
                  (ui-utils/remove-newlines))
        step-1 (utils/elide-string v-str 80)]
    (if (= \. (.charAt step-1 (dec (count step-1))))
      (subs step-1 1 (count step-1))
      (subs step-1 1 (dec (count step-1))))))

(defn- create-call-stack-tree-text-node [{:keys [form-id fn-name fn-ns args-vec] :as frame} flow-id thread-id]
  ;; Important !
  ;; this will be called for all visible tree nodes after any expansion
  ;; so it should be fast
  (if (:root? frame)

    "."

    (let [{:keys [multimethod/dispatch-val]} (runtime-api/get-form rt-api flow-id thread-id form-id)]

      (if dispatch-val
        (format "(%s/%s %s %s)" fn-ns fn-name (runtime-api/val-pprint rt-api dispatch-val {:print-length 3 :print-level 3 :pprint? false}) (format-tree-fn-call-args args-vec))
        (format "(%s/%s %s)" fn-ns fn-name (format-tree-fn-call-args args-vec))))))


(defn- select-call-stack-tree-node [flow-id thread-id match-idx]
  (let [[tree-view] (obj-lookup flow-id (ui-vars/thread-callstack-tree-view-id thread-id))
        [^TreeCell tree-cell] (obj-lookup flow-id (ui-vars/thread-callstack-tree-cell thread-id match-idx))]
    (when tree-cell
      (let [tree-cell-idx (.getIndex tree-cell)
            tree-item (.getTreeItem tree-cell)
            tree-selection-model (.getSelectionModel tree-view)]
        (.scrollTo tree-view tree-cell-idx)
        (.select ^SelectionModel tree-selection-model tree-item)))))

(defn- create-tree-search-pane [flow-id thread-id]
  (let [search-txt (doto (TextField.)
                     (.setPromptText "Search"))
        search-from-txt (doto (TextField. "0")
                          (.setPrefWidth 70)
                          (.setAlignment Pos/CENTER))
        search-lvl-txt (doto (TextField. "2")
                         (.setPrefWidth 50)
                         (.setAlignment Pos/CENTER))
        search-match-lbl (label "" "link-lbl")
        search-btn (ui-utils/icon-button :icon-name "mdi-magnify"
                                         :class "tree-search")
        search (fn [] (log "Searching")
                 (.setDisable search-btn true)
                 (doto search-match-lbl
                   (.setOnMouseClicked (event-handler [ev]))
                   (.setStyle ""))

                 (state/callstack-tree-collapse-all-calls flow-id thread-id)

                 (let [task-id (runtime-api/search-next-frame-idx
                                rt-api
                                flow-id
                                thread-id
                                (.getText search-txt)
                                (Integer/parseInt (.getText search-from-txt))
                                {:print-level (Integer/parseInt (.getText search-lvl-txt))})]

                   (ui-vars/subscribe-to-task-event :progress
                                                    task-id
                                                    (fn [progress-perc]
                                                      (ui-utils/run-later
                                                       (.setText search-match-lbl (format "%.2f %%" (double progress-perc))))))

                   (ui-vars/subscribe-to-task-event :result
                                                    task-id
                                                    (fn [{:keys [frame-data match-stack]}]

                                                      (if frame-data
                                                        (let [[match-idx] match-stack]
                                                          (state/callstack-tree-select-path flow-id
                                                                                            thread-id
                                                                                            match-stack)
                                                          (ui-utils/run-later
                                                           (update-call-stack-tree-pane flow-id thread-id)
                                                           (doto search-match-lbl
                                                             (.setText  (format "Match idx %d" match-idx))
                                                             (.setOnMouseClicked (event-handler
                                                                                  [ev]
                                                                                  (select-call-stack-tree-node flow-id thread-id match-idx))))
                                                           (.setText search-from-txt (str match-idx))))

                                                        (do
                                                          (ui-utils/run-later (.setText search-match-lbl ""))
                                                          (log "No match found")))

                                                      (ui-utils/run-later (.setDisable search-btn false))))))]

    (.setOnAction search-btn (event-handler [_] (search)))

    (.setOnKeyReleased search-txt (event-handler
                                   [kev]
                                   (when (= (.getCode kev) KeyCode/ENTER)
                                     (search))))

    (doto (h-box [search-match-lbl
                  search-txt
                  (label "From Idx: ")   search-from-txt
                  (label "*print-level* : ") search-lvl-txt
                  search-btn])
      (.setSpacing 3.0)
      (.setAlignment Pos/CENTER_RIGHT)
      (.setPadding (Insets. 4.0)))))

(defn- build-tree-cell-factory [flow-id thread-id]
  (proxy [javafx.util.Callback] []
    (call [tv]
      (proxy [TreeCell] []
        (updateItem [tree-node empty?]
          (proxy-super updateItem tree-node empty?)
          (if empty?

            (doto this
              (.setGraphic nil)
              (.setText nil))

            (let [frame (runtime-api/callstack-node-frame rt-api tree-node)
                  frame-idx (:frame-idx frame)
                  expanded? (or (nil? frame-idx)
                                (state/callstack-tree-item-expanded? flow-id thread-id frame-idx))
                  tree-item (.getTreeItem this)
                  update-tree-btn (icon-button :icon-name "mdi-reload"
                                               :class "reload-tree-btn"
                                               :on-click (fn []
                                                           (binding [runtime-api/*cache-disabled?* true]
                                                             (update-call-stack-tree-pane flow-id thread-id))))]

              (if (:root? frame)

                ;; it's the root dummy node, put update-tree-btn
                (doto this
                  (.setGraphic update-tree-btn)
                  (.setText nil))

                ;; else, put the frame
                (doto this
                  (.setGraphic nil)
                  (.setText (create-call-stack-tree-text-node frame flow-id thread-id))))

              (store-obj flow-id (ui-vars/thread-callstack-tree-cell thread-id frame-idx) this)

              (doto tree-item
                (.addEventHandler (TreeItem/branchCollapsedEvent)
                                  (event-handler
                                   [ev]
                                   (when (= (.getTreeItem ev) tree-item)
                                     (state/callstack-tree-collapse-calls flow-id thread-id #{frame-idx}))))
                (.addEventHandler (TreeItem/branchExpandedEvent)
                                  (event-handler
                                   [ev]
                                   (when (= (.getTreeItem ev) tree-item)
                                     (state/callstack-tree-expand-calls flow-id thread-id #{frame-idx}))))
                (.setExpanded expanded?)))))))))

(defn create-call-stack-tree-pane [flow-id thread-id]
  (let [tree-cell-factory (build-tree-cell-factory flow-id thread-id)
        search-pane (create-tree-search-pane flow-id thread-id)
        tree-view (doto (TreeView.)
                    (.setEditable false)
                    (.setCellFactory tree-cell-factory))
        tree-view-sel-model (.getSelectionModel tree-view)
        get-selected-frame (fn []
                             (let [sel-tree-node (.getValue (first (.getSelectedItems tree-view-sel-model)))]
                               (runtime-api/callstack-node-frame rt-api sel-tree-node)))
        ctx-menu-options [{:text "Step code"
                           :on-click (fn [& _]
                                       (let [{:keys [frame-idx]} (get-selected-frame)]
                                         (ui-flows-gral/select-tool-tab flow-id thread-id :code)
                                         (flow-code/jump-to-coord flow-id thread-id frame-idx)))}
                          {:text "Hide from tree"
                           :on-click (fn [& _]
                                       (let [{:keys [fn-name fn-ns]} (get-selected-frame)]
                                         (state/callstack-tree-hide-fn flow-id thread-id fn-name fn-ns)
                                         (update-call-stack-tree-pane flow-id thread-id)))}]
        ctx-menu (ui-utils/make-context-menu ctx-menu-options)
        callstack-fn-args-pane   (flow-cmp/create-pprint-pane flow-id thread-id "fn_args")
        callstack-fn-ret-pane (flow-cmp/create-pprint-pane flow-id thread-id "fn_ret")
        labeled-args-pane  (v-box [(label "Args:") callstack-fn-args-pane])
        labeled-ret-pane (v-box [(label "Ret:") callstack-fn-ret-pane])
        args-ret-pane (doto (h-box [labeled-args-pane labeled-ret-pane])
                        (.setSpacing 5.0))
        top-bottom-split (doto (SplitPane.)
                           (.setOrientation (Orientation/VERTICAL))
                           (.setDividerPosition 0 0.75))]
    (.setContextMenu tree-view ctx-menu)

    (VBox/setVgrow callstack-fn-args-pane Priority/ALWAYS)
    (VBox/setVgrow callstack-fn-ret-pane Priority/ALWAYS)
    (HBox/setHgrow labeled-args-pane Priority/ALWAYS)
    (HBox/setHgrow labeled-ret-pane Priority/ALWAYS)

    (.addListener (.selectedItemProperty tree-view-sel-model)
                  (proxy [ChangeListener] []
                    (changed [changed old-val new-val]
                      (when new-val
                        (let [{:keys [args-vec ret]} (runtime-api/callstack-node-frame rt-api (.getValue new-val))]
                          (flow-cmp/update-pprint-pane flow-id thread-id "fn_args" args-vec)
                          (flow-cmp/update-pprint-pane flow-id thread-id "fn_ret" ret))))))

    (store-obj flow-id (ui-vars/thread-callstack-tree-view-id thread-id) tree-view)
    (VBox/setVgrow tree-view Priority/ALWAYS)
    (-> top-bottom-split
        .getItems
        (.addAll [(v-box [search-pane tree-view])
                  args-ret-pane]))

    top-bottom-split))
