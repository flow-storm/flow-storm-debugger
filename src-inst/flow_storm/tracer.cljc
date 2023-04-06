(ns flow-storm.tracer
  (:require [flow-storm.utils :as utils]
            [hansel.instrument.runtime :refer [*runtime-ctx*]]
            [flow-storm.runtime.values :refer [snapshot-reference]]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.types.fn-call-trace :refer [make-fn-call-trace]]
            [flow-storm.runtime.types.fn-return-trace :refer [make-fn-return-trace]]
            [flow-storm.runtime.types.expr-trace :refer [make-expr-trace]]
            [flow-storm.runtime.types.bind-trace :refer [make-bind-trace]]))

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
  
  ([{:keys [form-id ns fn-name fn-args]}] ;; for using with hansel
   
   (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
     (when-not tracing-disabled?
       (trace-fn-call flow-id ns fn-name fn-args form-id))))
  
  ([flow-id fn-ns fn-name fn-args form-id]  ;; for using with storm
   
   (let [timestamp (utils/get-monotonic-timestamp)
         thread-id (utils/get-current-thread-id)
         thread-name (utils/get-current-thread-name)
         args (mapv snapshot-reference fn-args)]
     (indexes-api/add-fn-call-trace
      flow-id
      thread-id
      thread-name
      (make-fn-call-trace fn-ns fn-name form-id timestamp args)))))

(defn trace-fn-return

  "Send function return traces"
  
  ([{:keys [return coor form-id]}]  ;; for using with hansel
   
   (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
     (when-not tracing-disabled?
       (trace-fn-return flow-id return coor form-id))
     return))
  
  ([flow-id return coord form-id] ;; for using with storm

   (let [timestamp (utils/get-monotonic-timestamp)
         thread-id (utils/get-current-thread-id)]
     (indexes-api/add-fn-return-trace
      flow-id
      thread-id      
      (make-fn-return-trace form-id timestamp coord (snapshot-reference return))))))

(defn trace-expr-exec
  
  "Send expression execution trace."
  
  ([{:keys [result coor form-id]}]  ;; for using with hansel
   
   (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
     (when-not tracing-disabled?
       (trace-expr-exec flow-id result coor form-id)))
   
   result)

  ([flow-id result coord form-id]  ;; for using with storm
   
   (let [timestamp (utils/get-monotonic-timestamp)
         thread-id (utils/get-current-thread-id)]
     (indexes-api/add-expr-exec-trace      
      flow-id
      thread-id      
      (make-expr-trace form-id timestamp coord (snapshot-reference result))))))

(defn trace-bind
  
  "Send bind trace."
  
  ([{:keys [symb val coor]}] ;; for using with hansel

   (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
     (when-not tracing-disabled?
       (trace-bind flow-id coor (name symb) (snapshot-reference val)))))

  ([flow-id coord sym-name val]  ;; for using with storm
   
   (let [timestamp (utils/get-monotonic-timestamp)
         thread-id (utils/get-current-thread-id)]
     (indexes-api/add-bind-trace      
      flow-id
      thread-id      
      (make-bind-trace timestamp sym-name val coord)))))

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
