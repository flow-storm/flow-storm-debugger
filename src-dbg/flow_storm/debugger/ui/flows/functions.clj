(ns flow-storm.debugger.ui.flows.functions
  (:require [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [v-box h-box label list-view table-view]]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.flows.code :as flows-code]
            [clojure.string :as str])
  (:import [javafx.scene.layout Priority HBox VBox]
           [javafx.geometry Orientation]
           [javafx.scene Node]
           [javafx.scene.control CheckBox SplitPane]
           [javafx.scene.input MouseButton]))

(defn- functions-cell-factory [x]
  (if (number? x)
    (label (str x))

    (let [{:keys [form-def-kind fn-name fn-ns dispatch-val]} x
          fn-lbl (doto (case form-def-kind
                         :defmethod       (flow-cmp/def-kind-colored-label (format "%s/%s %s" fn-ns fn-name (runtime-api/val-pprint rt-api dispatch-val {:print-length 3 :print-level 3 :pprint? false})) form-def-kind)
                         :extend-protocol (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                         :extend-type     (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                         :defrecord       (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                         :deftype          (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                         :defn            (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                         (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind))
                   (.setPrefWidth 450))]
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

(defn- show-function-calls [flow-id thread-id selected-items]
  (let [{:keys [form-id fn-ns fn-name]} (first selected-items)
        [{:keys [clear add-all]}] (obj-lookup flow-id thread-id "function_calls_list")
        fn-call-traces (runtime-api/find-fn-frames-light rt-api flow-id thread-id fn-ns fn-name form-id)]
    (clear)
    (add-all fn-call-traces)))

(defn function-enter [flow-id thread-id selected-items]
  ;; selected items contains rows like [{...fn-call...} cnt]
  (let [selected-items (map first selected-items)]
    (show-function-calls flow-id thread-id selected-items)))

(defn- function-click [flow-id thread-id mev selected-items {:keys [table-view-pane]}]
  ;; selected items contains rows like [{...fn-call...} cnt]
  (let [selected-items (map first selected-items)]
    (cond
      (and (= MouseButton/PRIMARY (.getButton mev))
           (= 2 (.getClickCount mev)))
      (show-function-calls flow-id thread-id selected-items)

      (and (= MouseButton/SECONDARY (.getButton mev))
           (not ui-vars/clojure-storm-env?))
      (let [ctx-menu-un-instrument-item {:text "Un-instrument seleced functions" :on-click (fn [] (uninstrument-items selected-items) )}
            ctx-menu (ui-utils/make-context-menu [ctx-menu-un-instrument-item])]
        (.show ctx-menu
               table-view-pane
               (.getScreenX mev)
               (.getScreenY mev))))))

(defn- create-fns-list-pane [flow-id thread-id]
  (let [{:keys [table-view-pane] :as tv-data} (table-view
                                               {:columns ["Functions" "Count"]
                                                :cell-factory-fn functions-cell-factory
                                                :resize-policy :constrained
                                                :on-click (partial function-click flow-id thread-id)
                                                :on-enter (fn [sel-items] (function-enter flow-id thread-id sel-items))
                                                :selection-mode :multiple
                                                :search-predicate (fn [[{:keys [fn-name fn-ns]} _] search-str]
                                                                    (str/includes? (format "%s/%s" fn-ns fn-name) search-str))})]

    (store-obj flow-id thread-id "functions_table_data" tv-data)

    table-view-pane))

(def max-args 9)

(defn- functions-calls-cell-factory [selected-args list-cell {:keys [args-vec]}]
  (let [sel-args (selected-args)
        args-vec-str (if (= (count sel-args) max-args)
                       (runtime-api/val-pprint rt-api args-vec {:print-length 4 :print-level 4 :pprint? false})
                       (runtime-api/val-pprint rt-api args-vec {:print-length 4 :print-level 4 :pprint? false :nth-elems sel-args}))
        args-lbl (label args-vec-str)]
    (.setGraphic ^Node list-cell args-lbl)))

(defn- function-call-click [flow-id thread-id mev selected-items {:keys [list-view-pane]}]
  (let [idx (-> selected-items first :frame-idx)
        ret-val (-> selected-items first :ret)
        jump-to-idx (fn []
                      (ui-flows-gral/select-thread-tool-tab flow-id thread-id :code)
                      (flows-code/jump-to-coord flow-id thread-id idx))]

    (cond
      (and (= MouseButton/PRIMARY (.getButton mev))
           (= 1 (.getClickCount mev)))

      (flow-cmp/update-pprint-pane flow-id thread-id "functions-calls-ret-val" ret-val)

      (and (= MouseButton/PRIMARY (.getButton mev))
           (= 2 (.getClickCount mev)))

      (jump-to-idx)

      (= MouseButton/SECONDARY (.getButton mev))
      (let [ctx-menu (ui-utils/make-context-menu [{:text "Step code"
                                                   :on-click jump-to-idx}])]
        (.show ctx-menu
               list-view-pane
               (.getScreenX mev)
               (.getScreenY mev))))))

(defn- create-fn-calls-list-pane [flow-id thread-id]
  (let [args-checks (repeatedly max-args (fn [] (doto (CheckBox.)
                                                  (.setSelected true)
                                                  (.setFocusTraversable false))))
        selected-args (fn []
                        (->> args-checks
                             (keep-indexed (fn [idx ^CheckBox cb]
                                             (when (.isSelected cb) idx)))))
        {:keys [list-view-pane] :as lv-data} (list-view {:editable? false
                                                         :cell-factory-fn (partial functions-calls-cell-factory selected-args)
                                                         :on-click (partial function-call-click flow-id thread-id)
                                                         :on-enter (fn [sel-items]
                                                                     (let [ret-val (-> sel-items first :ret)]
                                                                       (flow-cmp/update-pprint-pane flow-id thread-id "functions-calls-ret-val" ret-val)))
                                                         :selection-mode :single})
        args-print-type-checks (doto (->> args-checks
                                          (map-indexed (fn [idx cb]
                                                         (h-box [(label (format "a%d" (inc idx))) cb])))
                                          (into [(label "Print args:")])
                                          h-box)
                                 (.setSpacing 8))
        fn-call-list-pane (v-box [args-print-type-checks list-view-pane])]

    (VBox/setVgrow list-view-pane Priority/ALWAYS)

    (store-obj flow-id thread-id "function_calls_list" lv-data)
    fn-call-list-pane))

(defn create-functions-pane [flow-id thread-id]
  (let [fns-list-pane (create-fns-list-pane flow-id thread-id)
        fn-calls-list-pane (create-fn-calls-list-pane flow-id thread-id)
        fn-calls-ret-pane (flow-cmp/create-pprint-pane flow-id thread-id "functions-calls-ret-val")
        right-split-pane (doto (SplitPane.)
                           (.setOrientation (Orientation/VERTICAL)))
        split-pane (doto (SplitPane.)
                     (.setOrientation (Orientation/HORIZONTAL)))]

    (HBox/setHgrow fn-calls-list-pane Priority/ALWAYS)

    (-> right-split-pane
        .getItems
        (.addAll [fn-calls-list-pane fn-calls-ret-pane]))

    (.setDividerPosition right-split-pane 0 0.7)

    (-> split-pane
        .getItems
        (.addAll [fns-list-pane right-split-pane]))
    split-pane))

(defn update-functions-pane [flow-id thread-id]
  (let [fn-call-stats (->> (runtime-api/fn-call-stats rt-api flow-id thread-id)
                           (sort-by :cnt >)
                           (map (fn [{:keys [cnt] :as fn-call}]
                                  [fn-call cnt])))
        [{:keys [add-all clear]}] (obj-lookup flow-id thread-id "functions_table_data")]
    (clear)
    (add-all fn-call-stats)))
