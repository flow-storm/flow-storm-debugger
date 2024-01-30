(ns flow-storm.debugger.ui.flows.call-tree
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral :refer [show-message]]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.state :as state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon-button border-pane]])
  (:import [javafx.collections ObservableList]
           [javafx.scene.control SelectionModel SplitPane TreeCell TreeView TreeItem]
           [javafx.scene.input MouseButton]
           [ javafx.beans.value ChangeListener]
           [javafx.geometry Pos Orientation]
           [javafx.scene.layout HBox Priority VBox]))

(def call-stack-tree-childs-limit 500)

(defn update-call-stack-tree-pane [flow-id thread-id]
  (let [lazy-tree-item (fn lazy-tree-item [tree-node]
                         (let [calls (runtime-api/callstack-node-childs rt-api tree-node)]
                           (proxy [TreeItem] [tree-node]
                             (getChildren []
                               (let [^ObservableList super-childrens (proxy-super getChildren)]
                                 (if (.isEmpty super-childrens)
                                   (let [new-children (->> calls
                                                           (take call-stack-tree-childs-limit)
                                                           (remove (fn [child-node]
                                                                     (let [{:keys [fn-name fn-ns]} (runtime-api/callstack-node-frame rt-api child-node)]
                                                                       (state/callstack-tree-hidden? flow-id thread-id fn-name fn-ns))))
                                                           (map lazy-tree-item)
                                                           (into-array TreeItem))]

                                     (when (> (count calls) call-stack-tree-childs-limit)
                                       (show-message (format "Tree childs had been limited to %d to keep the UI responsive. You can still analyze all of them with the rest of the tools."
                                                             call-stack-tree-childs-limit)
                                                     :warning))

                                     (.setAll super-childrens new-children)
                                     super-childrens)
                                   super-childrens)))
                             (isLeaf [] (empty? calls)))))
        tree-root-node (runtime-api/callstack-tree-root-node rt-api flow-id thread-id)
        root-item (lazy-tree-item tree-root-node)
        [tree-view] (obj-lookup flow-id thread-id "callstack_tree_view")]
    (.setExpanded root-item true)
    (.setRoot ^TreeView tree-view root-item)))

(defn format-tree-fn-call-args [args-vec]
  (let [v-str (-> (runtime-api/val-pprint rt-api args-vec {:print-length 3 :print-level 3 :pprint? false})
                  :val-str
                  (ui-utils/remove-newlines))
        step-1 (utils/elide-string v-str 80)]
    (if (= \. (.charAt step-1 (dec (count step-1))))
      (subs step-1 1 (count step-1))
      (subs step-1 1 (dec (count step-1))))))

(defn- create-call-stack-tree-graphic-node [{:keys [form-id fn-name fn-ns args-vec] :as frame} item-level]
  ;; Important !
  ;; this will be called for all visible tree nodes after any expansion
  ;; so it should be fast
  (if (:root? frame)

    (label ".")

    (let [{:keys [multimethod/dispatch-val form/form]} (runtime-api/get-form rt-api form-id)
          form-hint (if (= item-level 1)
                      (utils/elide-string (pr-str form) 80)
                      "")]

      (h-box [(label (if dispatch-val
                       (format "(%s/%s %s %s) " fn-ns fn-name (:val-str (runtime-api/val-pprint rt-api dispatch-val {:print-length 1 :print-level 1 :pprint? false})) (format-tree-fn-call-args args-vec))
                       (format "(%s/%s %s)" fn-ns fn-name (format-tree-fn-call-args args-vec))))
              (label form-hint "light")]))))

(defn- select-call-stack-tree-node [flow-id thread-id match-idx]
  (let [[tree-view] (obj-lookup flow-id thread-id "callstack_tree_view")
        [^TreeCell tree-cell] (obj-lookup flow-id thread-id (ui-utils/thread-callstack-tree-cell match-idx))]
    (when tree-cell
      (let [tree-cell-idx (.getIndex tree-cell)
            tree-item (.getTreeItem tree-cell)
            tree-selection-model (.getSelectionModel tree-view)]
        (.scrollTo tree-view tree-cell-idx)
        (.select ^SelectionModel tree-selection-model tree-item)))))

(defn expand-path [^TreeItem tree-item select-idx path-set]
  (when-not (.isEmpty (.getChildren tree-item))
    (doseq [child-item (.getChildren tree-item)]
      (let [cnode (.getValue child-item)
            {:keys [fn-call-idx]} (runtime-api/callstack-node-frame rt-api cnode)]
        (when (path-set fn-call-idx)
          (.setExpanded tree-item true)
          (when-not (= select-idx fn-call-idx)
            (expand-path child-item select-idx path-set)))))))

