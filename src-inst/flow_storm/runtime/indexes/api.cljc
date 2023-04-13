(ns flow-storm.runtime.indexes.api
  (:require [flow-storm.runtime.indexes.protocols :as indexes]
            [flow-storm.runtime.indexes.frame-index :as frame-index]            
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

(defn register-form [{:keys [flow-id form-id ns form def-kind mm-dispatch-val]}]
  (let [form-data (cond-> {:form/id form-id
                           :form/flow-id flow-id
                           :form/ns ns
                           :form/form form
                           :form/def-kind def-kind}
                    (= def-kind :defmethod)
                    (assoc :multimethod/dispatch-val mm-dispatch-val))]
    (indexes/register-form forms-registry form-id form-data)))

(defn handle-exception [^Thread thread _]  
  (let [thread-id (.getId thread)
        {:keys [frame-index]} (get-thread-indexes nil thread-id)]
    
    (when frame-index
      (indexes/reset-build-stack frame-index)
      
      (reset! last-exception-location {:thread/id thread-id
                                       :thread/name (.getName thread)
                                       :thread/idx (dec (indexes/timeline-count frame-index))}))))

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
                                              (let [registry (indexes/start-thread-registry
                                                              (thread-registry/make-thread-registry)
                                                              {:on-thread-created (fn [{:keys [flow-id thread-id thread-name form-id]}]                                                                    
                                                                                    (events/publish-event!
                                                                                     (events/make-thread-created-event flow-id thread-id thread-name form-id)))})]                                                
                                                registry)))
     
     (alter-var-root #'forms-registry (fn [_]                                       
                                       (indexes/start-form-registry
                                        (if (utils/storm-env?)
                                          ((requiring-resolve 'flow-storm.runtime.indexes.storm-form-registry/make-storm-form-registry))
                                          (form-registry/make-form-registry)))))
     (utils/log "Runtime index system started"))
   :cljs
   (defn start []
     (set! flow-thread-registry (indexes/start-thread-registry
                                 (thread-registry/make-thread-registry)
                                 {:on-thread-created (fn [{:keys [flow-id thread-id thread-name form-id]}]
                                                       (events/publish-event!
                                                        (events/make-thread-created-event flow-id thread-id thread-name form-id)))}))     
     (set! forms-registry (indexes/start-form-registry
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
     (alter-var-root #'flow-thread-registry indexes/stop-thread-registry)
     (alter-var-root #'forms-registry indexes/stop-form-registry)
     (utils/log "Runtime index system stopped"))
   
   :cljs
   (defn stop []
     (set! flow-thread-registry indexes/stop-thread-registry)
     (set! forms-registry indexes/stop-form-registry)
     (utils/log "Runtime index system stopped")))

(defn flow-exists? [flow-id]  
  (indexes/flow-exists? flow-thread-registry flow-id))

(defn create-thread-indexes! [flow-id thread-id thread-name form-id]
  (let [thread-indexes {:frame-index (frame-index/make-index)
                        :fn-call-stats-index (fn-call-stats-index/make-index)}]    

    (indexes/register-thread-indexes flow-thread-registry flow-id thread-id thread-name form-id thread-indexes)
    
    thread-indexes))

(defn get-thread-indexes [flow-id thread-id]  
  (when flow-thread-registry
    (indexes/get-thread-indexes flow-thread-registry flow-id thread-id)))

(defn get-or-create-thread-indexes [flow-id thread-id thread-name form-id]
  (when (and (nil? flow-id)
             (not (flow-exists? nil)))
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
  (let [{:keys [frame-index fn-call-stats-index]} (get-or-create-thread-indexes flow-id thread-id thread-name (fn-call-trace/get-form-id trace))]

    (indexes/add-fn-call frame-index trace)
    (indexes/add-fn-call fn-call-stats-index trace)))

(defn add-fn-return-trace [flow-id thread-id trace]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]    
    (when frame-index
      (indexes/add-fn-return frame-index trace))))

(defn add-expr-exec-trace [flow-id thread-id trace]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]
    (when frame-index
      (indexes/add-expr-exec frame-index trace))))

