(ns flow-storm-debugger.ui.events.traces
  (:require [flow-storm-debugger.ui.utils :as utils]
            [flow-storm-debugger.ui.events.flows :as events.flows]
            [flow-storm-debugger.highlighter :as highlighter]))

(defn flow-name [form]
  (let [str-len (count form)]
    (when form
     (cond-> form
       true (subs 0 (min 20 str-len))
       (> str-len 20) (str "...")))))

(defn init-trace [db {:keys [flow-id form-id form-flow-id args-vec fn-name form fixed-flow-id-starter?] :as trace}]
  (let [now (utils/get-timestamp)
        ;; if it is the starter init-trace of a fixed flow-id, remove the flow and start clean
        db (if fixed-flow-id-starter? (events.flows/remove-flow db flow-id) db)]
   (-> db
       (update :selected-flow-id #(or % flow-id))
       (update-in [:flows flow-id :flow-name] #(or % (flow-name form)))
       (assoc-in [:flows flow-id :forms form-id] (utils/pprint-form-for-html form))
       (update :form-flow-id->flow-id assoc form-flow-id flow-id)
       (update-in [:flows flow-id :traces] (fn [traces]
                                             (let [gen-trace (cond-> {:flow-id flow-id
                                                                      :form-id form-id
                                                                      :form-flow-id form-flow-id
                                                                      :coor []
                                                                      :timestamp now}
                                                               fn-name (assoc :args-vec args-vec
                                                                              :fn-name fn-name))]
                                               (if-not traces
                                                 [gen-trace]
                                                 (conj traces gen-trace)))))
       (assoc-in [:flows flow-id :trace-idx] 0)
       (assoc-in [:flows flow-id :timestamp] now)
       (update-in [:flows flow-id :bind-traces] #(or % [])))))

(defn add-bind-trace [db {:keys [flow-id form-id form-flow-id coor symbol value] :as trace}]
  (let [flow-id (or flow-id
                    (get-in db [:form-flow-id->flow-id form-flow-id]))]
    (-> db
        (update-in [:flows flow-id :bind-traces] conj {:flow-id flow-id
                                                       :form-id form-id
                                                       :coor coor
                                                       :symbol symbol
                                                       :value (utils/pprint-form-for-html value)
                                                       :timestamp (utils/get-timestamp)}))))

(defn flow-traces [db flow-id]
  (get-in db [:flows flow-id :traces]))

(defn flow-current-trace [{:keys [selected-flow-id] :as db} flow-id]
  (let [trace-idx (get-in db [:flows flow-id :trace-idx])
        traces (flow-traces db flow-id)]
    (get traces trace-idx)))

(defn add-trace [db {:keys [flow-id form-id form-flow-id coor result outer-form? err] :as trace}]
  (let [flow-id (or flow-id
                    (get-in db [:form-flow-id->flow-id form-flow-id]))

        traces (flow-traces db flow-id)
        trace-idx (count traces)
        new-trace (cond-> {:flow-id flow-id
                           :form-id form-id
                           :outer-form? outer-form?
                           :trace-idx trace-idx
                           :coor coor                                                  
                           :timestamp (utils/get-timestamp)}
                    (not err) (assoc :result (utils/pprint-form-for-html result))
                    err       (assoc :err err))
        flow-trace-idx (get-in db [:flows flow-id :trace-idx])]

    (cond-> (update-in db [:flows flow-id :traces] conj new-trace)

      ;; if this trace we are adding contains a error and the debugger isn't already
      ;; pointing to a error, set the debugger pointing to this trace
      (and err (not (:err (flow-current-trace db flow-id))))
      (assoc-in [:flows flow-id :trace-idx] trace-idx))))
