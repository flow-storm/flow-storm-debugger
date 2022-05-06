(ns flow-storm.debugger.ui.flows.functions
  (:require [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label]]
            [flow-storm.debugger.ui.flows.general :as ui-flows-gral]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.target-commands :as target-commands]
            [flow-storm.debugger.state :as state]
            [flow-storm.debugger.trace-indexer.protos :as indexer]
            [flow-storm.debugger.ui.flows.code :as flows-code])
  (:import [javafx.scene.layout Priority HBox VBox]
           [javafx.collections FXCollections ObservableList]
           [javafx.scene Node]
           [javafx.scene.control ComboBox ListView SelectionMode]
           [javafx.scene.input MouseButton]
           [javafx.util StringConverter]))

(defn- create-fns-list-pane [flow-id thread-id]
  (let [observable-fns-list (FXCollections/observableArrayList)
        cell-factory (proxy [javafx.util.Callback] []
                       (call [lv]
                         (ui-utils/create-list-cell-factory
                          (fn [list-cell {:keys [form-def-kind fn-name fn-ns dispatch-val cnt]}]
                            (let [fn-lbl (doto (case form-def-kind
                                                 :defmethod       (flow-cmp/def-kind-colored-label (format "%s/%s %s" fn-ns fn-name @dispatch-val) form-def-kind)
                                                 :extend-protocol (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                                                 :extend-type     (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                                                 :defn            (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind)
                                                 (flow-cmp/def-kind-colored-label (format "%s/%s" fn-ns fn-name) form-def-kind))
                                           (.setPrefWidth 500))
                                  cnt-lbl (doto (label (str cnt))
                                            (.setPrefWidth 100))
                                  hbox (h-box [fn-lbl cnt-lbl])]
                              (.setGraphic ^Node list-cell hbox))))))
        fns-list-view (doto (ListView. observable-fns-list)
                               (.setEditable false)
                               (.setCellFactory cell-factory))
        fns-list-selection (.getSelectionModel fns-list-view)
        ctx-menu-un-instrument-item {:text "Un-instrument seleced functions"
                                     :on-click (fn []
                                                 (let [groups (->> (.getSelectedItems fns-list-selection)
                                                                   (group-by (fn [{:keys [form-def-kind]}]
                                                                               (cond
                                                                                 (#{:defn} form-def-kind) :vars
                                                                                 (#{:defmethod :extend-protocol :extend-type} form-def-kind) :forms
                                                                                 :else nil))))]

                                                   (let [vars-symbs (->> (:vars groups)
                                                                         (map (fn [{:keys [fn-name fn-ns]}]
                                                                                (symbol fn-ns fn-name))))]
                                                     (target-commands/run-command :uninstrument-fn-bulk vars-symbs))

                                                   (let [forms (->> (:forms groups)
                                                                    (map (fn [{:keys [fn-ns form]}]
                                                                           {:form-ns fn-ns
                                                                            :form form})))]
                                                     (target-commands/run-command :eval-form-bulk forms))))}
        ctx-menu-show-similar-fn-call-item {:text "Show function calls"
                                            :on-click (fn []
                                                        (let [indexer (state/thread-trace-indexer flow-id thread-id)
                                                              {:keys [form-id fn-ns fn-name]} (first (.getSelectedItems fns-list-selection))
                                                              [observable-fn-calls-list] (obj-lookup flow-id (ui-vars/thread-fn-calls-list-id thread-id))
                                                              fn-call-traces (indexer/find-fn-calls indexer fn-ns fn-name form-id)]
                                                          (doto observable-fn-calls-list
                                                            .clear
                                                            (.addAll (into-array Object fn-call-traces)))))}]

    (.setOnMouseClicked fns-list-view
                        (event-handler
                         [mev]
                         (when (= MouseButton/SECONDARY (.getButton mev))
                           (let [sel-cnt (count (.getSelectedItems fns-list-selection))
                                 ctx-menu (if (= 1 sel-cnt)
                                            (ui-utils/make-context-menu [ctx-menu-un-instrument-item ctx-menu-show-similar-fn-call-item])
                                            (ui-utils/make-context-menu [ctx-menu-un-instrument-item]))]
                             (.show ctx-menu
                                    fns-list-view
                                    (.getScreenX mev)
                                    (.getScreenY mev))))))

    (.setSelectionMode fns-list-selection SelectionMode/MULTIPLE)
    (store-obj flow-id (ui-vars/thread-fns-list-id thread-id) observable-fns-list)
    fns-list-view))

(defn- create-fn-calls-list-pane [flow-id thread-id]
  (let [observable-fn-calls-list (FXCollections/observableArrayList)
        list-cell-factory (proxy [javafx.util.Callback] []
                            (call [lv]
                              (ui-utils/create-list-cell-factory
                               (fn [list-cell {:keys [args-vec]}]
                                 (let [args-vec-val @args-vec
                                       [args-print-type-combo] (obj-lookup flow-id (ui-vars/thread-fn-args-print-combo thread-id))
                                       pargs (.getSelectedItem (.getSelectionModel args-print-type-combo))
                                       arg-selector (fn [n]
                                                      (when (< n (count args-vec-val))
                                                        (str "... " (flow-cmp/format-value-short (nth args-vec-val n)) " ...")))
                                       args-lbl (label (cond
                                                         (= pargs "Print all args")   (flow-cmp/format-value-short args-vec-val)
                                                         (= pargs "Print only arg 0") (arg-selector 0)
                                                         (= pargs "Print only arg 1") (arg-selector 1)
                                                         (= pargs "Print only arg 2") (arg-selector 2)
                                                         (= pargs "Print only arg 3") (arg-selector 3)
                                                         (= pargs "Print only arg 4") (arg-selector 4)
                                                         (= pargs "Print only arg 5") (arg-selector 5)
                                                         (= pargs "Print only arg 6") (arg-selector 6)
                                                         (= pargs "Print only arg 7") (arg-selector 7)
                                                         (= pargs "Print only arg 8") (arg-selector 8)
                                                         (= pargs "Print only arg 9") (arg-selector 9)))]
                                   (.setGraphic ^Node list-cell args-lbl))))))
        combo-cell-factory (proxy [javafx.util.Callback] []
                             (call [lv]
                               (ui-utils/create-list-cell-factory
                                (fn [cell text]
                                  (.setText cell text)))))
        args-print-type-combo (doto (ComboBox.)
                                (.setItems (doto (FXCollections/observableArrayList)
                                             (.addAll (into-array String ["Print all args"
                                                                          "Print only arg 0"
                                                                          "Print only arg 1"
                                                                          "Print only arg 2"
                                                                          "Print only arg 3"
                                                                          "Print only arg 4"
                                                                          "Print only arg 5"
                                                                          "Print only arg 6"
                                                                          "Print only arg 7"
                                                                          "Print only arg 8"
                                                                          "Print only arg 9"]))))
                                (.setCellFactory combo-cell-factory))
        _ (.selectFirst (.getSelectionModel args-print-type-combo))
        _ (store-obj flow-id (ui-vars/thread-fn-args-print-combo thread-id) args-print-type-combo)
        fn-call-list-view (doto (ListView. observable-fn-calls-list)
                            (.setEditable false)
                            (.setCellFactory list-cell-factory))
        fn-call-list-selection (.getSelectionModel fn-call-list-view)
        fn-call-list-pane (v-box [args-print-type-combo fn-call-list-view])]

    (VBox/setVgrow fn-call-list-view Priority/ALWAYS)

    (.setOnMouseClicked fn-call-list-view
                        (event-handler
                         [mev]
                         (when (= MouseButton/SECONDARY (.getButton mev))
                           (let [trace-idx (-> (.getSelectedItems fn-call-list-selection)
                                               first
                                               meta
                                               :trace-idx)
                                 ctx-menu (ui-utils/make-context-menu [{:text (format "Goto trace %d" trace-idx)
                                                                        :on-click (fn []
                                                                                    (ui-flows-gral/select-tool-tab flow-id thread-id :code)
                                                                                    (flows-code/jump-to-coord flow-id thread-id trace-idx))}])]
                             (.show ctx-menu
                                    fn-call-list-view
                                    (.getScreenX mev)
                                    (.getScreenY mev))))))

    (.setSelectionMode fn-call-list-selection SelectionMode/SINGLE)
    (store-obj flow-id (ui-vars/thread-fn-calls-list-id thread-id) observable-fn-calls-list)
    fn-call-list-pane))

(defn create-functions-pane [flow-id thread-id]
  (let [fns-list-pane (create-fns-list-pane flow-id thread-id)
        fn-calls-list-pane (create-fn-calls-list-pane flow-id thread-id)]
    (HBox/setHgrow fns-list-pane Priority/ALWAYS)
    (HBox/setHgrow fn-calls-list-pane Priority/ALWAYS)
    (h-box [fns-list-pane fn-calls-list-pane])))

(defn update-functions-pane [flow-id thread-id]
  (let [fn-call-stats (->> (state/fn-call-stats flow-id thread-id)
                           (sort-by :cnt >))
        [^ObservableList observable-bindings-list] (obj-lookup flow-id (ui-vars/thread-fns-list-id thread-id))]
    (.clear observable-bindings-list)
    (.addAll observable-bindings-list (into-array Object fn-call-stats))))
