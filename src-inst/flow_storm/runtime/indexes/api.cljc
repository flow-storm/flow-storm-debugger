(ns flow-storm.runtime.indexes.api
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.timeline-index :as timeline-index]            
            [flow-storm.runtime.indexes.fn-call-stats-index :as fn-call-stats-index]
            [flow-storm.runtime.events :as events]            
            [flow-storm.runtime.indexes.thread-registry :as thread-registry]
            [flow-storm.runtime.indexes.form-registry :as form-registry]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]            
            [clojure.pprint :as pp]
            [flow-storm.utils :as utils]))

(declare discard-flow)
(declare get-thread-indexes)

(def flow-thread-registry nil)
(def forms-registry nil)
(def last-exception-location (atom nil))

(defn get-last-exception-location []
  @last-exception-location)

(defn register-form [form-data]
  (if forms-registry
    
    (index-protos/register-form forms-registry (:form/id form-data) form-data)

    (utils/log "Warning, trying to register a form before FlowStorm startup. If you have #trace tags on your code you will have to evaluate them again after starting the debugger.")))

(defn handle-exception [thread ex]
  
  #?(:clj
     (when-not (instance? java.lang.InterruptedException ex)
       (utils/log-error (utils/format "Error in thread %s" thread) ex))
     :cljs (utils/log-error (utils/format "Error in thread %s" thread) ex))
  
  (let [thread-id #?(:clj (.getId ^Thread thread) :cljs 0)
        thread-name  #?(:clj (.getName ^Thread thread) :cljs "main")
        {:keys [timeline-index]} (get-thread-indexes nil thread-id)]
    
    (when timeline-index
      (index-protos/reset-build-stack timeline-index)
      
      (reset! last-exception-location {:thread/id thread-id
                                       :thread/name thread-name
                                       :thread/idx (dec (index-protos/timeline-count timeline-index))}))))

(defn create-flow [{:keys [flow-id ns form timestamp]}]
  (discard-flow flow-id)  
  (events/publish-event! (events/make-flow-created-event flow-id ns form timestamp)))

#?(:clj
   (defn set-storm-fns

     "Set ClojureStorm callbacks by reflection so FlowStorm can be used
  without ClojureStorm on the classpath."  
     
     [callbacks]     
     (let [tracer-class (Class/forName "clojure.storm.Tracer")
           setTraceFnsCallbacks (.getMethod tracer-class "setTraceFnsCallbacks" (into-array java.lang.Class [clojure.lang.IPersistentMap]))]       
       (.invoke setTraceFnsCallbacks nil (into-array [callbacks])))))

