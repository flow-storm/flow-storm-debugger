(ns flow-storm.debugger.ui.flows.printer
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.ui.tasks :as tasks]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [clojure.string :as str]
            [flow-storm.debugger.state :as dbg-state :refer [obj-lookup store-obj]])
  (:import [javafx.scene.layout Priority VBox]
           [javafx.scene.control ComboBox SelectionModel]
           [javafx.scene Scene]
           [javafx.stage Stage]))


(defn clear-prints []
  (when-let [[{:keys [clear]}] (obj-lookup "printer-print-outs-list")]
    (clear)))

(defn update-prints-controls []
  (when-let [[{:keys [clear add-all]}] (obj-lookup "printer-prints-controls-table")]
    (let [printers-rows (->> (dbg-state/printers)
                             (reduce-kv (fn [r _ frm-printers]
                                          (reduce-kv (fn [rr _ p]
                                                       (conj rr p))
                                                     r
                                                     frm-printers))
                                        [])
                             (mapv (fn [printer]
                                     [(assoc printer :cell-type :function)
                                      (assoc printer :cell-type :source-expr)
                                      (assoc printer :cell-type :print-level)
                                      (assoc printer :cell-type :print-length)
                                      (assoc printer :cell-type :format)
                                      (assoc printer :cell-type :transform-expr)
                                      (assoc printer :cell-type :enable?)
                                      (assoc printer :cell-type :action)])))]

      (clear)
      (add-all printers-rows))))

(defn build-prints-controls []
  (let [{:keys [table-view-pane] :as table-data}
        (ui/table-view :columns ["Function" "Expr" "*print-level*" "*print-length*" "Format" "Transform Fn" "Enable" "_"]
                       :resize-policy :constrained
                       :cell-factory (fn [_ {:keys [form/id coord cell-type fn-ns fn-name transform-expr-str source-expr print-level print-length format-str]}]
                                       (case cell-type
                                         :function     (ui/label :text (format "%s/%s" fn-ns fn-name))
                                         :source-expr  (ui/label :text (pr-str source-expr))
                                         :print-level  (ui/text-field :initial-text (str print-level)
                                                                      :on-change (fn [val] (dbg-state/update-printer id coord :print-level (Long/parseLong val)))
                                                                      :align :center-right)
                                         :print-length (ui/text-field :initial-text (str print-length)
                                                                      :on-change (fn [val] (dbg-state/update-printer id coord :print-length (Long/parseLong val)))
                                                                      :align :center-right)
                                         :format       (ui/text-field :initial-text format-str
                                                                      :on-change (fn [val] (dbg-state/update-printer id coord :format-str val))
                                                                      :align :center-left)
                                         :transform-expr  (ui/text-field :initial-text transform-expr-str
                                                                         :on-change (fn [val] (dbg-state/update-printer id coord :transform-expr-str val))
                                                                         :align :center-left)
                                         :enable?      (ui/check-box :on-change (fn [selected?]
                                                                                  (dbg-state/update-printer id coord :enable? selected?))
                                                                     :selected? true)

                                         :action       (ui/icon-button :icon-name "mdi-delete-forever"
                                                                       :on-click (fn []
                                                                                   (dbg-state/remove-printer id coord)
                                                                                   (update-prints-controls)))))
                       :items [])]

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

(defn- main-pane []
  (let [^ComboBox thread-id-combo (ui/combo-box :items []
                                                :on-showing (fn [cb]
                                                              (let [flows-threads (runtime-api/all-flows-threads rt-api)
                                                                    thread-ids (->> flows-threads
                                                                                    (mapv (fn [[fid tid]]
                                                                                            (let [th-name (:thread/name (dbg-state/get-thread-info tid))]
                                                                                              (format "%s ~ %s [%d]"
                                                                                                      (if fid fid "")
                                                                                                      th-name
                                                                                                      tid)))))]
                                                                (ui-utils/combo-box-set-items cb thread-ids))))
        selected-fid-tid (fn []
                           (let [^SelectionModel cb-sel-model (.getSelectionModel thread-id-combo)]
                             (when-let [sel-thread-str (.getSelectedItem cb-sel-model)]
                               (let [[_ flow-id thread-id] (re-find #"(.+)? ~ .* \[(.+)\]" sel-thread-str)]
                                 [(when flow-id (Long/parseLong flow-id))
                                  (Long/parseLong thread-id)]))))

        {:keys [list-view-pane list-view add-all clear] :as list-data}
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell {:keys [flow-id thread-id text]}]
                                      (-> list-cell
                                          (ui-utils/set-text nil)
                                          (ui-utils/set-graphic (ui/label :text text
                                                                          :tooltip (format "FlowId: %s ThreadId: %s"
                                                                                           flow-id thread-id)))))
                      :on-click (fn [mev sel-items _]
                                  (when (and (ui-utils/mouse-primary? mev)
                                             (ui-utils/double-click? mev))
                                    (let [{:keys [idx flow-id thread-id]}  (first sel-items)
                                          goto-loc (requiring-resolve 'flow-storm.debugger.ui.flows.screen/goto-location)]
                                      (goto-loc {:flow-id flow-id
                                                 :thread-id thread-id
                                                 :idx idx}))))
                      :selection-mode :single
                      :search-predicate (fn [{:keys [text]} search-str]
                                          (str/includes? text search-str)))

        refresh-btn (ui/icon-button :icon-name "mdi-reload"
                                    :on-click (fn []
                                                (let [[flow-id thread-id] (selected-fid-tid)]
                                                  (clear)
                                                  (tasks/submit-task runtime-api/thread-prints-task
                                                                     [(cond-> {:printers  (prepare-printers (dbg-state/printers))}
                                                                        flow-id   (assoc :flow-id   flow-id)
                                                                        thread-id (assoc :thread-id thread-id))]
                                                                     {:on-progress (fn [{:keys [batch flow-id thread-id]}]
                                                                                     (add-all (into []
                                                                                                    (map (fn [po]
                                                                                                           (assoc po
                                                                                                                  :flow-id flow-id
                                                                                                                  :thread-id thread-id)))
                                                                                                    batch)))})))
                                    :tooltip "Re print everything")
        prints-controls-pane (build-prints-controls)
        thread-box (ui/h-box :childs [(ui/label :text "Thread id:") thread-id-combo]
                             :spacing 5)

        split-pane (ui/split :orientation :vertical
                             :childs [prints-controls-pane list-view-pane]
                             :sizes [0.3])
        main-pane (ui/border-pane
                   :top (ui/h-box
                         :childs [refresh-btn thread-box]
                         :class "controls-box"
                         :spacing 5)

                   :center split-pane
                   :class "printer-tool"
                   :paddings [10 10 10 10])]

    (store-obj "printer-thread-id-combo" thread-id-combo)
    (store-obj "printer-print-outs-list" list-data)

    (VBox/setVgrow list-view Priority/ALWAYS)

    main-pane))

(defn open-printers-window []
  (let [window-w 1000
        window-h 1000
        scene (Scene. (main-pane) window-w window-h)
        stage (doto (Stage.)
                (.setTitle "FlowStorm printers")
                (.setScene scene))]

    (.setOnCloseRequest stage (event-handler [_] (dbg-state/unregister-jfx-stage! stage)))
    (dbg-state/register-jfx-stage! stage)

    (let [{:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) window-w window-h)]
      (.setX stage x)
      (.setY stage y))

    (update-prints-controls)

    (-> stage .show)))
(comment

  )
