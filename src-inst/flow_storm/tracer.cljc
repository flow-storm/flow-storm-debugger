(ns flow-storm.tracer
  (:require [flow-storm.utils :refer [log] :as utils]
            [flow-storm.trace-types :as trace-types]
            [clojure.core.async :as async]))

(def orphan-flow-id -1)

(def trace-chan nil)
(def send-thread-stop-chan nil)

(def *stats (atom {}))

(def init-stats {:put 0
                 :sent 0
                 :last-report-t (utils/get-monotonic-timestamp)
                 :last-report-sent 0})

(defn enqueue-trace! [trace]
  (swap! *stats update :put inc)
  (async/put! trace-chan trace))

(def ^:dynamic *runtime-ctx* nil)

(defn build-runtime-ctx [{:keys [flow-id tracing-disabled?]}]
  {:flow-id flow-id
   :tracing-disabled? tracing-disabled?
   :init-traced-forms (atom #{})})

(defn trace-flow-init-trace

  "Send flow initialization trace"
  
  [flow-id form-ns form]
  (let [trace (trace-types/map->FlowInitTrace {:flow-id flow-id
                                               :form-ns form-ns
                                               :form form
                                               :timestamp (utils/get-monotonic-timestamp)})]
    (enqueue-trace! trace)))

(defn trace-form-init

  "Send form initialization trace only once for each thread."
  
  [{:keys [form-id ns def-kind dispatch-val]} form]
  (let [{:keys [flow-id init-traced-forms]} *runtime-ctx*        
        thread-id (utils/get-current-thread-id)]
    (when-not (contains? @init-traced-forms [flow-id thread-id form-id])
      (let [trace (trace-types/map->FormInitTrace {:flow-id flow-id
                                                   :form-id form-id
                                                   :thread-id thread-id
                                                   :form form
                                                   :ns ns
                                                   :def-kind def-kind
                                                   :mm-dispatch-val dispatch-val
                                                   :timestamp (utils/get-monotonic-timestamp)})]
        (enqueue-trace! trace)
        (swap! init-traced-forms conj [flow-id thread-id form-id])))))

(defn- snapshot-reference [x]  
  (if #?(:clj  (instance? clojure.lang.IDeref x)
         :cljs (instance? cljs.core.IDeref x))
    {:ref/snapshot (deref x)
     :ref/type (type x)}
    x))

(defn trace-expr-exec
  
  "Send expression execution trace."
  
  [result {:keys [coor outer-form? form-id]}]
  (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [trace (trace-types/map->ExecTrace {:flow-id flow-id
                                               :form-id form-id
                                               :coor coor
                                               :thread-id (utils/get-current-thread-id)
                                               :timestamp (utils/get-monotonic-timestamp)
                                               :result (snapshot-reference result)
                                               :outer-form? outer-form?})]
        (enqueue-trace! trace)))
    
    result))

(defn trace-fn-call

  "Send function call traces"
  
  [form-id ns fn-name args-vec]
  (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [trace (trace-types/map->FnCallTrace {:flow-id flow-id
                                                 :form-id form-id
                                                 :fn-name fn-name
                                                 :fn-ns ns
                                                 :thread-id (utils/get-current-thread-id)
                                                 :args-vec  (mapv snapshot-reference args-vec)
                                                 :timestamp (utils/get-monotonic-timestamp)})]
        (enqueue-trace! trace)))))

(defn trace-bind
  
  "Send bind trace."
  
  [symb val {:keys [coor]}]
  (let [{:keys [flow-id tracing-disabled?]} *runtime-ctx*]
    (when-not tracing-disabled?
      (let [trace (trace-types/map->BindTrace {:flow-id flow-id                                               
                                               :coor (or coor [])
                                               :thread-id (utils/get-current-thread-id)
                                               :timestamp (utils/get-monotonic-timestamp)
                                               :symbol (name symb)
                                               :value (snapshot-reference val)})]
        (enqueue-trace! trace)))))

(defn log-stats []
  (let [{:keys [put sent last-report-sent last-report-t]} @*stats
        qsize (- put sent)]
    (log (utils/format "CNT: %d, Q_SIZE: %d, Speed: %.1f tps"
                       sent
                       qsize
                       (quot (- sent last-report-sent)
                             (/ (double (- (utils/get-monotonic-timestamp) last-report-t))
                                1000000000.0))))))

(defn start-trace-sender
   
   "Creates and starts a thread that read traces from the global `trace-queue`
  and send them using `send-fn`"
   
   [{:keys [send-fn verbose?]}]

  ;; Initialize global vars
  #?@(:clj [(alter-var-root #'trace-chan (constantly (async/chan 30000000)))
            (alter-var-root #'send-thread-stop-chan (constantly (async/promise-chan)))]
      :cljs [(set! trace-chan (async/chan 30000000))
             (set! send-thread-stop-chan (async/promise-chan))])
  
  (async/go    
    (reset! *stats init-stats)
    (loop []
      (let [[v ch] (async/alts! [trace-chan send-thread-stop-chan])]
        (when-not (= ch send-thread-stop-chan)
          (let [trace v]

            ;; Stats
            (let [{:keys [sent]} @*stats]
              (when (and verbose? (zero? (mod sent 50000)))
                (log-stats)
                (swap! *stats
                       (fn [{:keys [sent] :as stats}]
                         (assoc stats 
                                :last-report-t (utils/get-monotonic-timestamp)
                                :last-report-sent sent)))))
                                    
            (send-fn trace)
            
            (swap! *stats update :sent inc))
          (recur))))
    (log "Thread interrupted. Dying..."))
  
  nil)


(defn stop-trace-sender []  
  (when send-thread-stop-chan
    (async/put! send-thread-stop-chan true)))
