(ns flow-storm.runtime.events)

(def callback (atom nil))

(defn subscribe! [callback-fn]
  (reset! callback callback-fn))

(defn clear-subscription! []
  (reset! callback nil))

(defn make-flow-created-event [flow-id form-ns form timestamp]
  [:flow-created {:flow-id flow-id
                  :form-ns form-ns
                  :form (pr-str form)
                  :timestamp timestamp}])

(defn make-thread-created-event [flow-id thread-id]
  [:thread-created {:flow-id flow-id
                    :thread-id thread-id}])

(defn make-first-fn-call-event [flow-id thread-id form-id]
  [:first-fn-call {:flow-id flow-id
                   :thread-id thread-id
                   :form-id form-id}])

(defn make-var-instrumented-event [var-name var-ns]
  [:var-instrumented {:var-name var-name
                      :var-ns var-ns}])

(defn make-var-uninstrumented-event [var-name var-ns]
  [:var-uninstrumented {:var-name var-name
                        :var-ns var-ns}])

(defn make-ns-instrumented-event [ns-name]
  [:namespace-instrumented {:ns-name ns-name}])

(defn make-ns-uninstrumented-event [ns-name]
  [:namespace-uninstrumented {:ns-name ns-name}])

(defn make-tap-event [tap-val]
  [:tap {:value tap-val}])

(defn make-task-result-event [task-id result]
  [:task-result {:task-id task-id :result result}])

(defn make-task-progress-event [task-id progress]
  [:task-progress {:task-id task-id :progress progress}])

(defn enqueue-event! [ev]
  (when-let [cb @callback]
    (cb ev)))
