(ns flow-storm-debugger.ui.events.refs)

(defn select-ref [{:keys [selected-ref-id] :as db} ref-id]
  (-> db
      (assoc :selected-ref-id ref-id)))

(defn remove-ref [{:keys [selected-ref-id flows] :as db} ref-id]
  (let [db' (-> db
                (update  :refs dissoc ref-id))]
    (cond-> db'
      (= selected-ref-id ref-id) (assoc :selected-ref-id (-> db' :refs keys first)))))

(defn selected-ref-first [{:keys [selected-ref-id] :as db}]
  (assoc-in db [:refs selected-ref-id :patches-applied] 0))

(defn selected-ref-prev [{:keys [selected-ref-id] :as db}]
  (update-in db [:refs selected-ref-id :patches-applied] dec))

(defn selected-ref-next [{:keys [selected-ref-id] :as db}]
  (update-in db [:refs selected-ref-id :patches-applied] inc))

(defn selected-ref-last [{:keys [selected-ref-id refs] :as db}]
  (assoc-in db [:refs selected-ref-id :patches-applied] (count refs)))

(defn set-selected-ref-value-panel-type [{:keys [selected-ref-id] :as db} t]
  (assoc-in db [:refs selected-ref-id :value-panel-type] t))
