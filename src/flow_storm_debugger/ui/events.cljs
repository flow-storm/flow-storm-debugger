(ns flow-storm-debugger.ui.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]
            [flow-storm-debugger.ui.db :as db]
            [cljs.tools.reader :as tools-reader]
            [flow-storm-debugger.ui.utils :as utils]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]))

(reg-event-db ::init (fn [_ _] (db/initial-db)))

(reg-event-db ::selected-flow-prev
              (fn [{:keys [selected-flow-id] :as db} _]
                (update-in db [:flows selected-flow-id :trace-idx] dec)))

(reg-event-db ::selected-flow-next
              (fn [{:keys [selected-flow-id] :as db} _]
                (update-in db [:flows selected-flow-id :trace-idx] inc)))

(reg-event-db ::add-trace (fn [db [_ {:keys [flow-id form-id coor result outer-form?] :as trace}]]
                            (-> db
                                (update-in [:flows flow-id :traces] conj {:flow-id flow-id
                                                                          :form-id form-id
                                                                          :outer-form? outer-form?
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
                                 (update-in [:flows flow-id :traces] (fn [traces]
                                                                       (if-not traces
                                                                         []
                                                                         (conj traces {:flow-id flow-id
                                                                                       :form-id form-id
                                                                                       :coor []
                                                                                       :fn-call? true
                                                                                       :timestamp (.getTime (js/Date.))}))))
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
 ::hide-modals
 (fn [{:keys [selected-flow-id] :as db} _]
   (-> db
       (assoc-in [:flows selected-flow-id :local-panel] nil)
       (assoc-in [:flows selected-flow-id :save-flow-panel-open?] false))))

(reg-event-db
 ::open-save-panel
 (fn [{:keys [selected-flow-id] :as db} _]
   (assoc-in db [:flows selected-flow-id :save-flow-panel-open?] true)))

(reg-event-fx
 ::save-selected-flow
 (fn [cofx [_ file-name]]
   (let [selected-flow-id (get-in cofx [:db :selected-flow-id])
         selected-flow (get-in cofx [:db :flows selected-flow-id])]
     (println "Saving to " file-name (pr-str selected-flow))
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
                   :on-failure      []}})))

(reg-event-db
 ::load-flow
 (fn [db [_ flow]]
   (let [flow-id (:flow-id flow)]
     (-> db
         (assoc-in [:flows flow-id] flow)
         (update :selected-flow-id #(or % flow-id))))))
