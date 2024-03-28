(ns flow-storm.debugger.ui.flows.call-tree
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral :refer [show-message]]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.state :as state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui])
  (:import [javafx.collections ObservableList]
           [javafx.scene.control TreeCell TreeView TreeItem]
           [ javafx.beans.value ChangeListener]
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
                                                           (map lazy-tree-item))]

                                     (when (> (count calls) call-stack-tree-childs-limit)
                                       (show-message (format "Tree childs had been limited to %d to keep the UI responsive. You can still analyze all of them with the rest of the tools."
                                                             call-stack-tree-childs-limit)
                                                     :warning))

                                     (.setAll super-childrens ^objects (into-array Object new-children))
                                     super-childrens)
                                   super-childrens)))
                             (isLeaf [] (empty? calls)))))
        tree-root-node (runtime-api/callstack-tree-root-node rt-api flow-id thread-id)
        ^TreeItem root-item (lazy-tree-item tree-root-node)
        [tree-view] (obj-lookup flow-id thread-id "callstack_tree_view")]
    (.setExpanded root-item true)
    (.setRoot ^TreeView tree-view root-item)))

(defn format-tree-fn-call-args [args-vec]
  (let [v-str (-> (runtime-api/val-pprint rt-api args-vec {:print-length 3 :print-level 3 :pprint? false})
                  :val-str
                  (ui-utils/remove-newlines))
        ^String step-1 (utils/elide-string v-str 80)]
    (if (= \. (.charAt step-1 (dec (count step-1))))
      (subs step-1 1 (count step-1))
      (subs step-1 1 (dec (count step-1))))))

(defn- create-call-stack-tree-graphic-node [{:keys [form-id fn-name fn-ns args-vec] :as frame} item-level]
  ;; Important !
  ;; this will be called for all visible tree nodes after any expansion
  ;; so it should be fast
  (if (:root? frame)

    (ui/label :text ".")

    (let [{:keys [multimethod/dispatch-val form/form]} (runtime-api/get-form rt-api form-id)
          form-hint (if (= item-level 1)
                      (utils/elide-string (pr-str form) 80)
                      "")]

      (ui/h-box :childs [(ui/label :text (if dispatch-val
                                           (format "(%s/%s %s %s) " fn-ns fn-name (:val-str (runtime-api/val-pprint rt-api dispatch-val {:print-length 1 :print-level 1 :pprint? false})) (format-tree-fn-call-args args-vec))
                                           (format "(%s/%s %s)" fn-ns fn-name (format-tree-fn-call-args args-vec))))
                         (ui/label :text form-hint
                                   :class "light")]))))

(defn- select-call-stack-tree-node [flow-id thread-id match-idx]
  (let [[^TreeView tree-view] (obj-lookup flow-id thread-id "callstack_tree_view")
        [^TreeCell tree-cell] (obj-lookup flow-id thread-id (ui-utils/thread-callstack-tree-cell match-idx))]
    (when tree-cell
      (let [tree-cell-idx (.getIndex tree-cell)
            tree-item (.getTreeItem tree-cell)
            tree-selection-model (.getSelectionModel tree-view)]
        (.scrollTo tree-view tree-cell-idx)
        (ui-utils/selection-select-obj tree-selection-model tree-item)))))

(defn expand-path [^TreeItem tree-item select-idx path-set]
  (when-not (.isEmpty (.getChildren tree-item))
    (doseq [^TreeItem child-item (.getChildren tree-item)]
      (let [cnode (.getValue child-item)
            {:keys [fn-call-idx]} (runtime-api/callstack-node-frame rt-api cnode)]
        (when (path-set fn-call-idx)
          (.setExpanded tree-item true)
          (when-not (= select-idx fn-call-idx)
            (expand-path child-item select-idx path-set)))))))

