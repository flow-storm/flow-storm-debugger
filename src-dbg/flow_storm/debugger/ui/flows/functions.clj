(ns flow-storm.debugger.ui.flows.functions
  (:require [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [v-box h-box label list-view table-view icon-button button
                                                               check-box]]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.flows.code :as flows-code]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.debugger.ui.tasks :as tasks]
            [clojure.pprint :refer [cl-format]]
            [clojure.string :as str])
  (:import [javafx.scene.layout Priority HBox VBox]
           [javafx.geometry Orientation Insets Pos]
           [javafx.scene Node]
           [javafx.scene.control CheckBox SplitPane]
           [javafx.scene.input MouseButton]))

(def max-args 9)

(defn- update-function-calls [flow-id thread-id]
  (when-let [{:keys [form-id fn-ns fn-name]} (dbg-state/get-selected-function-list-fn flow-id thread-id)]
    (let [[{:keys [clear add-all]}] (obj-lookup flow-id thread-id "function_calls_list")
          _ (clear)
          [selected-args-fn] (obj-lookup flow-id thread-id "function_calls_selected_args_fn")
          sel-args (selected-args-fn)
          render-args (when (not= (count sel-args) max-args)
                        sel-args)]

      (tasks/submit-task runtime-api/collect-fn-frames-task
                         [flow-id thread-id fn-ns fn-name form-id render-args]
                         {:on-progress (fn [{:keys [batch]}] (add-all batch))}))))

