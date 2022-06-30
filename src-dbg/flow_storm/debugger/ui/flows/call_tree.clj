(ns flow-storm.debugger.ui.flows.call-tree
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.trace-indexer.protos :as indexer]
            [flow-storm.utils :refer [log]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.state :as state]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label]]
            [flow-storm.debugger.trace-types :refer [deref-ser]])
  (:import [javafx.collections ObservableList]
           [javafx.scene.control SelectionModel SplitPane TreeCell TextField TreeView TreeItem]
           [javafx.scene.input KeyCode]
           [ javafx.beans.value ChangeListener]
           [javafx.geometry Insets Pos Orientation]
           [javafx.scene.layout HBox Priority VBox]))

(defn update-call-stack-tree-pane [flow-id thread-id]
  (let [indexer (state/thread-trace-indexer flow-id thread-id)
        lazy-tree-item (fn lazy-tree-item [tree-node]
                         (let [calls (indexer/callstack-tree-childs indexer tree-node)]
                           (proxy [TreeItem] [tree-node]
                             (getChildren []
                               (let [^ObservableList super-childrens (proxy-super getChildren)]
                                 (if (.isEmpty super-childrens)
                                   (let [new-children (->> calls
                                                           (remove (fn [child-node]
                                                                     (let [{:keys [fn-name fn-ns]} (indexer/callstack-node-frame indexer child-node)]
                                                                       (state/callstack-tree-hidden? flow-id thread-id fn-name fn-ns))))
                                                           (map lazy-tree-item)
                                                           (into-array TreeItem))]
                                     (.setAll super-childrens new-children)
                                     super-childrens)
                                   super-childrens)))
                             (isLeaf [] (empty? calls)))))
        tree-root-node (indexer/callstack-tree-root indexer)
        root-item (lazy-tree-item tree-root-node)
        [tree-view] (obj-lookup flow-id (ui-vars/thread-callstack-tree-view-id thread-id))]

    (.setRoot ^TreeView tree-view root-item)))

(defn format-tree-fn-call-args [args-vec]
  (let [step-1 (flow-cmp/elide-string (deref-ser args-vec {:print-length 3 :print-level 3 :pprint? false}) 80)]
    (if (= \. (.charAt step-1 (dec (count step-1))))
      (subs step-1 1 (count step-1))
      (subs step-1 1 (dec (count step-1))))))

;; NOTE: Not using create-call-stack-tree-graphic-node now because there is an annoying bug when expanding scrolled trees

#_(defn- create-call-stack-tree-graphic-node [{:keys [frame-idx form-id fn-name fn-ns args]} flow-id thread-id]
  ;; Important !
  ;; this will be called for all visible tree nodes after any expansion
  ;; so it should be fast
    (if-not frame-idx

    (doto (ui-utils/icon-button "mdi-reload")
        (.setOnAction (event-handler
                       [_]
                       (update-call-stack-tree-pane flow-id thread-id))))

    (let [indexer (state/thread-trace-indexer flow-id thread-id)
          {:keys [multimethod/dispatch-val form/def-kind]} (indexer/get-form indexer form-id)
          ns-lbl (label (str fn-ns "/") "fn-ns")
          fn-name-lbl (flow-cmp/def-kind-colored-label fn-name def-kind)
          args-lbl (label (str " " (format-tree-fn-call-args args)) "fn-args")
          fn-call-box (if dispatch-val
                        (h-box [(label "(") ns-lbl fn-name-lbl (label (str dispatch-val)) args-lbl (label ")")])
                        (h-box [(label "(") ns-lbl fn-name-lbl args-lbl (label ")")]))
          ctx-menu-options [{:text (format "Goto trace %d" frame-idx)
                             :on-click #(do
                                          (ui-flows-gral/select-tool-tab flow-id thread-id :code)
                                          (flow-code/jump-to-coord flow-id thread-id frame-idx))}
                            {:text (format "Hide %s/%s from this tree" fn-ns fn-name)
                             :on-click #(do
                                          (state/callstack-tree-hide-fn flow-id thread-id fn-name fn-ns)
                                          (update-call-stack-tree-pane flow-id thread-id))}]
          ctx-menu (ui-utils/make-context-menu ctx-menu-options)]
      (doto fn-call-box
        (.setOnMouseClicked (event-handler
                             [^MouseEvent mev]
                             (when (= MouseButton/SECONDARY (.getButton mev))
                               (.show ctx-menu
                                      fn-call-box
                                      (.getScreenX mev)
                                      (.getScreenY mev)))))))))

