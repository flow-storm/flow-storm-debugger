(ns flow-storm-debugger.ui.events.flows
  (:require [ajax.core :as ajax]))


(defn selected-flow-prev [{:keys [selected-flow-id] :as db} _]
  (update-in db [:flows selected-flow-id :trace-idx] dec))

(defn selected-flow-next [{:keys [selected-flow-id] :as db} _]
  (update-in db [:flows selected-flow-id :trace-idx] inc))

(defn select-flow [{:keys [selected-flow-id] :as db} [flow-id]]
  (-> db
      (assoc :selected-flow-id flow-id)))

(defn remove-flow [{:keys [selected-flow-id flows] :as db} [flow-id]]
  (let [db' (update db :flows dissoc flow-id)]
    (cond-> db'
      (= selected-flow-id flow-id) (assoc :selected-flow-id (-> db' :flows keys first)))))

(defn set-current-flow-trace-idx [{:keys [selected-flow-id] :as db} [trace-idx]]
  (assoc-in db [:flows selected-flow-id :trace-idx] trace-idx))

(defn save-selected-flow [cofx [file-name]]
  (let [selected-flow-id (get-in cofx [:db :selected-flow-id])
        selected-flow (get-in cofx [:db :flows selected-flow-id])]
    {:http-xhrio {:method          :post
                  :uri             "http://localhost:7722/save-flow"
                  :timeout         8000
                  :params (-> selected-flow
                              (select-keys [:forms :traces :trace-idx :bind-traces])
                              (assoc :flow-id selected-flow-id)
                              pr-str)
                  :url-params {:file-name file-name}
                  :format (ajax/text-request-format)
                  :response-format (ajax/raw-response-format)
                  :on-success      [:flow-storm-debugger.ui.events/hide-modals]
                  :on-failure      []}}))

(defn load-flow [db [flow]]
  (let [flow-id (:flow-id flow)]
    (-> db
        (assoc-in [:flows flow-id] flow)
        (update :selected-flow-id #(or % flow-id)))))
