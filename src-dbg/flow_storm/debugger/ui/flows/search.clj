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

(set! *warn-on-reflection* true)

(defn search [{:keys [print-level print-length] :as criteria}]
  (let [[{:keys [add-all clear]}] (obj-lookup "search_results_table_data")]
    (clear)
    (tasks/submit-task rt-api/search-collect-timelines-entries-task
                       [criteria
                        {:print-level (Integer/parseInt (or print-level "3"))
                         :print-length (Integer/parseInt (or print-length "3"))}]
                       {:on-progress (fn [{:keys [flow-id thread-id batch]}]
                                       (add-all (->> batch
                                                     (mapv (fn [entry]
                                                             (let [entry (assoc entry :flow-id flow-id :thread-id thread-id)]
                                                               [(assoc entry :cell-type :flow-id)
                                                                (assoc entry :cell-type :thread-id)
                                                                (assoc entry :cell-type :idx)
                                                                (assoc entry :cell-type :preview)]))))))})))

(defn create-search-params-pane []
  (let [criteria (atom {:query-str ""
                        :predicate-code-str "(fn [v] v)"
                        :print-length "3"
                        :print-level "3"
                        :search-type :pr-str})

        search-field (ui/text-field :prompt-text "Search"
                                    :on-change (fn [new-txt] (swap! criteria assoc :query-str new-txt))
                                    :on-return-key (fn [_] (search @criteria)))

        search-btn (ui/icon-button :icon-name "mdi-magnify"
                                   :class "tree-search"
                                   :on-click (fn [] (search @criteria)))
        flows-threads (rt-api/all-flows-threads rt-api)
        flow-combo (ui/combo-box :items (into ["All"] (mapv first flows-threads))
                                 :on-change (fn [_ new-flow-id]
                                              (if (= "All" new-flow-id)
                                                (swap! criteria dissoc :flow-id)
                                                (swap! criteria assoc :flow-id new-flow-id))))
        thread-combo (ui/combo-box :items (into ["All"] (mapv second flows-threads))
                                   :on-change (fn [_ new-thread-id]
                                                (if (= "All" new-thread-id)
                                                  (swap! criteria dissoc :thread-id)
                                                  (swap! criteria assoc :thread-id new-thread-id))))

        pr-len-txt (ui/text-field :initial-text (:print-length @criteria)
                                  :on-change (fn [new-txt] (swap! criteria assoc :print-length new-txt)))
        pr-lvl-txt (ui/text-field :initial-text (:print-level @criteria)
                                  :on-change (fn [new-txt] (swap! criteria assoc :print-level new-txt)))

        ^VBox pr-str-params-box (ui/v-box
                                 :childs [search-field
                                          (ui/h-box :childs [(ui/label :text "*print-level*") pr-lvl-txt])
                                          (ui/h-box :childs [(ui/label :text "*print-length*") pr-len-txt])]
                                 :spacing 5
                                 :paddings [10])

        pred-area (ui/text-area :text (:predicate-code-str @criteria)
                                :editable? true
                                :on-change (fn [new-txt] (swap! criteria assoc :predicate-code-str new-txt)))

        ^VBox custom-pred-params-box (ui/v-box
                                      :childs [(ui/label :text "Custom predicate :")
                                               pred-area]
                                      :spacing 5
                                      :paddings [10])

        change-pred-or-prn-combo (fn [_ new-val]
                                   (case new-val
                                     "Search by pr-str"    (do
                                                             (.setDisable pr-str-params-box false)
                                                             (.setDisable custom-pred-params-box true)
                                                             (swap! criteria assoc :search-type :pr-str))
                                     "Search by predicate" (do
                                                             (.setDisable pr-str-params-box true)
                                                             (.setDisable custom-pred-params-box false)
                                                             (swap! criteria assoc :search-type  :custorm-predicate))))
        pred-or-prn-combo (ui/combo-box :items (cond-> ["Search by pr-str"]
                                                 (= :clj (dbg-state/env-kind)) (into ["Search by predicate"]))
                                        :on-change change-pred-or-prn-combo)

        gral-row-box (ui/h-box :childs [(ui/label :text "Flow:")   flow-combo
                                        (ui/label :text "Thread:") thread-combo
                                        pred-or-prn-combo
                                        search-btn]
                               :spacing 5)]

    (change-pred-or-prn-combo nil "Search by pr-str")

    (ui/border-pane :top gral-row-box
                    :left  pr-str-params-box
                    :right custom-pred-params-box
                    :paddings [10])))

(defn create-results-table-pane []
  (let [{:keys [table-view-pane table-view] :as tv-data}
        (ui/table-view
         :columns ["Flow Id" "Thread Id" "Index" "Preview"]
         :columns-width-percs [0.1 0.1 0.1 0.7]
         :cell-factory (fn [_ item]
                         (let [^Label lbl (ui/label :text (case (:cell-type item)
                                                            :flow-id   (str (:flow-id item))
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
                       (let [{:keys [flow-id thread-id idx]} (ffirst items)
                             goto-loc (requiring-resolve 'flow-storm.debugger.ui.flows.screen/goto-location)]
                         (goto-loc {:flow-id   flow-id
                                    :thread-id thread-id
                                    :idx       idx})))))]

    (store-obj "search_results_table_data" tv-data)
    (VBox/setVgrow table-view Priority/ALWAYS)

    table-view-pane))

(defn create-search-pane []
  (ui/split :orientation :vertical
            :childs [(create-search-params-pane)
                     (create-results-table-pane)]
            :sizes [0.3 0.7]))

(defn search-window []
  (let [window-w 1000
        window-h 600
        scene (Scene. (create-search-pane) window-w window-h)
        stage (doto (Stage.)
                (.setTitle "FlowStorm search")
                (.setScene scene))]

    (.setOnCloseRequest stage (event-handler [_] (dbg-state/unregister-jfx-stage! stage)))
    (dbg-state/register-jfx-stage! stage)

    (let [{:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) window-w window-h)]
      (.setX stage x)
      (.setY stage y))

    (-> stage .show)))
