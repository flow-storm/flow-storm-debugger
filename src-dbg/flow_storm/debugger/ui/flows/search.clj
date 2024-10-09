(ns flow-storm.debugger.ui.flows.search
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.ui.tasks :as tasks]
            [flow-storm.debugger.runtime-api :as rt-api :refer [rt-api]])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.control Label]
           [javafx.scene.layout VBox Priority]))


(defn search [{:keys [flow-id print-level print-length] :as criteria}]
  (let [[{:keys [add-all clear]}] (obj-lookup flow-id "search_results_table_data")]
    (clear)
    (tasks/submit-task rt-api/search-collect-timelines-entries-task
                       [criteria
                        {:print-level (Integer/parseInt (or print-level "3"))
                         :print-length (Integer/parseInt (or print-length "3"))}]
                       {:on-progress (fn [{:keys [batch]}]
                                       (add-all (->> batch
                                                     (mapv (fn [entry]
                                                             [(assoc entry :cell-type :thread-id)
                                                              (assoc entry :cell-type :idx)
                                                              (assoc entry :cell-type :preview)])))))})))

(defn create-search-params-pane [flow-id]
  (let [criteria (atom {:flow-id flow-id})

        thread-combo (ui/combo-box :items (let [flows-threads (rt-api/all-flows-threads rt-api)]
                                            (->> (keep (fn [[fid tid]]
                                                         (when (= fid flow-id)
                                                           {:thread-id tid
                                                            :thread-name (:thread/name (dbg-state/get-thread-info tid))}))
                                                       flows-threads)
                                                 (into [{:thread-name "All"}])))
                                   :cell-factory (fn [_ {:keys [thread-id thread-name]}]
                                                   (ui/label :text (if thread-id
                                                                     (ui/thread-label thread-id thread-name)
                                                                     thread-name)))
                                   :button-factory (fn [_ {:keys [thread-id thread-name]}]
                                                     (ui/label :text (if thread-id
                                                                       (ui/thread-label thread-id thread-name)
                                                                       thread-name)))
                                   :on-change (fn [_ {:keys [thread-id]}]
                                                (if thread-id
                                                  (swap! criteria assoc :thread-id thread-id)
                                                  (swap! criteria dissoc :thread-id))))

        search-txt (ui/text-field :prompt-text "Search")

        ;; Pr-str search
        pr-len-txt (ui/text-field :initial-text "3" (:print-length @criteria))
        pr-lvl-txt (ui/text-field :initial-text "3" (:print-level @criteria))

        pr-str-params-box (let [search-btn (ui/button :label "Search"
                                                           :on-click (fn []
                                                                       (search (assoc @criteria
                                                                                      :search-type :pr-str
                                                                                      :print-length (.getText pr-len-txt)
                                                                                      :print-level (.getText pr-lvl-txt)
                                                                                      :query-str (.getText search-txt)))))]
                            (ui/v-box
                             :childs [search-txt
                                      (ui/h-box :childs [(ui/label :text "*print-level*") pr-lvl-txt])
                                      (ui/h-box :childs [(ui/label :text "*print-length*") pr-len-txt])
                                      search-btn]
                             :spacing 5
                             :paddings [10]))

        ;; Custom pred search
        pred-area (ui/text-area :text "(fn [v] (map? v))"
                                :editable? true
                                :on-change (fn [new-txt] (swap! criteria assoc :predicate-code-str new-txt)))

        custom-pred-params-box (let [search-btn (ui/button :label "Search"
                                                           :on-click (fn []
                                                                       (search (assoc @criteria
                                                                                      :predicate-code-str (.getText pred-area)
                                                                                      :search-type  :custorm-predicate))))]
                                 (ui/v-box
                                  :childs [(ui/label :text "Custom predicate :")
                                           pred-area
                                           search-btn]
                                  :spacing 5
                                  :paddings [10]))

        ;; Data window search
        dw-id-combo (ui/combo-box :items (keys (dbg-state/data-windows))
                                  :cell-factory (fn [_ dw-id] (ui/label :text (str dw-id)))
                                  :button-factory (fn [_ dw-id] (ui/label :text (str dw-id))))

        dw-search-box (let [search-btn (ui/button :label "Search"
                                                  :on-click (fn []
                                                              (let [sel-dw-id (ui-utils/combo-box-get-selected-item dw-id-combo)
                                                                    dw-val-ref (some-> (dbg-state/data-window sel-dw-id) :stack first :val-data :flow-storm.runtime.values/val-ref)]
                                                                (when dw-val-ref
                                                                  (search (assoc @criteria
                                                                                 :search-type :val-identity
                                                                                 :val-ref dw-val-ref))))))]
                        (ui/v-box :childs [(ui/h-box :childs [(ui/label :text "Search value selected in data window id:") dw-id-combo])
                                           search-btn]
                                  :spacing 5
                                  :paddings [10]))
        search-tabs (ui/tab-pane :closing-policy :unavailable
                                 :tabs (cond-> [(ui/tab :text "By pr-str"
                                                        :content pr-str-params-box
                                                        :tooltip "Search by sub strings inside the pr-str of values")
                                                (ui/tab :text "Data window val"
                                                        :content dw-search-box
                                                        :tooltip "Search for values matching a predicate")]
                                         (= :clj (dbg-state/env-kind)) (conj (ui/tab :text "By predicate"
                                                                                     :content custom-pred-params-box
                                                                                     :tooltip "Search for values matching a predicate"))))

        gral-row-box (ui/h-box :childs [(ui/label :text "Thread:") thread-combo]
                               :spacing 5)]


    (ui/border-pane :top gral-row-box
                    :center search-tabs
                    :paddings [10])))

(defn create-results-table-pane [flow-id]
  (let [{:keys [table-view-pane table-view] :as tv-data}
        (ui/table-view
         :columns ["Thread Id" "Index" "Preview"]
         :columns-width-percs [0.1 0.1 0.7]
         :cell-factory (fn [_ item]
                         (let [^Label lbl (ui/label :text (case (:cell-type item)
                                                            :thread-id (str (:thread-id item))
                                                            :idx       (str (:idx item))
                                                            :preview   (str (:entry-preview item))))]
                           (.setMaxHeight lbl 50)
                           lbl))
         :resize-policy :constrained
         :selection-mode :single
         :on-click (fn [mev items _]
                     (when (and (ui-utils/mouse-primary? mev)
                                (ui-utils/double-click? mev))
                       (let [{:keys [thread-id idx]} (ffirst items)
                             goto-loc (requiring-resolve 'flow-storm.debugger.ui.flows.screen/goto-location)]
                         (goto-loc {:flow-id   flow-id
                                    :thread-id thread-id
                                    :idx       idx})))))]

    (store-obj flow-id "search_results_table_data" tv-data)
    (VBox/setVgrow table-view Priority/ALWAYS)

    table-view-pane))

(defn create-search-pane [flow-id]
  (ui/v-box
   :childs [(ui/label :text (format "Flow: %d" flow-id))
            (ui/v-box :childs [(create-search-params-pane flow-id)
                               (create-results-table-pane flow-id)])]))

(defn search-window [flow-id]
  (let [window-w 1000
        window-h 600
        scene (Scene. (create-search-pane flow-id) window-w window-h)
        stage (doto (Stage.)
                (.setTitle "FlowStorm search")
                (.setScene scene))]

    (.setOnCloseRequest stage (event-handler [_] (dbg-state/unregister-jfx-stage! stage)))
    (dbg-state/register-jfx-stage! stage)

    (let [{:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) window-w window-h)]
      (.setX stage x)
      (.setY stage y))

    (-> stage .show)))
