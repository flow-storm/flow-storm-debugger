(ns flow-storm-debugger.ui.events.taps)

(defn select-tap [{:keys [selected-tap-id] :as db} tap-id]
  (-> db
      (assoc :selected-tap-id tap-id)))

(defn remove-tap [{:keys [selected-tap-id] :as db} tap-id]
  (let [db' (-> db
                (update  :taps dissoc tap-id))]
    (cond-> db'
      (= selected-tap-id tap-id) (assoc :selected-tap-id (-> db' :taps keys first)))))

(defn remove-all-taps [{:keys [taps] :as db}]
  (reduce (fn [r tap-id]
            (remove-tap r tap-id))
          db
          (keys taps)))

(defn remove-selected-tap [{:keys [selected-tap-id] :as db}]
  (remove-tap db selected-tap-id))

(defn set-selected-tap-value-panel-type [{:keys [selected-tap-id] :as db} t]
  (assoc-in db [:taps selected-tap-id :value-panel-type] t))

(defn set-result-panel [{:keys [selected-tap-id] :as db} content]
  (assoc-in db [:taps selected-tap-id :result-panel-content] content))

(defn- get-selected-tap-current-trace [{:keys [selected-tap-id] :as db}]
  (let [curr-trace-idx (get-in db [:taps selected-tap-id :tap-trace-idx])]
    (get-in db [:taps selected-tap-id :tap-values curr-trace-idx])))

(defn set-current-tap-trace-idx [{:keys [selected-tap-id] :as db} tap-trace-idx]
  (let [db' (assoc-in db [:taps selected-tap-id :tap-trace-idx] tap-trace-idx)
        {:keys [value]} (get-selected-tap-current-trace db')]
    (-> db'
        (set-result-panel value))))