(defn expand-and-highlight [flow-id thread-id [curr-idx :as fn-call-idx-path]]
  (ui-utils/run-later
    (let [[^TreeView tree-view] (obj-lookup flow-id thread-id "callstack_tree_view")
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

            (-> this
                (ui-utils/set-graphic nil)
                (ui-utils/set-text nil))

            (let [^TreeItem tree-item (.getTreeItem ^TreeCell this)
                  item-level (.getTreeItemLevel tree-view tree-item)
                  frame (runtime-api/callstack-node-frame rt-api tree-node)
                  fn-call-idx (:fn-call-idx frame)
                  update-tree-btn (ui/icon-button :icon-name "mdi-reload"
                                                  :classes ["reload-tree-btn"]
                                                  :on-click (fn []
                                                              (binding [runtime-api/*cache-disabled?* true]
                                                                (update-call-stack-tree-pane flow-id thread-id)))
                                                  :tooltip "Refresh the content of the tree. Useful since the tree will not autoupdate after it is open.")]

              (if (:root? frame)

                ;; it's the root dummy node, put update-tree-btn
                (-> this
                    (ui-utils/set-graphic update-tree-btn)
                    (ui-utils/set-text nil))

                ;; else, put the frame
                (-> this
                    (ui-utils/set-graphic (create-call-stack-tree-graphic-node frame item-level))
                    (ui-utils/set-text nil)))

              (store-obj flow-id thread-id (ui-utils/thread-callstack-tree-cell fn-call-idx) this))))))))

(defn highlight-current-frame [flow-id thread-id]
  (let [curr-idx (:fn-call-idx (state/current-timeline-entry flow-id thread-id))
        {:keys [fn-call-idx-path]} (runtime-api/frame-data rt-api flow-id thread-id curr-idx {:include-path? true})]
    (expand-and-highlight flow-id thread-id fn-call-idx-path)))

(defn create-call-stack-tree-pane [flow-id thread-id]
  (let [^TreeView tree-view (ui/tree-view)
        _ (.setCellFactory tree-view (build-tree-cell-factory flow-id thread-id tree-view))
        controls-box (ui/h-box :childs [(ui/icon-button :icon-name "mdi-adjust"
                                                        :on-click (fn [] (highlight-current-frame flow-id thread-id))
                                                        :tooltip "Highlight current frame")]
                               :spacing 3
                               :align :center-right)

        top-pane (ui/border-pane :left controls-box)
        tree-view-sel-model (.getSelectionModel tree-view)
        get-selected-frame (fn []
                             (let [sel-tree-node (.getValue ^TreeItem (first (.getSelectedItems tree-view-sel-model)))]
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
                                  (when (and (ui-utils/mouse-primary? mev)
                                             (ui-utils/double-click? mev))
                                    (jump-to-selected-frame-code)
                                    (ui-utils/consume mev)))))
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
        ctx-menu (ui/context-menu :items ctx-menu-options)
        callstack-fn-args-pane   (flow-cmp/create-pprint-pane flow-id thread-id "fn_args")
        callstack-fn-ret-pane (flow-cmp/create-pprint-pane flow-id thread-id "fn_ret")
        labeled-args-pane  (ui/v-box :childs [(ui/label :text "Args:") callstack-fn-args-pane])
        labeled-ret-pane (ui/v-box :childs [(ui/label :text "Ret:") callstack-fn-ret-pane])
        args-ret-pane (ui/h-box :childs [labeled-args-pane labeled-ret-pane]
                                :spacing 5)
        top-bottom-split (ui/split :orientation :vertical
                                   :childs [(ui/v-box :childs [top-pane tree-view])
                                            args-ret-pane]
                                   :sizes [0.75])]
    (.setContextMenu tree-view ctx-menu)

    (VBox/setVgrow callstack-fn-args-pane Priority/ALWAYS)
    (VBox/setVgrow callstack-fn-ret-pane Priority/ALWAYS)
    (HBox/setHgrow labeled-args-pane Priority/ALWAYS)
    (HBox/setHgrow labeled-ret-pane Priority/ALWAYS)

    (.addListener (.selectedItemProperty tree-view-sel-model)
                  (proxy [ChangeListener] []
                    (changed [changed old-val ^TreeItem new-val]
                      (when new-val
                        (let [{:keys [args-vec return/kind] :as frame} (runtime-api/callstack-node-frame rt-api (.getValue new-val))]
                          (flow-cmp/update-pprint-pane flow-id
                                                       thread-id
                                                       "fn_args"
                                                       {:val-ref args-vec}
                                                       {:find-and-jump-same-val (partial flow-code/find-and-jump-same-val flow-id thread-id)})
                          (flow-cmp/update-pprint-pane flow-id
                                                       thread-id
                                                       "fn_ret"
                                                       {:val-ref    (if (= :unwind kind) (:throwable frame) (:ret frame))
                                                        :extra-text (case kind
                                                                      :waiting "Return waiting"
                                                                      :unwind  "Throwed"
                                                                      nil)
                                                        :class (case kind
                                                                 :waiting :warning
                                                                 :unwind  :fail
                                                                 #_else   :normal)}
                                                       {:find-and-jump-same-val (partial flow-code/find-and-jump-same-val flow-id thread-id)}))))))

    (store-obj flow-id thread-id "callstack_tree_view" tree-view)
    (VBox/setVgrow tree-view Priority/ALWAYS)

    (update-call-stack-tree-pane flow-id thread-id)

    top-bottom-split))
