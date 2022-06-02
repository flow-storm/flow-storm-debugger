(ns flow-storm.tracer
  (:require [flow-storm.utils :refer [log] :as utils]
            [flow-storm.trace-types :as trace-types]
            [clojure.core.async :as async]))

(def orphan-flow-id -1)

(def trace-chan (async/chan 30000000))
(def send-thread-stop-chan (async/promise-chan))

(def *stats (atom {}))

(def init-stats {:put 0
                 :sent 0
                 :last-report-t (utils/get-monotonic-timestamp)
                 :last-report-sent 0})

(defn enqueue-trace! [trace]
  (swap! *stats update :put inc)
  (async/put! trace-chan trace))

(def ^:dynamic *runtime-ctx* nil)

(defn empty-runtime-ctx [flow-id]
  {:flow-id flow-id 
   :init-traced-forms (atom #{})})

(defn trace-flow-init-trace

  "Send flow initialization trace"
  
  [flow-id form-ns form]
  (let [trace (trace-types/map->FlowInitTrace {:flow-id flow-id
                                               :form-ns form-ns
                                               :form form
                                               :timestamp (utils/get-timestamp)})]    
    (enqueue-trace! trace)))

(defn trace-form-init-trace

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
                                                   :timestamp (utils/get-timestamp)})]
        (enqueue-trace! trace)
        (swap! init-traced-forms conj [flow-id thread-id form-id])))))

(defn trace-expr-exec-trace
  
  "Send expression execution trace."
  
  [result _ {:keys [coor outer-form? form-id]}]
  (let [{:keys [flow-id]} *runtime-ctx*
        trace (trace-types/map->ExecTrace {:flow-id flow-id
                                           :form-id form-id
                                           :coor coor
                                           :thread-id (utils/get-current-thread-id)
                                           :timestamp (utils/get-timestamp)
                                           :result result
                                           :outer-form? outer-form?})]
    (enqueue-trace! trace)
    result))

(defn trace-fn-call-trace

  "Send function call traces"
  
  [form-id ns fn-name args-vec]
  (let [{:keys [flow-id]} *runtime-ctx*
        trace (trace-types/map->FnCallTrace {:flow-id flow-id
                                             :form-id form-id
                                             :fn-name fn-name
                                             :fn-ns ns
                                             :thread-id (utils/get-current-thread-id)
                                             :args-vec args-vec
                                             :timestamp (utils/get-timestamp)})]
    (enqueue-trace! trace)))

(defn trace-bound-trace
  
  "Send bind trace."
  
  [symb val {:keys [coor form-id]}]
  (let [{:keys [flow-id]} *runtime-ctx*
        trace (trace-types/map->BindTrace {:flow-id flow-id
                                           :form-id form-id
                                           :coor (or coor [])
                                           :thread-id (utils/get-current-thread-id)
                                           :timestamp (utils/get-timestamp)
                                           :symbol (name symb)
                                           :value val})]
    (enqueue-trace! trace)))

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

  (async/go
    (reset! *stats init-stats)
    (loop []
      (let [[v ch] (async/alts! [trace-chan send-thread-stop-chan])]
        (when-not (= ch send-thread-stop-chan)
          (let [trace v]

            ;; Stats
            (when (zero? (mod (:put @*stats) 50000))                  
              (when verbose? (log-stats))
              
              (swap! *stats
                     (fn [{:keys [sent] :as stats}]
                       (assoc stats 
                              :last-report-t (utils/get-monotonic-timestamp)
                              :last-report-sent sent))))
            
            (swap! *stats update :sent inc)
            
            (send-fn trace))
          (recur))))
    (log "Thread interrupted. Dying..."))
  
  nil)


(defn stop-trace-sender []
  (async/put! send-thread-stop-chan true))