#?(:clj
   (defn start []
     (alter-var-root #'flow-thread-registry (fn [_]
                                              (when (utils/storm-env?)
                                                (set-storm-fns {:trace-fn-call-fn-key (requiring-resolve 'flow-storm.tracer/trace-fn-call)
	                                                            :trace-fn-return-fn-key (requiring-resolve 'flow-storm.tracer/trace-fn-return)
	                                                            :trace-expr-fn-key (requiring-resolve 'flow-storm.tracer/trace-expr-exec)
	                                                            :trace-bind-fn-key (requiring-resolve 'flow-storm.tracer/trace-bind)
	                                                            :handle-exception-fn-key handle-exception})                                                
                                                (utils/log "Storm functions plugged in"))
                                              (let [registry (index-protos/start-thread-registry
                                                              (thread-registry/make-thread-registry)
                                                              {:on-thread-created (fn [{:keys [flow-id]}]                                                                    
                                                                                    (events/publish-event!
                                                                                     (events/make-threads-updated-event flow-id)))})]                                                
                                                registry)))
     
     (alter-var-root #'forms-registry (fn [_]                                       
                                       (index-protos/start-form-registry
                                        (if (utils/storm-env?)
                                          ((requiring-resolve 'flow-storm.runtime.indexes.storm-form-registry/make-storm-form-registry))
                                          (form-registry/make-form-registry)))))
     (utils/log "Runtime index system started"))
   :cljs
   (defn start []
     (set! flow-thread-registry (index-protos/start-thread-registry
                                 (thread-registry/make-thread-registry)
                                 {:on-thread-created (fn [{:keys [flow-id]}]
                                                       (events/publish-event!
                                                        (events/make-threads-updated-event flow-id)))}))     
     (set! forms-registry (index-protos/start-form-registry
                           (form-registry/make-form-registry)))
     (utils/log "Runtime index system started")))

#?(:clj
   (defn stop []
     (when (utils/storm-env?)
       (set-storm-fns {:trace-fn-call-fn-key nil
	                   :trace-fn-return-fn-key nil
	                   :trace-expr-fn-key nil
	                   :trace-bind-fn-key nil
	                   :handle-exception-fn-key nil})
       (utils/log "Storm functions unplugged"))     
     (alter-var-root #'flow-thread-registry index-protos/stop-thread-registry)
     (alter-var-root #'forms-registry index-protos/stop-form-registry)
     (utils/log "Runtime index system stopped"))
   
   :cljs
   (defn stop []
     (set! flow-thread-registry index-protos/stop-thread-registry)
     (set! forms-registry index-protos/stop-form-registry)
     (utils/log "Runtime index system stopped")))

(defn flow-exists? [flow-id]  
  (when flow-thread-registry
    (index-protos/flow-exists? flow-thread-registry flow-id)))

(defn create-thread-indexes! [flow-id thread-id thread-name form-id]
  (let [thread-indexes {:timeline-index (timeline-index/make-index)
                        :fn-call-stats-index (fn-call-stats-index/make-index)}]    

    (index-protos/register-thread-indexes flow-thread-registry flow-id thread-id thread-name form-id thread-indexes)
    
    thread-indexes))

(defn get-thread-indexes [flow-id thread-id]  
  (when flow-thread-registry
    (index-protos/get-thread-indexes flow-thread-registry flow-id thread-id)))

(defn get-or-create-thread-indexes [flow-id thread-id thread-name form-id]
  ;; create the `nil` (funnel) flow if it doesn't exist
   (when (and (nil? flow-id) (not (flow-exists? nil)))
     (create-flow {:flow-id nil}))
  
  (if-let [ti (get-thread-indexes flow-id thread-id)]
    ti
    (create-thread-indexes! flow-id thread-id thread-name form-id)))

;;;;;;;;;;;;;;;;;;;;;;;
;; Indexes Build API ;;
;;;;;;;;;;;;;;;;;;;;;;;

(defn add-flow-init-trace [trace]
  (create-flow trace))

(defn add-form-init-trace [trace]
  (register-form trace))

(defn add-fn-call-trace [flow-id thread-id thread-name trace]
  (let [{:keys [timeline-index fn-call-stats-index]} (get-or-create-thread-indexes flow-id thread-id thread-name (fn-call-trace/get-form-id trace))]
    
    (when timeline-index
      (index-protos/add-fn-call timeline-index trace))
    
    (when fn-call-stats-index
      (index-protos/add-fn-call fn-call-stats-index trace))))

(defn add-fn-return-trace [flow-id thread-id trace]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]    
    (when timeline-index
      (index-protos/add-fn-return timeline-index trace))))

(defn add-expr-exec-trace [flow-id thread-id trace]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (when timeline-index
      (index-protos/add-expr-exec timeline-index trace))))

(defn add-bind-trace [flow-id thread-id trace]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (when timeline-index
      (index-protos/add-bind timeline-index trace))))

;;;;;;;;;;;;;;;;;
;; Indexes API ;;
;;;;;;;;;;;;;;;;;

(defn get-form [_ _ form-id]
  (when forms-registry
    (index-protos/get-form forms-registry form-id)))

(defn all-threads []
  (index-protos/all-threads flow-thread-registry))

(defn all-forms [_ _]
  (index-protos/all-forms forms-registry))

(defn timeline-count [flow-id thread-id]
  (when-let [timeline-index (:timeline-index (get-thread-indexes flow-id thread-id))]
    (index-protos/timeline-count timeline-index)))

(defn timeline-entry [flow-id thread-id idx drift]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (when timeline-index
      (index-protos/timeline-entry timeline-index idx drift))))

(defn frame-data [flow-id thread-id idx opts]  
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (index-protos/tree-frame-data timeline-index idx opts)))

(defn- coord-in-scope? [scope-coord current-coord]
  (if (empty? scope-coord)
    true
    (and (every? true? (map = scope-coord current-coord))
         (> (count current-coord) (count scope-coord)))))

(defn bindings [flow-id thread-id idx {:keys [all-frame?]}]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)
        {:keys [fn-call-idx] :as entry} (index-protos/timeline-entry timeline-index idx :at)
        frame-data (index-protos/tree-frame-data timeline-index fn-call-idx {:include-binds? true})
        [entry-coord entry-idx] (case (:type entry)
                                  :fn-call   [[] fn-call-idx]
                                  :fn-return [(:coord entry) (:idx entry)]
                                  :expr      [(:coord entry) (:idx entry)])]
    
    (cond->> (:bindings frame-data)
      (not all-frame?) (filter (fn [bind]
                                 (and (coord-in-scope? (:coord bind) entry-coord)
                                      (>= entry-idx (:visible-after bind)))))
      true             (map (fn [bind]
                              [(:symbol bind) (:value bind)]))
      true             (into {}))))

(defn callstack-root-node [flow-id thread-id]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)
        idx (index-protos/tree-root-index timeline-index)
        node [flow-id thread-id idx]]
    node))

