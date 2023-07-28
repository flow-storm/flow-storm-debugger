(ns flow-storm.debugger.ui.printer.screen
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [label list-view check-box text-field table-view h-box border-pane
                     icon-button combo-box combo-box-set-items]]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.ui.state-vars
             :as ui-vars
             :refer [obj-lookup store-obj show-message]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [clojure.string :as str]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.flows.screen :as flows-screen])
  (:import [javafx.scene.layout Priority VBox]
           [javafx.scene.input MouseButton]
           [javafx.geometry Orientation]
           [javafx.scene.control SplitPane]))

(defn clear-prints []
  (let [[{:keys [clear]}] (obj-lookup "printer-print-outs-list")]
    (clear)))

(defn update-prints-controls []
  (let [[{:keys [clear add-all]}] (obj-lookup "printer-prints-controls-table")
        [thread-id-combo] (obj-lookup "printer-thread-id-combo")
        flows-threads (runtime-api/all-flows-threads rt-api)
        printers-rows (->> (dbg-state/printers)
                           (reduce-kv (fn [r _ frm-printers]
                                        (reduce-kv (fn [rr _ p]
                                                     (conj rr p))
                                                   r
                                                   frm-printers))
                                      [])
                           (mapv (fn [printer]
                                   [(assoc printer :cell-type :function)
                                    (assoc printer :cell-type :expr)
                                    (assoc printer :cell-type :print-level)
                                    (assoc printer :cell-type :print-length)
                                    (assoc printer :cell-type :format)
                                    (assoc printer :cell-type :enable?)
                                    (assoc printer :cell-type :action)])))]

    (let [thread-ids (->> flows-threads
                          (mapv (fn [[fid tid]]
                                  (let [th-name (:thread/name (dbg-state/get-thread-info tid))]
                                    (format "%s ~ %s [%d]"
                                            (if fid fid "")
                                            th-name
                                            tid)))))]
      (when (seq thread-ids)
        (combo-box-set-items thread-id-combo thread-ids)))

    (clear)
    (add-all printers-rows)))

(defn build-prints-controls []
  (let [{:keys [table-view-pane] :as table-data}
        (table-view {:columns ["Function" "Expr" "*print-level*" "*print-length*" "Format" "Enable" "_"]
                     :resize-policy :constrained
                     :cell-factory-fn (fn [_ {:keys [form/id coord cell-type fn-ns fn-name expr print-level print-length format-str enable?]}]
                                        (case cell-type
                                          :function     (label (format "%s/%s" fn-ns fn-name))
                                          :expr         (label (pr-str expr))
                                          :print-level  (text-field {:initial-text (str print-level)
                                                                     :on-change (fn [val] (dbg-state/update-printer id coord :print-level (Long/parseLong val)))
                                                                     :align :right})
                                          :print-length (text-field {:initial-text (str print-length)
                                                                     :on-change (fn [val] (dbg-state/update-printer id coord :print-length (Long/parseLong val)))
                                                                     :align :right})
                                          :format       (text-field {:initial-text format-str
                                                                     :on-change (fn [val] (dbg-state/update-printer id coord :format-str val))
                                                                     :align :left})
                                          :enable?      (doto (check-box {:on-change (fn [selected?]
                                                                                       (dbg-state/update-printer id coord :enable? selected?))})
                                                          (.setSelected enable?))
                                          :action       (icon-button :icon-name "mdi-delete-forever"
                                                                     :on-click (fn []
                                                                                 (dbg-state/remove-printer id coord)
                                                                                 (update-prints-controls)))))
                     :items []})]

    (store-obj "printer-prints-controls-table" table-data)

    table-view-pane))

(defn- prepare-printers [printers]
  (utils/update-values
   printers
   (fn [form-printers]
     (reduce-kv (fn [r coord {:keys [enable?] :as printer}]
                  (if enable?
                    (let [p (update printer :format-str (fn [fs]
                                                          (if (str/includes? fs "%s")
                                                            fs
                                                            (str fs " %s"))))]
                      (assoc r coord p))
                    r))
      {}
      form-printers))))

(defn main-pane []
  (let [thread-id-combo (combo-box {:items []})
        selected-fid-tid (fn []
                                  (when-let [sel-thread-str (->> thread-id-combo
                                                                 .getSelectionModel
                                                                 .getSelectedItem)]
                                    (let [[_ flow-id thread-id] (re-find #"(.+)? ~ .* \[(.+)\]" sel-thread-str)]
                                      [(when flow-id (Long/parseLong flow-id))
                                       (Long/parseLong thread-id)])))
        {:keys [list-view-pane list-view add-all clear] :as list-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell {:keys [text]}]
                                       (.setText list-cell nil)
                                       (.setGraphic list-cell (label text)))
                    :on-click (fn [mev sel-items _]
                                (when (and (= MouseButton/PRIMARY (.getButton mev))
                                           (= 2 (.getClickCount mev)))
                                  (let [[flow-id thread-id] (selected-fid-tid)
                                        idx (-> sel-items first :idx)]
                                    (flows-screen/goto-location {:flow-id flow-id
                                                                 :thread-id thread-id
                                                                 :idx idx}))))
                    :selection-mode :single
                    :search-predicate (fn [{:keys [text]} search-str]
                                        (str/includes? text search-str))})

        refresh-btn (icon-button :icon-name "mdi-reload"
                                 :on-click (fn []
                                             (if-let [[flow-id thread-id] (selected-fid-tid)]
                                               (let [print-outs (runtime-api/thread-prints rt-api {:flow-id   flow-id
                                                                                                   :thread-id thread-id
                                                                                                   :printers  (prepare-printers (dbg-state/printers))})]
                                                 (clear)
                                                 (add-all print-outs))

                                               (show-message "You need to select a thread first" :error)))
                                 :tooltip "Re print everything")
        prints-controls-pane (build-prints-controls)
        thread-box (doto (h-box [(label "Thread id:") thread-id-combo])
                     (.setSpacing 5))
        split-pane (doto (SplitPane.)
                     (.setOrientation (Orientation/VERTICAL)))
        main-pane (border-pane
                   {:top (doto (h-box [refresh-btn thread-box] "controls-box")
                           (.setSpacing 5))
                    :center split-pane}
                   "printer-tool")]

    (-> split-pane
        .getItems
        (.addAll [prints-controls-pane list-view-pane]))

    (.setDividerPosition split-pane 0 0.3)

    (store-obj "printer-thread-id-combo" thread-id-combo)
    (store-obj "printer-print-outs-list" list-data)
    (update-prints-controls)
    (VBox/setVgrow list-view Priority/ALWAYS)

    main-pane))

(comment

  )
