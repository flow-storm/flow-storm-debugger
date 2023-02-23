(ns flow-storm.tracer
  (:require [flow-storm.utils :as utils]
            [hansel.instrument.runtime :refer [*runtime-ctx*]]
            [flow-storm.runtime.values :refer [snapshot-reference]]
            [flow-storm.runtime.indexes.api :as indexes-api]))

(declare start-tracer)
(declare stop-tracer)

(defn trace-flow-init-trace

  "Send flow initialization trace"
  
  [{:keys [flow-id form-ns form]}]
  (let [trace {:trace/type :flow-init
               :flow-id flow-id
               :ns form-ns
               :form form
               :timestamp (utils/get-monotonic-timestamp)}]
    (indexes-api/add-flow-init-trace trace)))

(defn trace-form-init

  "Send form initialization trace only once for each thread."
  
  [{:keys [form-id ns def-kind dispatch-val form]}]
  
  (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [thread-id (utils/get-current-thread-id)]
        (when-not (indexes-api/get-form flow-id thread-id form-id)
          (let [trace {:trace/type :form-init
                       :flow-id flow-id
                       :form-id form-id
                       :thread-id thread-id
                       :thread-name (utils/get-current-thread-name)
                       :form form
                       :ns ns
                       :def-kind def-kind
                       :mm-dispatch-val dispatch-val
                       :timestamp (utils/get-monotonic-timestamp)}]

            (indexes-api/add-form-init-trace trace)))))))

(defn trace-fn-call

  "Send function call traces"
  
  [{:keys [form-id ns fn-name fn-args]}]
  (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [trace {:trace/type :fn-call
                   :flow-id flow-id
                   :form-id form-id
                   :fn-name fn-name
                   :fn-ns ns
                   :thread-id (utils/get-current-thread-id)
                   :thread-name (utils/get-current-thread-name)
                   :args-vec  (mapv snapshot-reference fn-args)
                   :timestamp (utils/get-monotonic-timestamp)}]
        (indexes-api/add-fn-call-trace trace)))))

(defn trace-fn-return

  "Send function return traces"
  
  [{:keys [return form-id]}]
  
  (let [{:keys [flow-id tracing-disabled? thread-trace-limit]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [thread-id (utils/get-current-thread-id)
            trace {:trace/type :expr-exec
                   :flow-id flow-id
                   :form-id form-id
                   :thread-id thread-id
                   :outer-form? true
                   :coor []
                   :timestamp (utils/get-monotonic-timestamp)
                   :result (snapshot-reference return)}]
        (when thread-trace-limit
          (when (> (indexes-api/timeline-count flow-id thread-id) thread-trace-limit)
            (throw (ex-info "thread-trace-limit exceeded" {}))))
        (indexes-api/add-expr-exec-trace trace)))
    return))

(defn trace-expr-exec
  
  "Send expression execution trace."
  
  [{:keys [result coor form-id]}]
  
  (let [{:keys [flow-id tracing-disabled? thread-trace-limit]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [thread-id (utils/get-current-thread-id)
            trace {:trace/type :expr-exec
                   :flow-id flow-id
                   :form-id form-id
                   :coor coor
                   :thread-id thread-id
                   :thread-name (utils/get-current-thread-name)
                   :timestamp (utils/get-monotonic-timestamp)
                   :result (snapshot-reference result)}]
        (when thread-trace-limit
          (when (> (indexes-api/timeline-count flow-id thread-id) thread-trace-limit)
            (throw (ex-info "thread-trace-limit exceeded" {}))))
        (indexes-api/add-expr-exec-trace trace))))
  
  result)

(defn trace-bind
  
  "Send bind trace."
  
  [{:keys [symb val coor]}]

  (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [trace {:trace/type :bind
                   :flow-id flow-id                                               
                   :coor (or coor [])
                   :thread-id (utils/get-current-thread-id)
                   :timestamp (utils/get-monotonic-timestamp)
                   :symbol (name symb)
                   :value (snapshot-reference val)}]
        (indexes-api/add-bind-trace trace)))))

(defn hansel-config

  "Builds a hansel config from inst-opts"
  
  [{:keys [disable] :or {disable #{}}}]
  (cond-> `{:trace-form-init trace-form-init
            :trace-fn-call trace-fn-call
            :trace-fn-return trace-fn-return
            :trace-expr-exec trace-expr-exec
            :trace-bind trace-bind}
    
    (disable :expr-exec)    (dissoc :trace-expr-exec)
    (disable :bind)         (dissoc :trace-expr-exec)
    (disable :anonymous-fn) (assoc :disable #{:anonymous-fn})))
