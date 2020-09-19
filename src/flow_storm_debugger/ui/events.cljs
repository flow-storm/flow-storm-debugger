(ns flow-storm-debugger.ui.events
  (:require [re-frame.core :refer [reg-event-db]]
            [flow-storm-debugger.ui.db :as db]
            [cljs.tools.reader :as tools-reader]
            [flow-storm-debugger.ui.utils :as utils]))

(reg-event-db ::init (fn [_ _] (db/initial-db)))

(reg-event-db ::selected-flow-prev
              (fn [{:keys [selected-flow-id] :as db} _]
                (update-in db [:flows selected-flow-id :trace-idx] dec)))

(reg-event-db ::selected-flow-next
              (fn [{:keys [selected-flow-id] :as db} _]
                (update-in db [:flows selected-flow-id :trace-idx] inc)))

(reg-event-db ::add-trace (fn [db [_ {:keys [flow-id form-id coor result] :as trace}]]
                            (-> db
                                (update-in [:flows flow-id :traces] conj {:flow-id flow-id
                                                                          :form-id form-id
                                                                          :coor coor
                                                                          :result (utils/pprint-form-for-html result)
                                                                          :timestamp (.getTime (js/Date.))}))))

(reg-event-db ::add-bind-trace (fn [db [_ {:keys [flow-id form-id coor symbol value] :as trace}]]
                            (-> db
                                (update-in [:flows flow-id :bind-traces] conj {:flow-id flow-id
                                                                               :form-id form-id
                                                                               :coor coor
                                                                               :symbol symbol
                                                                               :value (utils/pprint-form-for-html value)
                                                                               :timestamp (.getTime (js/Date.))}))))
(reg-event-db ::init-trace (fn [db [_ {:keys [flow-id form-id form]}]]
                             (-> db
                                 (update :selected-flow-id #(or % flow-id))
                                 (assoc-in [:flows flow-id :forms form-id] (utils/pprint-form-for-html form))
                                 (update-in [:flows flow-id :traces] #(or % []))
                                 (assoc-in [:flows flow-id :trace-idx] 0)
                                 (update-in [:flows flow-id :bind-traces] #(or % []))
                                 (assoc-in [:flows flow-id :local-panel-symbol] nil))))

(reg-event-db
 ::select-flow
 (fn [{:keys [selected-flow-id] :as db} [_ flow-id]]
   (-> db
       (assoc :selected-flow-id flow-id))))

(reg-event-db
 ::remove-flow
 (fn [{:keys [selected-flow-id flows] :as db} [_ flow-id]]
   (let [db' (update db :flows dissoc flow-id)]
     (cond-> db'
       (= selected-flow-id flow-id) (assoc :selected-flow-id (-> db' :flows keys first))))))

(reg-event-db
 ::set-current-flow-trace-idx
 (fn [{:keys [selected-flow-id] :as db} [_ trace-idx]]
   (assoc-in db [:flows selected-flow-id :trace-idx] trace-idx)))

(reg-event-db
 ::select-result-panel
 (fn [db [_ result-panel]]
   (assoc db :selected-result-panel result-panel)))

(reg-event-db
 ::show-local
 (fn [{:keys [selected-flow-id] :as db} [_ symbol value]]
   (assoc-in db [:flows selected-flow-id :local-panel] [symbol value])))

(reg-event-db
 ::hide-local-panel
 (fn [{:keys [selected-flow-id] :as db} _]
   (assoc-in db [:flows selected-flow-id :local-panel] nil)))
