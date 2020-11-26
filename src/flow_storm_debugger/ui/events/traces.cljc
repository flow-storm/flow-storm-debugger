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

(defn init-trace [db {:keys [flow-id form-id form-flow-id args-vec fn-name form fixed-flow-id-starter? timestamp] :as trace}]
  (let [;; if it is the starter init-trace of a fixed flow-id, remove the flow and start clean
        db (if fixed-flow-id-starter? (events.flows/remove-flow db flow-id) db)]
   (-> db
       (update :selected-flow-id #(or % flow-id))
       (update-in [:flows flow-id :flow-name] #(or % (flow-name form)))
       (assoc-in [:flows flow-id :forms form-id] (utils/pprint-form-for-html form))
       (update :form-flow-id->flow-id assoc form-flow-id flow-id)
       (update-in [:flows flow-id :traces] (fn [traces]
                                             (let [trace-idx (count traces)
                                                   gen-trace (cond-> {:flow-id flow-id
                                                                      :form-id form-id
                                                                      :form-flow-id form-flow-id
                                                                      :coor []
                                                                      :trace-idx trace-idx
                                                                      :timestamp timestamp}
                                                               fn-name (assoc :args-vec args-vec
                                                                              :fn-name fn-name))]
                                               (if-not traces
                                                 [gen-trace]
                                                 (conj traces gen-trace)))))
       (assoc-in [:flows flow-id :trace-idx] 0)
       (assoc-in [:flows flow-id :timestamp] timestamp)
       (update-in [:flows flow-id :bind-traces] #(or % [])))))

(defn add-bind-trace [db {:keys [flow-id form-id form-flow-id coor symbol value] :as trace}]
  (let [flow-id (or flow-id
                    (get-in db [:form-flow-id->flow-id form-flow-id]))]
    (-> db
        (update-in [:flows flow-id :bind-traces] conj {:flow-id flow-id
                                                       :form-id form-id
                                                       :coor coor
                                                       :symbol symbol
                                                       :value value
                                                       :timestamp (utils/get-timestamp)}))))

(defn flow-traces [db flow-id]
  (get-in db [:flows flow-id :traces]))

(defn flow-current-trace [{:keys [selected-flow-id] :as db} flow-id]
  (let [trace-idx (get-in db [:flows flow-id :trace-idx])
        traces (flow-traces db flow-id)]
    (get traces trace-idx)))

(defn add-trace [db {:keys [flow-id form-id form-flow-id coor result outer-form? err timestamp] :as trace}]
  (let [flow-id (or flow-id
                    (get-in db [:form-flow-id->flow-id form-flow-id]))

        traces (flow-traces db flow-id)
        trace-idx (count traces)
        new-trace (cond-> {:flow-id flow-id
                           :form-id form-id
                           :outer-form? outer-form?
                           :trace-idx trace-idx
                           :coor coor                                                  
                           :timestamp timestamp}
                    (not err) (assoc :result result)
                    err       (assoc :err err))
        flow-trace-idx (get-in db [:flows flow-id :trace-idx])]

    (cond-> (update-in db [:flows flow-id :traces] conj new-trace)

      ;; if this trace we are adding contains a error and the debugger isn't already
      ;; pointing to a error, set the debugger pointing to this trace
      (and err (not (:err (flow-current-trace db flow-id))))
      (assoc-in [:flows flow-id :trace-idx] trace-idx))))

(defn add-ref-init-trace [db {:keys [ref-id ref-name init-val timestamp] :as trace}]
  (-> db
      (update :selected-ref-id #(or % ref-id))
      (assoc-in [:refs ref-id] {:ref-name ref-name
                                :ref-id ref-id
                                :init-val (utils/read-form init-val)
                                :patches []
                                :patches-applied 0
                                :timestamp timestamp})))

(defn add-ref-trace [db {:keys [ref-id patch timestamp] :as trace}]
  (-> db
      (update-in [:refs ref-id] (fn [ref]
                                  (-> ref
                                      (update :patches conj {:timestamp timestamp
                                                             :ref-id ref-id
                                                             :patch (utils/read-form patch)})
                                      (update :patches-applied inc))))))

(defn add-tap-trace [db {:keys [tap-id tap-name value timestamp] :as trace}]
  (if-not (get-in db [:taps tap-id])
    (-> db
        (assoc-in [:taps tap-id] {:tap-id tap-id
                                  :tap-name tap-name
                                  :tap-trace-idx 0
                                  :tap-values [{:timestamp timestamp
                                                :tap-id tap-id
                                                :tap-name tap-name
                                                :tap-trace-idx 0
                                                :value value}]})
        (update :selected-tap-id #(or % tap-id)))
    (update-in db [:taps tap-id] (fn [tap]
                                   (-> tap
                                       (update :tap-values (fn [tv]
                                                             (conj tv {:timestamp timestamp
                                                                       :tap-id tap-id
                                                                       :value value
                                                                       :tap-trace-idx (count tv)}))))))))
