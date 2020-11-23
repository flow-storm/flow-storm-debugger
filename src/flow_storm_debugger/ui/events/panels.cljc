(ns flow-storm-debugger.ui.events.panels)

(def tool->idx {:flows    0
                :refs     1
                :taps     2
                :timeline 3})

(defn focus-thing [db thing]
  (case (:thing/type thing)
    :flow (let [{:keys [flow-id trace-idx]} thing]
            (-> db
                (assoc :selected-tool-idx (tool->idx :flows))
                (assoc :selected-flow-id flow-id)
                (assoc-in [:flows flow-id :trace-idx] trace-idx)))
    :ref  (let [{:keys [ref-id patch-idx]} thing]
            (let [db' (-> db
                          (assoc :selected-tool-idx (tool->idx :refs))
                          (assoc :selected-ref-id ref-id))]
              (if patch-idx
                (assoc-in db' [:refs ref-id :patches-applied] (inc patch-idx))
                (assoc-in db' [:refs ref-id :patches-applied] 0))))    
    :tap  (let [{:keys [tap-id tap-trace-idx]} thing]
            (-> db
                (assoc :selected-tool-idx (tool->idx :taps))
                (assoc :selected-tap-id tap-id)
                (assoc-in [:taps tap-id :tap-trace-idx] tap-trace-idx)))))
