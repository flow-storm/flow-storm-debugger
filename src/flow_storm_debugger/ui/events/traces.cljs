(ns flow-storm-debugger.ui.events.traces
  (:require [flow-storm-debugger.ui.utils :as utils]))

(defn init-trace [db [{:keys [flow-id form-id form-flow-id args-vec fn-name form] :as trace}]]
  (-> db
      (update :selected-flow-id #(or % flow-id))
      (assoc-in [:flows flow-id :forms form-id] (utils/pprint-form-for-html form))
      (update :form-flow-id->flow-id assoc form-flow-id flow-id)
      (update-in [:flows flow-id :traces] (fn [traces]
                                            (let [gen-trace (cond-> {:flow-id flow-id
                                                                     :form-id form-id
                                                                     :form-flow-id form-flow-id
                                                                     :coor []
                                                                     :timestamp (.getTime (js/Date.))}
                                                              fn-name (assoc :args-vec args-vec
                                                                             :fn-name fn-name))]
                                              (if-not traces
                                                [gen-trace]
                                                (conj traces gen-trace)))))
      (assoc-in [:flows flow-id :trace-idx] 0)
      (update-in [:flows flow-id :bind-traces] #(or % []))
      (assoc-in [:flows flow-id :local-panel-symbol] nil)))

(defn add-bind-trace [db {:keys [flow-id form-id form-flow-id coor symbol value] :as trace}]
  (let [flow-id (or flow-id
                    (get-in db [:form-flow-id->flow-id form-flow-id]))]
    (-> db
        (update-in [:flows flow-id :bind-traces] conj {:flow-id flow-id
                                                       :form-id form-id
                                                       :coor coor
                                                       :symbol symbol
                                                       :value (utils/pprint-form-for-html value)
                                                       :timestamp (.getTime (js/Date.))}))))

(defn add-trace [db {:keys [flow-id form-id form-flow-id coor result outer-form?] :as trace}]
  (let [flow-id (or flow-id
                    (get-in db [:form-flow-id->flow-id form-flow-id]))]
    (-> db
        (update-in [:flows flow-id :traces] conj {:flow-id flow-id
                                                  :form-id form-id
                                                  :outer-form? outer-form?
                                                  :coor coor
                                                  :result (utils/pprint-form-for-html result)
                                                  :timestamp (.getTime (js/Date.))}))))
