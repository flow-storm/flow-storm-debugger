(ns flow-storm.tracer
  (:require [flow-storm.utils :as utils]
            [flow-storm.runtime.values :refer [snapshot-reference]]
            [flow-storm.runtime.indexes.api :as indexes-api]))

(declare start-tracer)
(declare stop-tracer)

(defn trace-flow-init-trace

  "Send flow initialization trace"
  
  [flow-id form-ns form]
  (let [trace {:trace/type :flow-init
               :flow-id flow-id
               :ns form-ns
               :form form
               :timestamp (utils/get-monotonic-timestamp)}]
    (indexes-api/add-flow-init-trace trace)))

(defn trace-form-init

  "Send form initialization trace only once for each thread."
  
  [{:keys [form-id ns def-kind dispatch-val]} form {:keys [flow-id tracing-disabled?]}]
  
  (when-not tracing-disabled?
    (let [thread-id (utils/get-current-thread-id)]
      (when-not (indexes-api/get-form flow-id thread-id form-id)
        (let [trace {:trace/type :form-init
                     :flow-id flow-id
                     :form-id form-id
                     :thread-id thread-id
                     :form form
                     :ns ns
                     :def-kind def-kind
                     :mm-dispatch-val dispatch-val
                     :timestamp (utils/get-monotonic-timestamp)}]

          (indexes-api/add-form-init-trace trace))))))

(defn trace-expr-exec
  
  "Send expression execution trace."
  
  [result {:keys [coor outer-form? form-id]} {:keys [flow-id tracing-disabled? thread-trace-limit]}]
  (when-not tracing-disabled?
    (let [thread-id (utils/get-current-thread-id)
          trace {:trace/type :expr-exec
                 :flow-id flow-id
                 :form-id form-id
                 :coor coor
                 :thread-id thread-id
                 :timestamp (utils/get-monotonic-timestamp)
                 :result (snapshot-reference result)
                 :outer-form? outer-form?}]
      (when thread-trace-limit
        (when (> (indexes-api/timeline-count flow-id thread-id) thread-trace-limit)
          (throw (ex-info "thread-trace-limit exceeded" {}))))
      (indexes-api/add-expr-exec-trace trace)))
  
  result)

(defn trace-fn-call

  "Send function call traces"
  
  [form-id ns fn-name args-vec {:keys [flow-id tracing-disabled?]}]
  (when-not tracing-disabled?
    (let [trace {:trace/type :fn-call
                 :flow-id flow-id
                 :form-id form-id
                 :fn-name fn-name
                 :fn-ns ns
                 :thread-id (utils/get-current-thread-id)
                 :args-vec  (mapv snapshot-reference args-vec)
                 :timestamp (utils/get-monotonic-timestamp)}]
      (indexes-api/add-fn-call-trace trace))))

(defn trace-bind
  
  "Send bind trace."
  
  [symb val {:keys [coor]} {:keys [flow-id tracing-disabled?]}]
  (when-not tracing-disabled?
    (let [trace {:trace/type :bind
                 :flow-id flow-id                                               
                 :coor (or coor [])
                 :thread-id (utils/get-current-thread-id)
                 :timestamp (utils/get-monotonic-timestamp)
                 :symbol (name symb)
                 :value (snapshot-reference val)}]
      (indexes-api/add-bind-trace trace))))
