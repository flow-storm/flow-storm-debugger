(ns flow-storm.tracer
  (:require [flow-storm.utils :refer [log log-error]]
            [flow-storm.trace-types :as trace-types])    
  #?(:clj (:import [java.util.concurrent ArrayBlockingQueue])))

(def trace-queue nil)

(defn enqueue-trace! [trace]
  (.put trace-queue trace))

(defonce ^Thread send-thread nil)

(def ^:dynamic *runtime-ctx* nil)

(defn empty-runtime-ctx [flow-id]
  {:flow-id flow-id 
   :init-traced-forms (atom #{})})

(defn get-timestamp []
  #?(:cljs (.getTime (js/Date.))
     :clj (System/currentTimeMillis)))

(defn trace-flow-init-trace

  "Send flow initialization trace"
  
  [flow-id form-ns form]
  (let [trace (trace-types/map->FlowInitTrace {:flow-id flow-id
                                               :form-ns form-ns
                                               :form form
                                               :timestamp (get-timestamp)})]    
    (enqueue-trace! trace)))

(defn get-current-thread-id []
  #?(:clj (.getId (Thread/currentThread))
     :cljs 0))

(defn trace-form-init-trace

  "Send form initialization trace only once for each thread."
  
  [{:keys [form-id ns def-kind dispatch-val]} form]
  (let [{:keys [flow-id init-traced-forms]} *runtime-ctx*        
        thread-id (get-current-thread-id)]
    (when-not (contains? @init-traced-forms [flow-id thread-id form-id])
      (let [trace (trace-types/map->FormInitTrace {:flow-id flow-id
                                                   :form-id form-id
                                                   :thread-id thread-id
                                                   :form form
                                                   :ns ns
                                                   :def-kind def-kind
                                                   :mm-dispatch-val dispatch-val
                                                   :timestamp (get-timestamp)})]
        (enqueue-trace! trace)
        (swap! init-traced-forms conj [flow-id thread-id form-id])))))

(defn trace-expr-exec-trace
  
  "Send expression execution trace."
  
  [result _ {:keys [coor outer-form? form-id]}]
  (let [{:keys [flow-id]} *runtime-ctx*
        trace (trace-types/map->ExecTrace {:flow-id flow-id
                                           :form-id form-id
                                           :coor coor
                                           :thread-id (get-current-thread-id)
                                           :timestamp (get-timestamp)
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
                                             :thread-id (get-current-thread-id)
                                             :args-vec args-vec
                                             :timestamp (get-timestamp)})]
    (enqueue-trace! trace)))

(defn trace-bound-trace
  
  "Send bind trace."
  
  [symb val {:keys [coor form-id]}]
  (let [{:keys [flow-id]} *runtime-ctx*
        trace (trace-types/map->BindTrace {:flow-id flow-id
                                           :form-id form-id
                                           :coor (or coor [])
                                           :thread-id (get-current-thread-id)
                                           :timestamp (get-timestamp)
                                           :symbol (name symb)
                                           :value val})]
    (enqueue-trace! trace)))

;; (defn ref-init-trace
;;   "Sends the `:ref-init-trace` trace"
;;   [ref-id ref-name init-val]
;;   (let [trace-data {:ref-id ref-id
;;                     :ref-name ref-name
;;                     :init-val (serialize-val init-val)
;;                     :timestamp (get-timestamp)}]
;;     (traceit [:ref-init-trace trace-data])))

;; (defn ref-trace
;;   "Sends the `:ref-trace` trace"
;;   [ref-id patch]
;;   (let [trace-data {:ref-id ref-id
;;                     :patch (pr-str patch)
;;                     :timestamp (get-timestamp)}]
;;     (traceit [:ref-trace trace-data])))

;; (defn trace-ref [ref {:keys [ref-name ignore-keys]}]
;;   (let [ref-id (hash ref)
;;         rm-ignored-keys (fn [v]
;;                           (if (and (seq ignore-keys) (map? v))
;;                             (apply (partial dissoc v) ignore-keys)
;;                             v))
;;         ref-init-val (-> @ref
;;                          rm-ignored-keys)]

;;     (ref-init-trace ref-id ref-name ref-init-val)

;;     (add-watch ref :flow-storm
;;                (fn [_ _ old-value new-value]
;;                  (let [patch (-> (edit.core/diff (rm-ignored-keys old-value)
;;                                                  (rm-ignored-keys new-value))
;;                                  edit.edit/get-edits)]
;;                    (when (seq patch)
;;                      (ref-trace ref-id patch)))))))

;; (defn untrace-ref [ref]
;;   (remove-watch ref :flow-storm))

;; (defn trace-tap [tap-id tap-name v]
;;   (let [trace-data {:tap-id tap-id
;;                     :tap-name tap-name
;;                     :value (serialize-val v)
;;                     :timestamp (get-timestamp)}]
;;     (traceit [:tap-trace trace-data])))

;; (defn init-tap
;;   ([] (let [rnd-id (rand-int 100000)] (init-tap rnd-id (str rnd-id))))
;;   ([tap-name] (init-tap (rand-int 100000) tap-name))
;;   ([tap-id tap-name]
;;    ;; we resolve add-tap like this so flow-storm can be used in older versions of clojure
;;    (when-let [add-tap-fn (resolve 'clojure.core/add-tap)]
;;     (add-tap-fn (fn [v]
;;                   (trace-tap tap-id tap-name v))))))

;; #?(:clj
;;    (defn build-ws-sender [{:keys [host port]}]
;;      host port
;;      (let [wsc (proxy
;;                    [WebSocketClient]
;;                    [(URI. "ws://localhost:7722/ws")]
;;                  (onOpen [^ServerHandshake handshake-data]
;;                    (println "Connection opened"))
;;                  (onMessage [^String message])
;;                  (onClose [code reason remote?]
;;                    (println "Connection closed" [code reason remote?]))
;;                  (onError [^Exception e]
;;                    (println "WS ERROR" e)))]

;;        (.setConnectionLostTimeout wsc 0)
;;        (.connect wsc)

;;        {:send-fn (fn [trace]
;;                    (let [trace-json-str (json/write-value-as-string trace)]                                                                  
;;                      (.send wsc trace-json-str)))
;;         :ws-client wsc})))

;; (defn build-file-sender [{:keys [file-path]}]
;;   (let [file-dos (DataOutputStream. (FileOutputStream. ^String file-path))
;;         ;; _bos (ByteArrayOutputStream.)
;;         ;; file-dos (when to-file (DataOutputStream. bos))
;;         ]
;;     {:send-fn (fn [trace]
;;                 (bin-serializer/serialize-trace file-dos trace))
;;      :file-output-stream file-dos}))

#?(:clj
   (defn log-stats [cnt qsize last-report-cnt last-report-t]  
     (log (format "CNT: %d, Q_SIZE: %d, Speed: %.1f tps"
                   cnt
                   qsize
                   (quot (- cnt last-report-cnt)
                         (/ (double (- (System/nanoTime) last-report-t))
                            1000000000.0)))))
   
   :cljs
   (defn log-stats [_ _ _ _]  
     (log "Not implemented")
     (log-error "Not implemented")))

#?(:clj
   (defn start-trace-sender
     
     "Creates and starts a thread that read traces from the global `trace-queue`
  and send them using `send-fn`"
     
     [{:keys [send-fn verbose?]}]

     ;; Stop the previous running `send-thread` if we have one
     (when send-thread (.interrupt send-thread))
     
     (let [ _ (alter-var-root #'trace-queue (constantly (ArrayBlockingQueue. 30000000)))
           *consumer-stats (atom {:cnt 0 :last-report-t (System/nanoTime) :last-report-cnt 0})
           
           send-thread (Thread.
                        (fn []
                          
                          (while (not (.isInterrupted (Thread/currentThread)))                          
                            (try
                              (let [trace (.take trace-queue)                                
                                    qsize (.size trace-queue)]

                                ;; Consumer stats
                                (let [{:keys [cnt last-report-t last-report-cnt]} @*consumer-stats]
                                  
                                  (when (zero? (mod cnt 1000))
                                    
                                    (when verbose?
                                      (log-stats cnt qsize last-report-cnt last-report-t))
                                    
                                    (swap! *consumer-stats
                                           assoc
                                           :last-report-t (System/nanoTime)
                                           :last-report-cnt cnt))
                                  
                                  (swap! *consumer-stats update :cnt inc))
                                
                                (send-fn trace))
                              (catch java.lang.InterruptedException _ nil)
                              (catch java.lang.IllegalMonitorStateException _ nil)
                              (catch Exception e
                                (log-error "SendThread consumer exception" e))))
                          (log "Thread interrupted. Dying...")))]
       (alter-var-root #'send-thread (constantly send-thread))
       (.start send-thread)
       
       nil)))


(defn stop-trace-sender []
  (when send-thread (.interrupt send-thread)))
