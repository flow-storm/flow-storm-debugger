(ns flow-storm-debugger.ui.events.flows
  #?(:cljs (:require [ajax.core :as ajax])))

(defn set-pprint-panel [{:keys [selected-flow-id] :as db} content]
  (assoc-in db [:flows selected-flow-id :pprint-panel-content] content))

(defn- get-selected-flow-current-trace [{:keys [selected-flow-id] :as db}]
  (let [curr-trace-idx (get-in db [:flows selected-flow-id :trace-idx])]
    (get-in db [:flows selected-flow-id :traces curr-trace-idx])))

(defn selected-flow-prev [{:keys [selected-flow-id] :as db}]
  (let [db' (update-in db [:flows selected-flow-id :trace-idx] dec)
        {:keys [result]} (get-selected-flow-current-trace db')]
   (-> db'       
       (set-pprint-panel result))))

(defn selected-flow-next [{:keys [selected-flow-id] :as db}]
  (let [db' (update-in db [:flows selected-flow-id :trace-idx] inc)
        {:keys [result]} (get-selected-flow-current-trace db')]
    (-> db'
        (set-pprint-panel result))))

(defn select-flow [{:keys [selected-flow-id] :as db} flow-id]
  (-> db
      (assoc :selected-flow-id flow-id)))

(defn remove-flow [{:keys [selected-flow-id flows] :as db} flow-id]
  (let [db' (update db :flows dissoc flow-id)]
    (cond-> db'
      (= selected-flow-id flow-id) (assoc :selected-flow-id (-> db' :flows keys first)))))

(defn set-current-flow-trace-idx [{:keys [selected-flow-id] :as db} trace-idx]
  (let [db' (assoc-in db [:flows selected-flow-id :trace-idx] trace-idx)
        {:keys [result]} (get-selected-flow-current-trace db')]
    (-> db'
        (set-pprint-panel result))))

(defn open-dialog [db dialog]
  (assoc db :open-dialog dialog))

(defn load-flow [db flow]
  (let [flow-id (:flow-id flow)]
    (-> db
        (assoc-in [:flows flow-id] flow)
        (update :selected-flow-id #(or % flow-id)))))
