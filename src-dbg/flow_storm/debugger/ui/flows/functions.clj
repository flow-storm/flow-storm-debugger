(ns flow-storm.debugger.ui.flows.functions
  (:require [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.flows.code :as flows-code]
            [flow-storm.debugger.ui.tasks :as tasks]
            [clojure.pprint :refer [cl-format]]
            [clojure.string :as str]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows])
  (:import [javafx.scene.layout Priority HBox VBox]))


(def max-args 9)

(defn- update-function-calls [flow-id thread-id]
  (when-let [{:keys [form-id fn-ns fn-name]} (dbg-state/get-selected-function-list-fn flow-id thread-id)]
    (let [[{:keys [clear add-all]}] (obj-lookup flow-id thread-id "function_calls_list")
          _ (clear)
          [selected-args-fn] (obj-lookup flow-id thread-id "function_calls_selected_args_and_ret_fn")
          {:keys [sel-args ret?]} (selected-args-fn)
          render-args (when (not= (count sel-args) max-args)
                        sel-args)]

      (tasks/submit-task runtime-api/collect-fn-frames-task
                         [flow-id thread-id fn-ns fn-name form-id render-args ret?]
                         {:on-progress (fn [{:keys [batch]}] (add-all batch))}))))

(defn- functions-cell-factory [_ {:keys [cell-type] :as cell-info}]
  (case cell-type
    :calls (ui/h-box :childs [(ui/label :text (cl-format nil "~:d" (:cnt cell-info)))]
                     :align :center-right)

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
        (runtime-api/vanilla-uninstrument-var rt-api (namespace vs) (name vs) {})))

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
      (and (ui-utils/mouse-secondary? mev)
           (not (dbg-state/clojure-storm-env?)))
      (let [ctx-menu-un-instrument-item {:text "Un-instrument seleced functions" :on-click (fn [] (uninstrument-items selected-items) )}
            ctx-menu (ui/context-menu
                      :items [ctx-menu-un-instrument-item])]
        (ui-utils/show-context-menu
         :menu ctx-menu
         :parent table-view-pane
         :mouse-ev mev)))))

(defn- create-fns-list-pane [flow-id thread-id]
  (let [{:keys [table-view-pane table-view] :as tv-data}
        (ui/table-view
         :columns ["Functions" "Calls"]
         :cell-factory functions-cell-factory
         :resize-policy :constrained
         :on-click function-click
         :on-selection-change (fn [_ sel-item]
                                (dbg-state/set-selected-function-list-fn flow-id thread-id (first sel-item))
                                (update-function-calls flow-id thread-id))
         :selection-mode :multiple
         :search-predicate (fn [[{:keys [fn-name fn-ns]} _] search-str]
                             (str/includes? (format "%s/%s" fn-ns fn-name) search-str)))]

    (store-obj flow-id thread-id "functions_table_data" tv-data)
    (VBox/setVgrow table-view Priority/ALWAYS)

    table-view-pane))

(defn- functions-calls-cell-factory [list-cell {:keys [args-vec ret throwable args-vec-str ret-str throwable-str]}]
  (let [args-node (when-not (str/blank? args-vec-str)
                    (ui/h-box :childs [(ui/button :label "args"
                                                  :classes ["def-btn" "btn-sm"]
                                                  :tooltip "Open this value in the value inspector."
                                                  :on-click (fn [] (data-windows/create-data-window-for-vref args-vec)))
                                       (ui/label :text args-vec-str)]
                              :spacing 5))
        ret-node (when ret-str
                   (ui/h-box :childs [(ui/button :label "ret"
                                                 :classes ["def-btn" "btn-sm"]
                                                 :tooltip "Open this value in the value inspector."
                                                 :on-click (fn [] (data-windows/create-data-window-for-vref ret)))
                                      (ui/label :text ret-str)]
                             :spacing 5))
        throwable-node (when throwable-str
                         (ui/h-box :childs [(ui/button :label "throw"
                                                       :classes ["def-btn" "btn-sm"]
                                                       :tooltip "Open this value in the value inspector."
                                                       :on-click (fn [] (data-windows/create-data-window-for-vref throwable)))
                                            (ui/label :text throwable-str
                                                      :class "fail")]
                                   :spacing 5))
        ret-kind-node (cond
                        ret-str       ret-node
                        throwable-str throwable-node)
        cell (ui/v-box :childs (cond-> []
                                 args-node     (conj args-node)
                                 ret-kind-node (conj ret-kind-node))
                       :class "contrast-background"
                       :spacing 5
                       :paddings [5])]
    (ui-utils/set-graphic list-cell cell)))