(defn stack-for-frame [flow-id thread-id fn-call-idx]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)
        {:keys [fn-call-idx-path]} (index-protos/tree-frame-data timeline-index fn-call-idx {:include-path? true})]
    (reduce (fn [stack fidx]
              (conj stack (select-keys (index-protos/tree-frame-data timeline-index fidx {})
                                       [:fn-ns :fn-name :fn-call-idx])))
            []
            fn-call-idx-path)))

(defn callstack-node-childs [[flow-id thread-id idx]]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (->> (index-protos/tree-childs-indexes timeline-index idx)
         (mapv (fn [idx]
                 [flow-id thread-id idx])))))

(defn callstack-node-frame-data [[flow-id thread-id idx]]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (index-protos/tree-frame-data timeline-index idx {})))

(defn reset-all-threads-trees-build-stack [flow-id]
  (when flow-thread-registry
    (let [flow-threads (index-protos/flow-threads-info flow-thread-registry flow-id)]
      (doseq [{:keys [thread/id]} flow-threads]
        (let [{:keys [fn-call-idx]} (get-thread-indexes flow-id id)]
          (when fn-call-idx
            (index-protos/reset-build-stack fn-call-idx)))))))

(defn fn-call-stats [flow-id thread-id]
  (let [{:keys [fn-call-stats-index]} (get-thread-indexes flow-id thread-id)]
    (->> (index-protos/all-stats fn-call-stats-index)
         (keep (fn [[fn-call cnt]]
                 (let [form (get-form flow-id thread-id (:form-id fn-call))]
                   (cond-> {:fn-ns (:fn-ns fn-call)
                            :fn-name (:fn-name fn-call)
                            :form-id (:form-id fn-call)
                            :form (:form/form form)
                            :form-def-kind (:form/def-kind form)
                            :dispatch-val (:multimethod/dispatch-val form)
                            :cnt cnt}
                     (:multimethod/dispatch-val form) (assoc :dispatch-val (:multimethod/dispatch-val form)))))))))

(defn all-frames [flow-id thread-id pred]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (index-protos/timeline-frames timeline-index nil nil pred)))

(defn find-fn-frames [flow-id thread-id fn-ns fn-name form-id]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (index-protos/timeline-frames timeline-index
                             nil
                             nil
                             (fn [fns fname formid _]
                               (and (= form-id formid)
                                    (= fn-name fname)
                                    (= fn-ns fns))))))


(defn discard-flow [flow-id] 
  (let [discard-keys (some->> flow-thread-registry
                              index-protos/all-threads 
                              (filter (fn [[fid _]] (= fid flow-id))))]
    
    (when flow-thread-registry
      (index-protos/discard-threads flow-thread-registry discard-keys))))

#?(:cljs (defn flow-threads-info [flow-id] [{:flow/id flow-id :thread/id 0 :thread/name "main" :thread/blocked? false}])
   :clj (defn flow-threads-info [flow-id]          
          (when flow-thread-registry
            (index-protos/flow-threads-info flow-thread-registry flow-id))))

(defn mark-thread-blocked [flow-id thread-id breakpoint]
  (when flow-thread-registry
    (index-protos/set-thread-blocked flow-thread-registry flow-id thread-id breakpoint)
    (events/publish-event! (events/make-threads-updated-event flow-id))))

(defn mark-thread-unblocked [flow-id thread-id]
  (when flow-thread-registry
    (index-protos/set-thread-blocked flow-thread-registry flow-id thread-id nil)
    (events/publish-event! (events/make-threads-updated-event flow-id))))

(defn find-fn-call [fq-fn-call-symb from-idx {:keys [from-back?]}]  
  (some (fn [[flow-id thread-id]]
          (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
            (when-let [fn-call (index-protos/timeline-find-entry
                                timeline-index
                                from-idx
                                from-back?
                                (fn [entry]
                                  (and (fn-call-trace/fn-call-trace? entry)
                                       (= (fn-call-trace/get-fn-ns entry)   (namespace fq-fn-call-symb))
                                       (= (fn-call-trace/get-fn-name entry) (name fq-fn-call-symb)))))]
              (assoc fn-call
                     :flow-id flow-id
                     :thread-id thread-id))))
        (index-protos/all-threads flow-thread-registry)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for exploring indexes from the repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def selected-thread (atom nil))

(defn print-threads []
  (->> (all-threads)
       (map #(zipmap [:flow-id :thread-id :thread-name] %))
       pp/print-table))

(defn select-thread [flow-id thread-id]
  (reset! selected-thread [flow-id thread-id]))

(defn print-forms []  
  (let [[flow-id thread-id] @selected-thread]
    (->> (all-forms flow-id thread-id)
         (map #(dissoc % :form/flow-id ))
         pp/print-table)))