(defn- functions-cell-factory [_ {:keys [cell-type] :as cell-info}]
  (case cell-type
    :calls (doto (h-box [(label (cl-format nil "~:d" (:cnt cell-info)))])
             (.setAlignment Pos/CENTER_RIGHT))

    :function (let [{:keys [form-def-kind fn-name fn-ns dispatch-val]} cell-info
                    fn-lbl (case form-def-kind
                             :defmethod       (flow-cmp/def-kind-colored-label (format "%s/%s %s" fn-ns fn-name (:val-str (runtime-api/val-pprint rt-api dispatch-val {:print-length 3 :print-level 3 :pprint? false}))) form-def-kind)
                             :extend-protocol (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                             :extend-type     (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                             :defrecord       (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                             :deftype          (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                             :defn            (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                             (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind))]
                fn-lbl)))

(defn- uninstrument-items [items]
  (let [groups (->> items
                    (group-by (fn [{:keys [form-def-kind]}]
                                (cond
                                  (#{:defn} form-def-kind) :vars
                                  (#{:defmethod :extend-protocol :extend-type} form-def-kind) :forms
                                  :else nil))))]

    (let [vars-symbs (->> (:vars groups)
                          (map (fn [{:keys [fn-name fn-ns]}]
                                 (symbol fn-ns fn-name))))]
      (doseq [vs vars-symbs]
        (runtime-api/uninstrument-var rt-api (namespace vs) (name vs) {})))

    (let [forms (->> (:forms groups)
                     (map (fn [{:keys [fn-ns form]}]
                            {:form-ns fn-ns
                             :form form})))]
      (when (seq forms)
        (doseq [{:keys [form-ns form]} forms]
          (runtime-api/eval-form rt-api form {:instrument? false
                                              :ns form-ns}))))))

(defn- function-click [mev selected-items {:keys [table-view-pane]}]
  ;; selected items contains rows like [{...fn-call...} cnt]
  (let [selected-items (map first selected-items)]
    (cond
      (and (= MouseButton/SECONDARY (.getButton mev))
           (not (dbg-state/clojure-storm-env?)))
      (let [ctx-menu-un-instrument-item {:text "Un-instrument seleced functions" :on-click (fn [] (uninstrument-items selected-items) )}
            ctx-menu (ui-utils/make-context-menu [ctx-menu-un-instrument-item])]
        (ui-utils/show-context-menu
         ctx-menu
         table-view-pane
         (.getScreenX mev)
         (.getScreenY mev))))))

(defn- create-fns-list-pane [flow-id thread-id]
  (let [{:keys [table-view-pane table-view] :as tv-data} (table-view
                                               {:columns ["Functions" "Calls"]
                                                :cell-factory-fn functions-cell-factory
                                                :resize-policy :constrained
                                                :on-click function-click
                                                :on-selection-change (fn [_ sel-item]
                                                                       (dbg-state/set-selected-function-list-fn flow-id thread-id (first sel-item))
                                                                       (update-function-calls flow-id thread-id))
                                                :selection-mode :multiple
                                                :search-predicate (fn [[{:keys [fn-name fn-ns]} _] search-str]
                                                                    (str/includes? (format "%s/%s" fn-ns fn-name) search-str))})]

    (store-obj flow-id thread-id "functions_table_data" tv-data)
    (VBox/setVgrow table-view Priority/ALWAYS)

    table-view-pane))

(defn- functions-calls-cell-factory [flow-id thread-id list-cell {:keys [args-vec ret throwable args-vec-str ret-str throwable-str]}]
  (let [create-inspector (fn [vref]
                           (value-inspector/create-inspector vref {:find-and-jump-same-val (partial flows-code/find-and-jump-same-val flow-id thread-id)}))
        args-node (when-not (str/blank? args-vec-str)
                    (doto (h-box [(button :label "args"
                                          :classes ["def-btn" "btn-sm"]
                                          :tooltip "Open this value in the value inspector."
                                          :on-click (fn [] (create-inspector args-vec)))
                                  (label args-vec-str)])
                      (.setSpacing 5)))
        ret-node (when ret-str
                   (doto (h-box [(button :label "ret"
                                    :classes ["def-btn" "btn-sm"]
                                    :tooltip "Open this value in the value inspector."
                                    :on-click (fn [] (create-inspector ret)))
                                 (label ret-str)])
                     (.setSpacing 5)))
        throwable-node (when throwable-str
                         (doto (h-box [(button :label "throw"
                                          :classes ["def-btn" "btn-sm"]
                                          :tooltip "Open this value in the value inspector."
                                          :on-click (fn [] (create-inspector throwable)))
                                       (label throwable-str "fail")])
                           (.setSpacing 5)))
        ret-kind-node (cond
                        ret-str       ret-node
                        throwable-str throwable-node)
        cell (doto (v-box (conj (if args-node [args-node] [])
                                ret-kind-node)
                          "contrast-background")
               (.setSpacing 5)
               (.setPadding (Insets. 5)))]
    (.setGraphic ^Node list-cell cell)))

(defn- function-call-click [flow-id thread-id mev selected-items {:keys [list-view-pane]}]
  (let [idx (-> selected-items first :fn-call-idx)
        jump-to-idx (fn []
                      (ui-flows-gral/select-thread-tool-tab flow-id thread-id :code)
                      (flows-code/jump-to-coord flow-id
                                                thread-id
                                                (runtime-api/timeline-entry rt-api flow-id thread-id idx :at)))]

    (cond
      (and (= MouseButton/PRIMARY (.getButton mev))
           (= 2 (.getClickCount mev)))

      (jump-to-idx)

      (= MouseButton/SECONDARY (.getButton mev))
      (let [ctx-menu (ui-utils/make-context-menu [{:text "Step code"
                                                   :on-click jump-to-idx}])]
        (ui-utils/show-context-menu ctx-menu
                                    list-view-pane
                                    (.getScreenX mev)
                                    (.getScreenY mev))))))

(defn- create-fn-calls-list-pane [flow-id thread-id]
  (let [args-checks (repeatedly max-args (fn [] (doto (check-box {:on-change (fn [_] (update-function-calls flow-id thread-id))})
                                                  (.setSelected true)
                                                  (.setFocusTraversable false))))
        selected-args (fn []
                        (->> args-checks
                             (keep-indexed (fn [idx ^CheckBox cb]
                                             (when (.isSelected cb) idx)))))
        {:keys [list-view-pane] :as lv-data} (list-view {:editable? false
                                                         :cell-factory-fn (partial functions-calls-cell-factory flow-id thread-id)
                                                         :on-click (partial function-call-click flow-id thread-id)
                                                         :selection-mode :single})
        args-print-type-checks (doto (->> args-checks
                                          (map-indexed (fn [idx cb]
                                                         (h-box [(label (format "a%d" (inc idx))) cb])))
                                          (into [(label "Print args:")])
                                          h-box)
                                 (.setSpacing 8))
        fn-call-list-pane (doto (v-box [args-print-type-checks
                                        list-view-pane])
                            (.setSpacing 5))]

    (VBox/setVgrow list-view-pane Priority/ALWAYS)

    (store-obj flow-id thread-id "function_calls_list" lv-data)
    (store-obj flow-id thread-id "function_calls_selected_args_fn" selected-args)

    fn-call-list-pane))


(defn update-functions-pane [flow-id thread-id]
  (let [fn-call-stats (->> (runtime-api/fn-call-stats rt-api flow-id thread-id)
                           (sort-by :cnt >)
                           (map (fn [fn-call]
                                  [(assoc fn-call :cell-type :function)
                                   (assoc fn-call :cell-type :calls)])))
        [{:keys [add-all clear]}] (obj-lookup flow-id thread-id "functions_table_data")]
    (clear)
    (add-all fn-call-stats)))

(defn create-functions-pane [flow-id thread-id]
  (let [refresh-btn (icon-button :icon-name "mdi-reload"
                                 :on-click (fn [] (update-functions-pane flow-id thread-id))
                                 :tooltip "Refresh the content of the functions list.")
        controls-box (doto (h-box [refresh-btn])
                       (.setPadding (Insets. 10.0)))
        fns-list-pane (create-fns-list-pane flow-id thread-id)
        fn-calls-list-pane (create-fn-calls-list-pane flow-id thread-id)
        split-pane (doto (SplitPane.)
                     (.setOrientation (Orientation/HORIZONTAL)))
        functions-pane (v-box [controls-box
                               split-pane])]

    (HBox/setHgrow fn-calls-list-pane Priority/ALWAYS)
    (VBox/setVgrow split-pane Priority/ALWAYS)

    (-> split-pane
        .getItems
        (.addAll [fns-list-pane fn-calls-list-pane]))

    (update-functions-pane flow-id thread-id)

    functions-pane))
