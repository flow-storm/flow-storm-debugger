(ns flow-storm-debugger.ui.events
  (:require [re-frame.core :refer [reg-event-db]]
            [flow-storm-debugger.ui.db :as db]
            [cljs.tools.reader :as tools-reader]))

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
                                                                          :result result}))))

(reg-event-db ::init-trace (fn [db [_ {:keys [flow-id form-id form]}]]
                             (-> db
                                 (update :selected-flow-id #(or % flow-id))
                                 (assoc-in [:flows flow-id :forms form-id] form)
                                 (update-in [:flows flow-id :traces] #(or % []))
                                 (assoc-in [:flows flow-id :trace-idx] 0))))

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