(defn- create-call-stack-tree-text-node [{:keys [frame-idx form-id fn-name fn-ns args-vec]} flow-id thread-id]
  ;; Important !
  ;; this will be called for all visible tree nodes after any expansion
  ;; so it should be fast
  (if-not frame-idx

    "."

    (let [indexer (state/thread-trace-indexer flow-id thread-id)
          {:keys [multimethod/dispatch-val]} (indexer/get-form indexer form-id)]

      (if dispatch-val
        (format "(%s/%s %s %s)" fn-ns fn-name (deref-ser dispatch-val {:print-length 3 :print-level 3 :pprint? false}) (format-tree-fn-call-args args-vec))
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
  (let [indexer (state/thread-trace-indexer flow-id thread-id)
        search-txt (doto (TextField.)
                     (.setPromptText "Search"))
        search-from-txt (doto (TextField. "0")
                          (.setPrefWidth 70)
                          (.setAlignment Pos/CENTER))
        search-lvl-txt (doto (TextField. "2")
                         (.setPrefWidth 50)
                         (.setAlignment Pos/CENTER))
        search-match-lbl (label "")
        search-btn (ui-utils/icon-button "mdi-magnify" "tree-search")
        search (fn [] (log "Searching")
                 (.setDisable search-btn true)
                 (doto search-match-lbl
                   (.setOnMouseClicked (event-handler [ev]))
                   (.setStyle ""))

                 (state/callstack-tree-collapse-all-calls flow-id thread-id)
                 (indexer/search-next-frame-idx
                  indexer
                  (.getText search-txt)
                  (Integer/parseInt (.getText search-from-txt))
                  (Integer/parseInt (.getText search-lvl-txt))
                  (fn [next-match-path]
                    (if next-match-path
                      (let [[match-idx] next-match-path]
                        #_(log (format "Next match at %s" next-match-path))
                        (state/callstack-tree-select-path flow-id
                                                          thread-id
                                                          next-match-path)
                        (ui-utils/run-later
                         (update-call-stack-tree-pane flow-id thread-id)
                         (doto search-match-lbl
                           (.setText  (format "Match idx %d" match-idx))
                           (.setStyle "-fx-text-fill: blue; -fx-cursor: hand;")
                           (.setOnMouseClicked (event-handler
                                                [ev]
                                                (select-call-stack-tree-node flow-id thread-id match-idx))))
                         (.setText search-from-txt (str match-idx))))

                      (do
                        (ui-utils/run-later (.setText search-match-lbl ""))
                        (log "No match found")))
                    (ui-utils/run-later (.setDisable search-btn false)))
                  (fn [progress-perc]
                    (ui-utils/run-later
                     (.setText search-match-lbl (format "%.2f %%" (double progress-perc)))))))]
    (.setOnKeyReleased search-txt (event-handler
                                   [kev]
                                   (when (= (.getCode kev) KeyCode/ENTER)
                                     (search))))

    (.setOnAction search-btn (event-handler [_] (search)))
    (doto (h-box [search-match-lbl
                  search-txt
                  (label "From Idx: ")   search-from-txt
                  (label "*print-level* : ") search-lvl-txt
                  search-btn])
      (.setSpacing 3.0)
      (.setAlignment Pos/CENTER_RIGHT)
      (.setPadding (Insets. 4.0)))))

(defn create-call-stack-tree-pane [flow-id thread-id]
  (let [indexer (state/thread-trace-indexer flow-id thread-id)

        cell-factory (proxy [javafx.util.Callback] []
                       (call [tv]
                         (proxy [TreeCell] []
                           (updateItem [tree-node empty?]
                             (proxy-super updateItem tree-node empty?)
                             (if empty?

                               (doto this
                                 (.setGraphic nil)
                                 (.setText nil))

                               (let [frame (indexer/callstack-node-frame indexer tree-node)
                                     frame-idx (:frame-idx frame)
                                     expanded? (or (nil? frame-idx)
                                                   (state/callstack-tree-item-expanded? flow-id thread-id frame-idx))
                                     tree-item (.getTreeItem this)]

                                 (doto this
                                   #_(.setGraphic (create-call-stack-tree-graphic-node frame flow-id thread-id))
                                   (.setGraphic nil)
                                   (.setText (create-call-stack-tree-text-node frame flow-id thread-id))

                                   #_(.setTooltip (Tooltip. (format "Idx : %d" frame-idx))))

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
                                   (.setExpanded expanded?))))))))
        search-pane (create-tree-search-pane flow-id thread-id)
        tree-view (doto (TreeView.)
                    (.setEditable false)
                    (.setCellFactory cell-factory))
        tree-view-sel-model (.getSelectionModel tree-view)
        get-selected-frame (fn []
                             (let [sel-tree-node (.getValue (first (.getSelectedItems tree-view-sel-model)))]
                               (indexer/callstack-node-frame indexer sel-tree-node)))
        ctx-menu-options [{:text "Goto trace"
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
    (HBox/setHgrow labeled-args-pane Priority/ALWAYS)
    (HBox/setHgrow labeled-ret-pane Priority/ALWAYS)
    (.addListener (.selectedItemProperty tree-view-sel-model)
                  (proxy [ChangeListener] []
                    (changed [changed old-val new-val]
                      (when new-val
                        (let [{:keys [args-vec ret]} (indexer/callstack-node-frame indexer (.getValue new-val))]
                          (flow-cmp/update-pprint-pane flow-id thread-id "fn_args" args-vec)
                          (flow-cmp/update-pprint-pane flow-id thread-id "fn_ret" ret))))))

    (store-obj flow-id (ui-vars/thread-callstack-tree-view-id thread-id) tree-view)
    (VBox/setVgrow tree-view Priority/ALWAYS)
    (-> top-bottom-split
        .getItems
        (.addAll [(v-box [search-pane tree-view])
                  args-ret-pane]))

    top-bottom-split))