(defn add-bind-trace [flow-id thread-id trace]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]
    (when frame-index
      (indexes/add-bind frame-index trace))))

;;;;;;;;;;;;;;;;;
;; Indexes API ;;
;;;;;;;;;;;;;;;;;

(defn get-form [_ _ form-id]
  (indexes/get-form forms-registry form-id))

(defn all-threads []
  (indexes/all-threads flow-thread-registry))

(defn all-forms [_ _]
  (indexes/all-forms forms-registry))

(defn timeline-count [flow-id thread-id]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]
    (indexes/timeline-count frame-index)))

(defn timeline-frame-seq [flow-id thread-id]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]
    (indexes/timeline-frame-seq frame-index)))

(defn timeline-entry [flow-id thread-id idx]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]
    (indexes/timeline-entry frame-index idx)))

(defn frame-data [flow-id thread-id idx]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]
    (indexes/frame-data frame-index idx)))

(defn- coor-in-scope? [scope-coor current-coor]
  (if (empty? scope-coor)
    true
    (and (every? true? (map = scope-coor current-coor))
         (> (count current-coor) (count scope-coor)))))

(defn bindings [flow-id thread-id idx]
  (let [entry (timeline-entry flow-id thread-id idx)]
    (cond
      (= :frame (:timeline/type entry))
      []

      (= :expr (:timeline/type entry))
      (let [expr entry
            {:keys [bindings]} (frame-data flow-id thread-id idx)]
        (->> bindings
             (keep (fn [bind]
                     (when (and (coor-in-scope? (:coor bind) (:coor expr))
                                (<= (:timestamp bind) (:timestamp expr)))
                       [(:symbol bind) (:value bind)])))
             (into {}))))))

(defn callstack-tree-root-node [flow-id thread-id]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)]
    (indexes/callstack-tree-root-node frame-index)))

(defn callstack-node-childs [node]
  (indexes/get-childs node))

(defn callstack-node-frame [node]
  (indexes/get-node-immutable-frame node))

(defn reset-all-threads-trees-build-stack [flow-id]
  (doseq [{:keys [thread/id]} (indexes/flow-threads-info flow-thread-registry flow-id)]
    (let [{:keys [frame-idx]} (get-thread-indexes flow-id id)]
      (when frame-idx
        (indexes/reset-build-stack frame-idx)))))

(defn fn-call-stats [flow-id thread-id]
  (let [{:keys [fn-call-stats-index]} (get-thread-indexes flow-id thread-id)]
    (->> (indexes/all-stats fn-call-stats-index)
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

(defn find-fn-frames [flow-id thread-id fn-ns fn-name form-id]
  (let [{:keys [frame-index]} (get-thread-indexes flow-id thread-id)
        fn-calls (->> (indexes/timeline-seq frame-index)
                      (filter fn-call-trace/fn-call-trace?))]
    (->> fn-calls
         (keep (fn [fn-call]
                 (when (and (= fn-ns (fn-call-trace/get-fn-ns fn-call))
                            (= fn-name (fn-call-trace/get-fn-name fn-call))
                            (= form-id (fn-call-trace/get-form-id fn-call)))
                   (-> (fn-call-trace/get-frame-node fn-call)
                       indexes/get-frame 
                       indexes/get-immutable-frame)))))))


(defn discard-flow [flow-id] 
  (let [discard-keys (some->> flow-thread-registry
                              indexes/all-threads 
                              (filter (fn [[fid _]] (= fid flow-id))))]
    
    (when flow-thread-registry
      (indexes/discard-threads flow-thread-registry discard-keys))))

#?(:cljs (defn flow-threads-info [flow-id] [{:flow/id flow-id :thread/id 0 :thread/name "main"}])
   :clj (defn flow-threads-info [flow-id]          
          (when flow-thread-registry
            (indexes/flow-threads-info flow-thread-registry flow-id))))

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