(defn- function-call-click [flow-id thread-id mev selected-items {:keys [list-view-pane]}]
  (let [idx (-> selected-items first :fn-call-idx)
        jump-to-idx (fn []
                      (ui-flows-gral/select-thread-tool-tab flow-id thread-id "flows-code-stepper")
                      (flows-code/jump-to-coord flow-id
                                                thread-id
                                                (runtime-api/timeline-entry rt-api flow-id thread-id idx :at)))]

    (cond
      (and (ui-utils/mouse-primary? mev)
           (ui-utils/double-click? mev))

      (jump-to-idx)

      (ui-utils/mouse-secondary? mev)
      (let [ctx-menu (ui/context-menu
                      :items [{:text "Step code"
                               :on-click jump-to-idx}])]
        (ui-utils/show-context-menu :menu ctx-menu
                                    :parent list-view-pane
                                    :mouse-ev mev)))))

(defn- create-fn-calls-list-pane [flow-id thread-id]
  (let [args-checks (repeatedly max-args (fn []
                                           (ui/check-box :on-change (fn [_] (update-function-calls flow-id thread-id))
                                                              :selected? true
                                                              :focus-traversable? false)))

        {:keys [list-view-pane] :as lv-data} (ui/list-view :editable? false
                                                           :cell-factory functions-calls-cell-factory
                                                           :on-click (partial function-call-click flow-id thread-id)
                                                           :selection-mode :single)
        args-print-type-checks (ui/h-box
                                :childs (->> args-checks
                                             (map-indexed (fn [idx cb]
                                                            (ui/h-box :childs [(ui/label :text (format "a%d" (inc idx))) cb])))
                                             (into [(ui/label :text "Print args:")]))
                                :spacing 8)

        ret-check (ui/check-box :on-change (fn [_] (update-function-calls flow-id thread-id))
                                :selected? true
                                :focus-traversable? false)

        fn-call-list-pane (ui/v-box :childs [args-print-type-checks
                                             (ui/h-box :childs [(ui/label :text "Print ret?") ret-check])
                                             list-view-pane]
                                    :spacing 5)

        selected-args-and-ret (fn []
                                {:sel-args (->> args-checks
                                                (keep-indexed (fn [idx cb]
                                                                (when (ui-utils/checkbox-checked? cb) idx))))
                                 :ret? (ui-utils/checkbox-checked? ret-check)})]

    (VBox/setVgrow list-view-pane Priority/ALWAYS)

    (store-obj flow-id thread-id "function_calls_list" lv-data)
    (store-obj flow-id thread-id "function_calls_selected_args_and_ret_fn" selected-args-and-ret)

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
  (let [fns-list-pane (create-fns-list-pane flow-id thread-id)
        fn-calls-list-pane (create-fn-calls-list-pane flow-id thread-id)
        split-pane (ui/split :orientation :horizontal
                             :childs [fns-list-pane fn-calls-list-pane])]

    (HBox/setHgrow fn-calls-list-pane Priority/ALWAYS)
    (VBox/setVgrow split-pane Priority/ALWAYS)

    (update-functions-pane flow-id thread-id)

    split-pane))
