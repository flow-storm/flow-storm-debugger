(ns flow-storm.runtime.events)

(defonce *dispatch (atom nil))
(defonce pending-events (atom []))

(defn clear-dispatch-fn! []
  (reset! *dispatch nil))

(defn clear-pending-events! []
  (reset! pending-events []))

(defn set-dispatch-fn [dispatch-fn]
  (reset! *dispatch dispatch-fn)
  (locking pending-events
    (doseq [pe @pending-events]
      (dispatch-fn pe))))

(defn make-flow-created-event [flow-id form-ns form timestamp]
  [:flow-created {:flow-id flow-id
                  :form-ns form-ns
                  :form (pr-str form)
                  :timestamp timestamp}])

(defn make-flow-discarded-event [flow-id]
  [:flow-discarded {:flow-id flow-id}])

(defn make-threads-updated-event [flow-id flow-threads-info]
  [:threads-updated {:flow-id flow-id :flow-threads-info flow-threads-info}])

(defn make-timeline-updated-event [flow-id thread-id]
  [:timeline-updated {:flow-id flow-id :thread-id thread-id}])

(defn make-storm-instrumentation-updated-event [inst-data]
  [:storm-instrumentation-updated-event inst-data])

(defn make-vanilla-ns-instrumented-event [ns-name]
  [:vanilla-namespace-instrumented {:ns-name ns-name}])

(defn make-vanilla-ns-uninstrumented-event [ns-name]
  [:vanilla-namespace-uninstrumented {:ns-name ns-name}])

(defn make-vanilla-var-instrumented-event [var-name var-ns]
  [:vanilla-var-instrumented {:var-name var-name
                              :var-ns var-ns}])

(defn make-vanilla-var-uninstrumented-event [var-name var-ns]
  [:vanilla-var-uninstrumented {:var-name var-name
                                :var-ns var-ns}])

(defn make-tap-event [tap-val]
  [:tap {:value tap-val}])

(defn make-task-submitted-event [task-id]
  [:task-submitted {:task-id task-id}])

(defn make-task-progress-event [task-id task-data]
  [:task-progress (assoc task-data :task-id task-id)])

(defn make-task-finished-event
  ([task-id]
   (make-task-finished-event task-id nil))
  ([task-id result]
   [:task-finished (cond-> {:task-id task-id}
                     result (assoc :result result))]))

(defn make-task-failed-event [task-id message]
  [:task-failed {:task-id task-id
                 :message message}])

(defn make-heap-info-update-event [heap-info]
  [:heap-info-update heap-info])

(defn make-goto-location-event [flow-id thread-id idx]
  [:goto-location {:flow-id flow-id :thread-id thread-id :idx idx}])

(defn make-break-installed-event [fq-fn-symb]
  [:break-installed {:fq-fn-symb fq-fn-symb}])

(defn make-break-removed-event [fq-fn-symb]
  [:break-removed {:fq-fn-symb fq-fn-symb}])

(defn make-recording-updated-event [recording?]
  [:recording-updated {:recording? recording?}])

(defn make-multi-timeline-recording-updated-event [recording?]
  [:multi-timeline-recording-updated {:recording? recording?}])

(defn make-function-unwinded-event [ev-data]
  [:function-unwinded-event ev-data])

(defn make-expression-bookmark-event [ev-data]
  [:expression-bookmark-event ev-data])

(defn show-doc-event [vsymb]
  [:show-doc {:var-symbol vsymb}])

(defn make-data-window-push-val-data-event [dw-id vdata extras]
  [:data-window-push-val-data {:dw-id dw-id
                               :val-data vdata
                               :extras extras}])

(defn make-data-window-update-event [dw-id data]
  [:data-window-update {:dw-id dw-id :data data}])

(defn make-out-write-event [s]
  [:out-write {:msg s}])

(defn make-err-write-event [s]
  [:err-write {:msg s}])

(defn make-last-evals-update-event [last-evals-refs]
  [:last-evals-update {:last-evals-refs last-evals-refs}])

(defn publish-event! [[ev-key :as ev]]
  (if-let [dispatch @*dispatch]

    (dispatch ev)

    (when-not (#{:heap-info-update} ev-key )
      (locking pending-events
        (swap! pending-events conj ev)))))