(defn expand-and-highlight [flow-id thread-id [curr-idx :as fn-call-idx-path]]
  (ui-utils/run-later
   (let [[tree-view] (obj-lookup flow-id thread-id "callstack_tree_view")
         root-item (.getRoot tree-view)]
     (expand-path root-item curr-idx (into #{} fn-call-idx-path))
     (select-call-stack-tree-node flow-id thread-id curr-idx))))

(defn- build-tree-cell-factory [flow-id thread-id ^TreeView tree-view]
  (proxy [javafx.util.Callback] []
    (call [tv]
      (proxy [TreeCell] []
        (updateItem [tree-node empty?]
          (proxy-super updateItem tree-node empty?)
          (if empty?

            (doto this
              (.setGraphic nil)
              (.setText nil))

            (let [^TreeItem tree-item (.getTreeItem this)
                  item-level (.getTreeItemLevel tree-view tree-item)
                  frame (runtime-api/callstack-node-frame rt-api tree-node)
                  fn-call-idx (:fn-call-idx frame)
                  update-tree-btn (icon-button :icon-name "mdi-reload"
                                               :classes ["reload-tree-btn"]
                                               :on-click (fn []
                                                           (binding [runtime-api/*cache-disabled?* true]
                                                             (update-call-stack-tree-pane flow-id thread-id)))
                                               :tooltip "Refresh the content of the tree. Useful since the tree will not autoupdate after it is open.")]

              (if (:root? frame)

                ;; it's the root dummy node, put update-tree-btn
                (doto this
                  (.setGraphic update-tree-btn)
                  (.setText nil))

                ;; else, put the frame
                (doto this
                  (.setGraphic (create-call-stack-tree-graphic-node frame item-level))
                  (.setText nil)))

              (store-obj flow-id thread-id (ui-utils/thread-callstack-tree-cell fn-call-idx) this))))))))

(defn highlight-current-frame [flow-id thread-id]
  (let [curr-idx (:fn-call-idx (state/current-timeline-entry flow-id thread-id))
        {:keys [fn-call-idx-path]} (runtime-api/frame-data rt-api flow-id thread-id curr-idx {:include-path? true})]
    (expand-and-highlight flow-id thread-id fn-call-idx-path)))

(defn create-call-stack-tree-pane [flow-id thread-id]
  (let [tree-view (doto (TreeView.)
                    (.setEditable false))
        tree-cell-factory (build-tree-cell-factory flow-id thread-id tree-view)
        controls-box (doto (h-box [(icon-button :icon-name "mdi-adjust"
                                                :on-click (fn [] (highlight-current-frame flow-id thread-id))
                                                :tooltip "Highlight current frame")])
                       (.setAlignment Pos/CENTER_RIGHT)
                       (.setSpacing 3.0))
        top-pane (border-pane {:left controls-box})
        _ (doto tree-view
            (.setCellFactory tree-cell-factory))
        tree-view-sel-model (.getSelectionModel tree-view)
        get-selected-frame (fn []
                             (let [sel-tree-node (.getValue (first (.getSelectedItems tree-view-sel-model)))]
                               (runtime-api/callstack-node-frame rt-api sel-tree-node)))
        jump-to-selected-frame-code (fn [& _]
                                      (let [{:keys [fn-call-idx]} (get-selected-frame)]
                                        (ui-flows-gral/select-thread-tool-tab flow-id thread-id :code)
                                        (flow-code/jump-to-coord flow-id
                                                                 thread-id
                                                                 (runtime-api/timeline-entry rt-api flow-id thread-id fn-call-idx :at))))
        copy-selected-frame-to-clipboard (fn [args?]
                                           (let [{:keys [fn-name fn-ns args-vec]} (get-selected-frame)]
                                             (ui-utils/copy-selected-frame-to-clipboard fn-ns fn-name (when args? args-vec))))
        _ (doto tree-view
            (.setOnMouseClicked (event-handler
                                 [mev]
                                 (when (and (= MouseButton/PRIMARY (.getButton mev))
                                            (= 2 (.getClickCount mev)))
                                   (jump-to-selected-frame-code)
                                   (.consume mev)))))
        ctx-menu-options [{:text "Step code"
                           :on-click jump-to-selected-frame-code}
                          {:text "Copy qualified function symbol"
                           :on-click (fn [] (copy-selected-frame-to-clipboard false))}
                          {:text "Copy function calling form"
                           :on-click (fn [] (copy-selected-frame-to-clipboard true))}
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
                        (let [{:keys [args-vec ret throwable return/kind]} (runtime-api/callstack-node-frame rt-api (.getValue new-val))]
                          (flow-cmp/update-pprint-pane flow-id
                                                       thread-id
                                                       "fn_args"
                                                       args-vec
                                                       {:find-and-jump-same-val (partial flow-code/find-and-jump-same-val flow-id thread-id)})
                          (flow-cmp/update-return-pprint-pane flow-id
                                                              thread-id
                                                              "fn_ret"
                                                              kind
                                                              (case kind
                                                                :return ret
                                                                :unwind throwable)
                                                              {:find-and-jump-same-val (partial flow-code/find-and-jump-same-val flow-id thread-id)}))))))

    (store-obj flow-id thread-id "callstack_tree_view" tree-view)
    (VBox/setVgrow tree-view Priority/ALWAYS)
    (-> top-bottom-split
        .getItems
        (.addAll [(v-box [top-pane tree-view])
                  args-ret-pane]))

    (update-call-stack-tree-pane flow-id thread-id)

    top-bottom-split))
