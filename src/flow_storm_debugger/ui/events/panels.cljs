(ns flow-storm-debugger.ui.events.panels)

(defn select-result-panel [db [result-panel]]
  (assoc db :selected-result-panel result-panel))

(defn show-local [{:keys [selected-flow-id] :as db} [symbol value]]
  (assoc-in db [:flows selected-flow-id :local-panel] [symbol value]))

(defn hide-modals [{:keys [selected-flow-id] :as db} _]
  (-> db
      (assoc-in [:flows selected-flow-id :local-panel] nil)
      (assoc-in [:flows selected-flow-id :save-flow-panel-open?] false)))

(defn open-save-panel [{:keys [selected-flow-id] :as db} _]
  (assoc-in db [:flows selected-flow-id :save-flow-panel-open?] true))
