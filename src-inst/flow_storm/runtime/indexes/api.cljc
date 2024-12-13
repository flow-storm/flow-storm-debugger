(ns flow-storm.runtime.indexes.api
  
  "You can use this namespace to work with your recordings from the repl.

  Find more documentation on the docstrings of each specific function.
  
  From the UI you can retrieve the flow-id and thread-id of your recordings
  which you will need for accessing them from the repl.

  TIMELINES
  ---------
  
  Let's say you want to explore recordings on flow 0 thread 18, you can retrieve the timeline
  by using `get-timeline` like this :

  (def timeline (get-timeline 0 18))

  Once you have the timeline you can start exploring it.
  The timeline implements many of the Clojure basic interfaces, so you can :
  
  - (count timeline)
  - (take 10 timeline)
  - (get timeline 0)
  - etc

  The easiest way to take a look at a thread timeline is with some code like this :

  (->> timeline
       (take 10)
       (map as-immutable))
  
  Timelines entries are of 4 different kinds: FnCallTrace, FnReturnTrace, FnUnwindTrace and ExprTrace.

  You can access their data by using the following functions depending on the entry :
  
  All kinds :
  - `as-immutable`
  - `entry-idx`
  - `fn-call-idx`

  ExprTrace, FnReturnTrace and FnUnwindTrace :
  - `get-coord-vec`

  ExprTrace, FnReturnTrace :  
  - `get-expr-val`

  FnUnwindTrace :
  - `get-throwable`
  
  FnCallTrace :
  - `get-fn-name`
  - `get-fn-ns`
  - `get-fn-args`
  - `get-fn-parent-idx`
  - `get-fn-ret-idx`
  - `get-fn-bindings`

  You can also access the timeline as a tree by calling :

  - `callstack-root-node`
  - `callstack-node-childs`
  - `callstack-node-frame-data`

  FORMS
  -----
  
  You can retrieve forms with :

  - `get-form`

  MULTI-THREAD TIMELINES
  ----------------------
  
  If you have recorded a multi-thread timeline on flow 0, you can retrieve with `total-order-timeline` like this :

  (def mt-timeline (total-order-timeline 0))

  which you can iterate using normal Clojure functions (map, filter, reduce, get, etc).
  The easiest way to explore it is with some code like this :
  
  (->> mt-timeline
       (take 10)
       (map as-immutable))
  
  OTHER UTILITIES
  ---------------

  - `stack-for-frame`
  - `fn-call-stats`
  - `find-expr-entry`
  - `find-fn-call-entry`
  
  "
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.timeline-index :as timeline-index]            
            [flow-storm.runtime.events :as events]            
            [flow-storm.runtime.indexes.thread-registry :as thread-registry]
            [flow-storm.runtime.indexes.form-registry :as form-registry]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]            
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]            
            [flow-storm.runtime.types.expr-trace :as expr-trace]
            [flow-storm.runtime.indexes.total-order-timeline :as total-order-timeline]
            [flow-storm.utils :as utils]
            [hansel.utils :as hansel-utils]
            [clojure.pprint :as pp]
            [clojure.string :as str]))

(declare discard-flow)
(declare get-thread-indexes)

;; Registry that contains all flows and threads timelines.
;; It is an instance of `flow-storm.runtime.indexes.thread-registry/ThreadRegistry`.
(defonce flow-thread-registry nil)

;; Registry that contains all registered forms.
;; It could be anything that implements `flow-storm.runtime.indexes.protocols/FormRegistryP`
;; 
;; Currently it can be an instance of `flow-storm.runtime.indexes.thread-registry/FormRegistry`
;; or `clojure.storm/FormRegistry` when working with ClojureStorm.
(defonce forms-registry nil)

;; Stores the function calls limits for different functions.
(defonce fn-call-limits (atom nil))

(defn add-fn-call-limit [fn-ns fn-name limit]
  (swap! fn-call-limits assoc-in [fn-ns fn-name] limit))

(defn rm-fn-call-limit [fn-ns fn-name]
  (swap! fn-call-limits update fn-ns dissoc fn-name))

(defn clear-fn-call-limits []
  (reset! fn-call-limits nil))

(defn get-fn-call-limits []
  @fn-call-limits)

(defn indexes-started? []
  (not (nil? flow-thread-registry)))

(defn check-fn-limit!
  
  "Automatically decrease the limit for the function if it exists.
  Returns true when there is a limit and it is reached, false otherwise."
  
  [thread-fn-call-limits fn-ns fn-name]
  (when-let [fcl @thread-fn-call-limits]
    (when-let [l (get-in fcl [fn-ns fn-name])]
      (if (not (pos? l))
        true
        (do
          (swap! thread-fn-call-limits update-in [fn-ns fn-name] dec)
          false)))))

(defn register-form [form-data]
  (if forms-registry
    
    (index-protos/register-form forms-registry (:form/id form-data) form-data)

    (utils/log (str "Warning, trying to register a form before FlowStorm startup. If you have #trace tags on your code you will have to evaluate them again after starting the debugger."
                    (pr-str form-data)))))

(defn create-flow [{:keys [flow-id ns form timestamp]}]
  (discard-flow flow-id)  
  (events/publish-event! (events/make-flow-created-event flow-id ns form timestamp)))

#?(:clj
   (defn start []
     (alter-var-root #'flow-thread-registry (fn [_]
                                              (when (utils/storm-env?)
                                                ((requiring-resolve 'flow-storm.tracer/hook-clojure-storm))
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
     (when-not flow-thread-registry
       (set! flow-thread-registry (index-protos/start-thread-registry
                                   (thread-registry/make-thread-registry)
                                   {:on-thread-created (fn [{:keys [flow-id]}]
                                                         (events/publish-event!
                                                          (events/make-threads-updated-event flow-id)))}))     
       (set! forms-registry (index-protos/start-form-registry
                             (form-registry/make-form-registry)))
       (utils/log (str "Runtime index system started")))))

#?(:clj
   (defn stop []
     (when (utils/storm-env?)
       ((requiring-resolve 'flow-storm.tracer/unhook-clojure-storm))
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
  (let [thread-indexes {:timeline-index (timeline-index/make-index flow-id thread-id)
                        :fn-call-limits (atom @fn-call-limits)
                        :thread-limited (atom nil)}]    

    (when flow-thread-registry
        (index-protos/register-thread-indexes flow-thread-registry flow-id thread-id thread-name form-id thread-indexes))
    
    thread-indexes))

(defn get-thread-indexes [flow-id thread-id]  
  (when flow-thread-registry
    (index-protos/get-thread-indexes flow-thread-registry flow-id thread-id)))

(defn get-timeline
  ([flow-id thread-id]
   (-> (get-thread-indexes flow-id thread-id)
       :timeline-index))
  ([thread-id]
   (some (fn [[fid tid]]
           (when (= thread-id tid)
             (get-timeline fid tid)))
         (index-protos/all-threads flow-thread-registry))))

(defn get-or-create-thread-indexes [flow-id thread-id thread-name form-id]

  (when-not (flow-exists? flow-id)
    (create-flow {:flow-id flow-id}))
  
  (if-let [ti (get-thread-indexes flow-id thread-id)]
    ti
    (create-thread-indexes! flow-id thread-id thread-name form-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indexes build api, this functions are meant to be called by  ;;
;; Hansel, ClojureStorm or ClojureScriptStorm instrumented code ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-flow-init-trace [trace]
  (create-flow trace))

(defn add-form-init-trace [trace]  
  (register-form trace))

(defn add-fn-call-trace [flow-id thread-id thread-name fn-ns fn-name form-id args total-order-recording?]
  (let [{:keys [timeline-index fn-call-limits thread-limited]} (get-or-create-thread-indexes flow-id thread-id thread-name form-id)]
    (when timeline-index
      (if-not @thread-limited
        
        (if-not (check-fn-limit! fn-call-limits fn-ns fn-name)
          ;; if we are not limited, go ahead and record fn-call
          (let [tl-idx (index-protos/add-fn-call timeline-index fn-ns fn-name form-id args)]
            (when (and tl-idx total-order-recording?)
              (index-protos/record-total-order-entry flow-thread-registry flow-id timeline-index (get timeline-index tl-idx))))

          ;; we hitted the limit, limit the thread with depth 1
          (reset! thread-limited 1))

        ;; if this thread is already limited, just increment the depth
        (swap! thread-limited inc)))))

(defn add-fn-return-trace [flow-id thread-id coord ret-val total-order-recording?]
  (let [{:keys [timeline-index thread-limited]} (get-thread-indexes flow-id thread-id)]
    (when timeline-index
      (if-not @thread-limited
        ;; when not limited, go ahead
        (let [tl-idx (index-protos/add-fn-return timeline-index coord ret-val)]
          (when (and tl-idx total-order-recording?)
            (index-protos/record-total-order-entry flow-thread-registry flow-id timeline-index (get timeline-index tl-idx))))

        ;; if we are limited decrease the limit depth or remove it when it reaches to 0
        (do
          (swap! thread-limited (fn [l]
                                  (when (> l 1)
                                    (dec l))))
          nil)))))

(defn add-fn-unwind-trace [flow-id thread-id coord throwable total-order-recording?]
  (let [{:keys [timeline-index thread-limited]} (get-thread-indexes flow-id thread-id)]
    (when timeline-index
      (if-not @thread-limited
        ;; when not limited, go ahead
        (when-let [tl-idx (index-protos/add-fn-unwind timeline-index coord throwable)]
          (let [unwind-trace (get timeline-index tl-idx)
                fn-idx (index-protos/fn-call-idx unwind-trace)
                {:keys [fn-ns fn-name]} (-> (get timeline-index fn-idx)
                                            index-protos/as-immutable)
                ev (events/make-function-unwinded-event {:flow-id flow-id
                                                         :thread-id thread-id
                                                         :idx tl-idx
                                                         :fn-ns fn-ns
                                                         :fn-name fn-name
                                                         :ex-type (pr-str (type throwable))
                                                         :ex-message (ex-message throwable)
                                                         :ex-hash (hash throwable)})]

            (when (and tl-idx total-order-recording?)
              (index-protos/record-total-order-entry flow-thread-registry flow-id timeline-index (get timeline-index tl-idx)))

            (events/publish-event! ev)))

        ;; if we are limited decrease the limit depth or remove it when it reaches to 0
        (do
          (swap! thread-limited (fn [l]
                                  (when (> l 1)
                                    (dec l))))
          nil)))))

(defn add-expr-exec-trace [flow-id thread-id coord expr-val total-order-recording?]
  (let [{:keys [timeline-index thread-limited]} (get-thread-indexes flow-id thread-id)]

    (when (and timeline-index (not @thread-limited))
      (let [tl-idx (index-protos/add-expr-exec timeline-index coord expr-val)]
        (when (and tl-idx total-order-recording?)
          (index-protos/record-total-order-entry flow-thread-registry flow-id timeline-index (get timeline-index tl-idx)))
        (when (= expr-val 'flow-storm/bookmark)
          (let [ev (events/make-expression-bookmark-event {:flow-id flow-id
                                                           :thread-id thread-id
                                                           :idx tl-idx
                                                           :note (:flow-storm.bookmark/note (meta expr-val))})]
            (events/publish-event! ev)))))))

(defn add-bind-trace [flow-id thread-id coord symb-name symb-val]
  (let [{:keys [timeline-index thread-limited]} (get-thread-indexes flow-id thread-id)]
    (when (and timeline-index (not @thread-limited))
      (index-protos/add-bind timeline-index coord symb-name symb-val))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indexes API. This are functions meant to be called by            ;;
;; debugger-api to expose indexes to debuggers or directly by users ;;
;; to query indexes from the repl.                                  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-form
  
  "Given a form id returns the registered form data as a map."
  
  [form-id]
  (when forms-registry    
    (try
      (index-protos/get-form forms-registry form-id)
      #?(:clj (catch Exception _ nil)
         :cljs (catch js/Error _ nil)))))

(defn all-threads []
  (when flow-thread-registry
    (index-protos/all-threads flow-thread-registry)))

(defn all-threads-ids [flow-id]
  (->> (index-protos/flow-threads-info flow-thread-registry flow-id)
       (mapv :thread/id)))

(defn all-forms [_ _]
  (index-protos/all-forms forms-registry))

(defn timeline-entry [flow-id thread-id idx drift]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (when timeline-index
      (timeline-index/timeline-entry timeline-index idx drift))))

(defn frame-data [flow-id thread-id idx opts]  
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (timeline-index/tree-frame-data timeline-index idx opts)))

(defn- coord-in-scope? [scope-coord current-coord]
  (if (empty? scope-coord)
    true
    (and (every? true? (map = scope-coord current-coord))
         (> (count current-coord) (count scope-coord)))))

(defn bindings [flow-id thread-id idx {:keys [all-frame?]}]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)
        {:keys [fn-call-idx] :as entry} (timeline-index/timeline-entry timeline-index idx :at)
        frame-data (timeline-index/tree-frame-data timeline-index fn-call-idx {:include-binds? true})
        [entry-coord entry-idx] (case (:type entry)
                                  :fn-call   [[] fn-call-idx]
                                  :fn-return [(:coord entry) (:idx entry)]
                                  :fn-unwind [(:coord entry) (:idx entry)]
                                  :expr      [(:coord entry) (:idx entry)])]
    
    (cond->> (:bindings frame-data)
      (not all-frame?) (filter (fn [bind]
                                 (and (coord-in-scope? (:coord bind) entry-coord)
                                      (>= entry-idx (:visible-after bind)))))
      true             (map (fn [bind]
                              [(:symbol bind) (:value bind)]))
      true             (into {}))))

(defn callstack-root-node
  
  "Given a flow-id and thread-id return the idx of the root node which you can
  use to start exploring the tree by calling `callstack-node-frame-data` and `callstack-node-childs`."
  
  [flow-id thread-id]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)
        idx (index-protos/tree-root-index timeline-index)
        node [flow-id thread-id idx]]
    node))

(defn callstack-node-frame-data

  "Given a flow-id, thread-id and the index of a FnCallTrace returns a map
  with the frame data."

  [flow-id thread-id fn-call-idx]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (timeline-index/tree-frame-data timeline-index fn-call-idx {})))

(defn callstack-node-childs

  "Given a flow-id, thread-id and the index of a FnCallTrace returns a vector
  of touples containing [flow-id thread-id idx] of all childs."
  
  [flow-id thread-id fn-call-idx]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (into [] 
          (map (fn [idx]
                 [flow-id thread-id idx]))
          (index-protos/tree-childs-indexes timeline-index fn-call-idx))))

(defn stack-for-frame

  "Given a flow-id, thread-id and the index of a FnCallTrace returns the
  stack for the given frame as vector of maps containing {:keys [fn-name fn-ns fn-call-idx]}."
  
  [flow-id thread-id fn-call-idx]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)
        {:keys [fn-call-idx-path]} (timeline-index/tree-frame-data timeline-index fn-call-idx {:include-path? true})]
    (reduce (fn [stack fidx]
              (let [{:keys [fn-name fn-ns fn-call-idx form-id]} (timeline-index/tree-frame-data timeline-index fidx {})
                    {:keys [form/def-kind multimethod/dispatch-val]} (get-form form-id)]
                (conj stack (cond-> {:fn-name fn-name
                                     :fn-ns fn-ns
                                     :fn-call-idx fn-call-idx
                                     :form-def-kind def-kind}
                              (= def-kind :defmethod) (assoc :dispatch-val dispatch-val)))))
            []
            fn-call-idx-path)))

(defn reset-all-threads-trees-build-stack [flow-id]
  (when flow-thread-registry
    (let [flow-threads (index-protos/flow-threads-info flow-thread-registry flow-id)]
      (doseq [{:keys [thread/id]} flow-threads]
        (let [{:keys [fn-call-idx]} (get-thread-indexes flow-id id)]
          (when fn-call-idx
            (index-protos/reset-build-stack fn-call-idx)))))))

(defn fn-call-stats

  "Given a flow-id and optionally thread-id (could be nil) returns all functions stats as a vector of
  maps containing {:keys [fn-ns fn-name form-id form form-def-kind dispatch-val cnt]}"
  
  [flow-id thread-id]
  (let [thread-ids (if thread-id
                     [thread-id]
                     (all-threads-ids flow-id))
        timelines-stats (mapcat (fn [tid]
                                  (let [{:keys [timeline-index]} (get-thread-indexes flow-id tid)]
                                    (index-protos/all-stats timeline-index))) 
                                thread-ids)]
    (into []
          (keep (fn [[fn-call cnt]]
                  (when-let [form (get-form (:form-id fn-call))]
                    (cond-> {:fn-ns (:fn-ns fn-call)
                             :fn-name (:fn-name fn-call)
                             :form-id (:form-id fn-call)
                             :form (:form/form form)
                             :form-def-kind (:form/def-kind form)
                             :dispatch-val (:multimethod/dispatch-val form)
                             :cnt cnt}
                      (:multimethod/dispatch-val form) (assoc :dispatch-val (:multimethod/dispatch-val form))))))
          timelines-stats)))


(defn make-frame-keeper [flow-id thread-id fn-ns fn-name form-id]
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
    (fn [tl-entry]
      (when (and (fn-call-trace/fn-call-trace? tl-entry)
                 (if form-id (= form-id (index-protos/get-form-id tl-entry)) true)
                 (if fn-ns   (= fn-ns   (index-protos/get-fn-ns tl-entry))   true)
                 (if fn-name (= fn-name (index-protos/get-fn-name tl-entry)) true))
        (timeline-index/tree-frame-data timeline-index (index-protos/entry-idx tl-entry) {})))))

(defn find-fn-frames
  
  "Return all the FnCallTraces matching the provided criteria."
  
  ([flow-id thread-id pred]
   
   (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
     (into [] (keep (fn [tl-entry]
                      (when (and (fn-call-trace/fn-call-trace? tl-entry)
                                 (pred tl-entry))
                        (timeline-index/tree-frame-data timeline-index (index-protos/entry-idx tl-entry) {}))))
           timeline-index)))
  
  ([flow-id thread-id fn-ns fn-name form-id]
   (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
     (into [] (keep (make-frame-keeper flow-id thread-id fn-ns fn-name form-id))
           timeline-index))))

(defn discard-flow [flow-id] 
  (let [discard-keys (some->> flow-thread-registry
                              index-protos/all-threads 
                              (filter (fn [[fid _]] (= fid flow-id))))]
    
    (when flow-thread-registry
      ;; this will also discard any total-order-timeline if there is one
      (index-protos/discard-threads flow-thread-registry discard-keys)
      (events/publish-event! (events/make-flow-discarded-event flow-id)))))

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

(defn timelines-for

  "Returns a collection of [flow-id thread-id timeline] that matches the optional criteria.
  skip-threads can be a set of thread ids."
  
  [{:keys [flow-id thread-id skip-threads]}]
  (->> (all-threads)
       (keep (fn [[fid tid]]
               (when (and (or (nil? flow-id)
                              (= flow-id fid))
                          (or (nil? thread-id)
                              (= thread-id tid))
                          (not (and (set? skip-threads)
                                    (skip-threads tid))))                                                          
                 (get-timeline fid tid))))))

(defn- keep-timeline-sub-range

  "Given a timeline and a sub range [from to] applies f like clojure.core/keep.
  The provided function f will be called with two args: thread-id and timeline-entry. "

  [timeline from to f]
  
  (when (<= 0 from to (count timeline))
    (loop [i from
           batch-res (transient [])]
      (if (< i to)
        (let [tl-entry (get timeline i)
              tl-entry (if (total-order-timeline/total-order-timeline-entry? tl-entry)
                         (index-protos/tote-thread-timeline-entry tl-entry)
                         tl-entry)
              ;; we get the thread-id for each entry to support multi-thread-timelines
              tl-thread-id (index-protos/thread-id timeline i)]
          (if-let [e (f tl-thread-id tl-entry)]             
            (recur (inc i) (conj! batch-res e))
            (recur (inc i) batch-res)))
        (persistent! batch-res)))))

(defn async-interruptible-batched-timelines-keep

  "Like clojure.core/keep over timelines entries but async, applying f in batches of size batch-size.
  The provided function f will be called with two args: thread-id and timeline-entry.
  Returns a map {:keys [start interrupt]} with two functions which can be used to interrupt the collection processing between batches.
  on-batch should be a callback of 3 args, that will be called with the
  flow-id thread-id and the result of processing each batch with xf.
  on-end will be called with no args for signaling the end."

  [f timelines {:keys [on-batch on-end]}]

  (let [batch-size 10000
        interrupted? (atom false)
        interrupt (fn interrupt [] (reset! interrupted? true))
        start (fn start []
                (let [process-next-batch (fn process-next-batch [[timeline & rtimelines :as work-timelines] from-idx]
                                           (if (or @interrupted?
                                                   (nil? timeline))
                                             
                                             (on-end)

                                             ;; else keep collecting
                                             (let [to-idx (min (+ from-idx batch-size) (count timeline))
                                                   batch-res (locking timeline
                                                               ;; this locking because the timeline is mutable,
                                                               ;; but maybe the timeline should expose a way of iterating sub sequences
                                                               (keep-timeline-sub-range timeline from-idx to-idx f))]
                                               
                                               
                                               (on-batch batch-res)
                                               (if (= to-idx (count timeline))

                                                 ;; if the batch we just processed was the last one of the timeline,
                                                 ;; change timelines
                                                 #?(:clj (recur rtimelines 0)
                                                    :cljs (js/setTimeout process-next-batch 0 rtimelines 0))

                                                 ;; else keep collecting from the current timeline
                                                 #?(:clj (recur work-timelines to-idx)
                                                    :cljs (js/setTimeout process-next-batch 0 work-timelines to-idx))))))]
                  
                  (process-next-batch timelines 0)))]
    {:interrupt interrupt
     :start     start}))


(defn find-in-timeline-sub-range

  "Given a timeline tries to find a entry using pred but only between
  from-idx to to-idx. It can also search backwards.
  pred should be a fn of two args (fn [form-id entry]). When needs-form-id?
  is false (default) form-id will be nil. Done for perf reasons."
  
  [timeline from-idx to-idx pred {:keys [backward? needs-form-id?]}]
  (let [next-idx (if backward? dec inc)]
    ;; this locking because the timeline is mutable,
    ;; but maybe the timeline should expose a way of iterating sub sequences    
    (locking timeline
      (loop [idx from-idx]
        (when-not (= idx to-idx)
          (let [entry (get timeline idx)
                form-id (when needs-form-id?
                         (let [fn-call (if (fn-call-trace/fn-call-trace? entry)
                                         entry
                                         (get timeline (index-protos/fn-call-idx entry)))]
                           (index-protos/get-form-id fn-call)))]
            (if-let [r (pred form-id entry)]
             r
             (recur (next-idx idx)))))))))

(defn timelines-async-interruptible-find-entry

  "Search all timelines entries with pred.
  
  If flow-id and thread-id are provided, search will only be restricted to that thread timeline.
  pred should be a function of two arguments form-id and the entry. form-id will be nil by default unless
  needs-form-id? is true.
  from-idx can be used to start the search from its position.
  backward? will change the direction of the search.
  skip-threads is an optional set of threds ids you would like to skip the search on.

  This funciton returns immediately and you should provide the following callbacks :

  - on-progress [optional], a fn of one arg containing a 0-100 integer of the progress
  - on-match, a fn of one arg called with a map containing the matched entry info
  - on-end, a fn of zero args that will be called for signaling that the search reached the end with no matches  

  Returns a map of {:keys [start interrupt]}, two functions of zero args you should call to start the process
  or interrupt it while it is running."

  [pred timelines {:keys [from-idx backward? needs-form-id?]} {:keys [on-progress on-match on-end]}]
  (let [batch-size 10000
        total-batches (max 1
                           (->> (all-threads)
                                (mapv (fn [[fid tid]]
                                        (quot (count (get-timeline fid tid))
                                              batch-size)))
                                (reduce +)))
        batches-processed (volatile! 0)
        interrupted? (atom false)
        interrupt (fn [] (reset! interrupted? true))
        start (fn []
                (let [find-next-batch (fn find-next-batch [[timeline & rtimelines :as work-timelines] curr-idx]
                                        (if (or @interrupted?
                                                (nil? timeline))
                                          
                                          (on-end)

                                          ;; else keep searching
                                          (let [curr-idx (or curr-idx
                                                             (if backward?
                                                               (dec (count timeline))
                                                               0))
                                                flow-id (index-protos/flow-id timeline curr-idx)
                                                thread-id (index-protos/thread-id timeline curr-idx)
                                                to-idx (if backward?
                                                         (max (- curr-idx batch-size) 0)
                                                         (min (+ curr-idx batch-size) (count timeline)))
                                                entry (find-in-timeline-sub-range timeline curr-idx to-idx pred {:backward? backward? :needs-form-id? needs-form-id?})]
                                            (if entry
                                              
                                              ;; if we found the entry report the match and finish
                                              (on-match (-> entry
                                                            index-protos/as-immutable
                                                            (assoc :flow-id flow-id
                                                                   :thread-id thread-id)))
                                              
                                              ;; else report progress and continue searching
                                              (do
                                                (when on-progress
                                                  (on-progress (int (* 100 (/ @batches-processed total-batches)))))
                                                (if (or (and (not backward?) (= to-idx (count timeline)))
                                                        (and backward?       (= to-idx 0)))

                                                  ;; if the batch we just searched was the last one of the timeline,
                                                  ;; change timelines
                                                  #?(:clj (recur rtimelines from-idx)
                                                     :cljs (js/setTimeout find-next-batch 0 rtimelines from-idx))

                                                  ;; else keep collecting from the current timeline
                                                  #?(:clj (recur work-timelines to-idx)
                                                     :cljs (js/setTimeout find-next-batch 0 work-timelines to-idx)))))))
                                        )]
                  (find-next-batch timelines from-idx)))]
    {:interrupt interrupt
     :start start}))

(defn find-flow-fn-call [flow-id]
  (some (fn [[fid tid]]
          (when (= flow-id fid)
            (let [{:keys [timeline-index]} (get-thread-indexes fid tid)]
              (when (pos? (count timeline-index))
                (-> timeline-index
                    first
                    index-protos/as-immutable
                    (assoc :flow-id fid
                           :thread-id tid)) ))))
        (index-protos/all-threads flow-thread-registry)))

(defn build-find-fn-call-entry-predicate [{:keys [fn-ns fn-name form-id args-pred]}]
  (fn [entry-form-id tl-entry]
    (when (and (fn-call-trace/fn-call-trace? tl-entry)
             (if form-id      (= form-id entry-form-id)                    true)
             (if fn-ns (= (index-protos/get-fn-ns tl-entry) fn-ns)         true)
             (if fn-name (= (index-protos/get-fn-name tl-entry) fn-name)   true)
             (if args-pred (args-pred (index-protos/get-fn-args tl-entry)) true))
      tl-entry)))

#?(:clj
   (defn find-fn-call-entry

     "Find the first match of a FnCallTrace entry that matches the criteria.

  Criteria (can be combined in any way) :

  - flow-id, if not present will match any.
  - thread-id, if not present will match any.
  - from-idx, where to start searching, defaults to: 0 or last when backward? is true.
  - backward?, search backwards, default to false.
  - fn-ns, the function namespace to match.
  - fn-name, the function name to match.
  - args-pred, a predicate of one argument that will receive the args vector.

  Absent criteria that doesn't have a default value will always match."
     
     [criteria]
     (let [result-prom (promise)
           {:keys [start]} (timelines-async-interruptible-find-entry
                            (build-find-fn-call-entry-predicate criteria)
                            (timelines-for criteria)
                            criteria
                            {:on-match (fn [m] (deliver result-prom m))                             
                             :on-end   (fn []  (deliver result-prom nil))})]
       (start)       
       @result-prom)))

(defn- entry-matches-file-and-line? [entry-form-id entry file line]
  (let [form (get-form entry-form-id)]
    (when (= file (:form/file form))
      (let [coord (index-protos/get-coord-vec entry)
            sub-form (hansel-utils/get-form-at-coord (:form/form form) coord)]
        (-> sub-form meta :line (= line))))))

(defn build-find-expr-entry-predicate [{:keys [identity-val equality-val custom-pred-form coord form-id file line] :as criteria}]
  (let [coord (when coord (utils/stringify-coord coord))
        custom-pred-fn #?(:clj (when custom-pred-form (eval (read-string custom-pred-form)))
                          :cljs (do
                                  (utils/log (str "Custom stepping is not supported in ClojureScript yet " custom-pred-form))
                                  (constantly true)))]
    (fn [entry-form-id tl-entry]
      (when (and (or (fn-return-trace/fn-return-trace? tl-entry)
                     (expr-trace/expr-trace? tl-entry))
                 (if (contains? criteria :identity-val) (identical? (index-protos/get-expr-val tl-entry) identity-val)  true)
                 (if (contains? criteria :equality-val) (= (index-protos/get-expr-val tl-entry) equality-val)           true)
                 (if coord           (= coord (index-protos/get-coord-raw tl-entry))                 true)
                 (if form-id         (= form-id entry-form-id)                                       true)
                 (if (and file line) (entry-matches-file-and-line? entry-form-id tl-entry file line) true)
                 (if custom-pred-fn  (custom-pred-fn (index-protos/get-expr-val tl-entry))           true))
        tl-entry))))

#?(:clj  
   (defn find-expr-entry

     "Find the first match of a ExprTrace or ReturnTrace entry that matches criteria.
  
  Criteria (can be combined in any way) :

  - flow-id, if not present will match any.
  - thread-id, if not present will match any.
  - from-idx, where to start searching, defaults to: 0 or last when backward? is true.
  - backward?, search backwards, default to false.
  - identity-val, search this val with identical? over expressions values.
  - equality-val, search this val with = over expressions values.
  - form-id, search by the form-id of the expression.
  - file and line, search by file name and line
  - coord, a vector with a coordinate to match, like [3 1 2].
  - custom-pred-form, a string with a form to use as a custom predicate over expression values, like \"(fn [v] (map? v))\"
  - skip-threads, a set of threads ids to skip.
  
  Absent criteria that doesn't have a default value will always match.
  "
     
     [criteria]
     (let [result-prom (promise)
           {:keys [start]} (timelines-async-interruptible-find-entry
                            (build-find-expr-entry-predicate criteria)
                            (timelines-for criteria)
                            criteria
                            {:on-match (fn [m] (deliver result-prom m))
                             :on-end   (fn []  (deliver result-prom nil))})]
       (start)       
       @result-prom)))

(defn total-order-timeline
  
  "Retrieves the total order timeline for a flow-id if there is one recorded.
  Look at this namespace docstring for more info."
  
  [flow-id]
  (index-protos/total-order-timeline flow-thread-registry flow-id))

(defn detailed-total-order-timeline [flow-id]
  (let [timeline (total-order-timeline flow-id)]
    (into []
          (keep (total-order-timeline/make-detailed-timeline-mapper forms-registry))
          timeline)))

(defn make-thread-prints-keeper [printers]
  ;; printers is a map of {form-id {coord-vec-1 {:format-str :print-length :print-level :transform-expr-str}}}
  (let [printers (utils/update-values
                  printers
                  (fn [corrds-map]
                    (-> corrds-map
                        (utils/update-keys (fn [coord-vec]
                                             (let [scoord (str/join "," coord-vec)]
                                               #?(:cljs scoord
                                                  :clj (.intern scoord)))))
                        (utils/update-values (fn [printer-params]
                                               #?(:cljs printer-params
                                                  
                                                  :clj (let [trans-expr (:transform-expr-str printer-params)]
                                                         (try
                                                           (if-not (str/blank? trans-expr)
                                                             (let [expr-fn (-> trans-expr
                                                                               read-string
                                                                               eval)]
                                                               (assoc printer-params :transform-expr-fn expr-fn))
                                                             printer-params)
                                                           (catch Exception e
                                                             (utils/log-error (str "Error evaluating printer transform expresion " trans-expr) e)
                                                             printer-params)))))))))
        
        maybe-print-entry (fn [form-id thread-id tl-entry]
                            (when (contains? printers form-id)
                              (let [coords-map (get printers form-id)
                                    coord (index-protos/get-coord-raw tl-entry)]
                                (when (contains? coords-map coord)
                                  ;; we are interested in this coord so lets print it
                                  (let [{:keys [print-length print-level format-str transform-expr-fn]} (get coords-map coord)
                                        transform-expr-fn (or transform-expr-fn identity)
                                        val (index-protos/get-expr-val tl-entry)
                                        entry-idx (index-protos/entry-idx tl-entry)]
                                    (binding [*print-length* print-length
                                              *print-level* print-level]
                                      {:text (->> val
                                                  transform-expr-fn
                                                  pr-str
                                                  (utils/format format-str))
                                       :idx entry-idx                                       
                                       :thread-id thread-id}))))))
        threads-stacks (atom {})]
    (fn [thread-id tl-entry]
      (let [form-id (when-let [thread-fn-call (first (get @threads-stacks thread-id))]
                      (index-protos/get-form-id thread-fn-call))]
        
        (cond
          (fn-call-trace/fn-call-trace? tl-entry)
          (do
            (swap! threads-stacks (fn [ths-stks] (update ths-stks thread-id conj tl-entry)))
            nil)
          
          (fn-return-trace/fn-end-trace? tl-entry)
          (let [p (maybe-print-entry form-id thread-id tl-entry)]
            (swap! threads-stacks (fn [ths-stks] (update ths-stks thread-id pop)))
            p)

          (expr-trace/expr-trace? tl-entry)
          (maybe-print-entry form-id thread-id tl-entry))))))

(defn timelines-mod-timestamps []
  (reduce (fn [acc [fid tid]]
            (let [tl (get-timeline fid tid)]
              (conj acc {:flow-id fid
                         :thread-id tid
                         :last-modified (index-protos/last-modified tl)})))
          #{}
   (all-threads)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for exploring indexes from the repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn print-threads []
  (->> (all-threads)
       (map #(zipmap [:flow-id :thread-id :thread-name] %))
       pp/print-table))

(defn entry-idx
  "Given a timeline entry of any kind return its possition in the timeline."
  [entry]
  (index-protos/entry-idx entry))

(defn fn-call-idx
  "Given a timeline entry of any kind return the possition of the FnCallTrace entry
  wrapping it."
  [entry]
  (index-protos/fn-call-idx entry))

(defn get-coord-vec
  "Given a ExprTrace, FnReturnTrace or FnUnwindTrace return its coordinate vector."
  [entry]
  (index-protos/get-coord-vec entry))

(defn get-expr-val
  "Given a ExprTrace or FnReturnTrace entry return its expression value."
  [entry]
  (index-protos/get-expr-val entry))

(defn get-throwable
  "Returns the throwable of a FnUnwindTrace entry."
  [entry]
  (index-protos/get-throwable entry))

(defn as-immutable
  "Returns a hasmap representing this timeline entry."
  [entry]
  (index-protos/as-immutable entry))

(defn get-fn-name
  "Returns the function name of a FnCallTrace timeline entry."
  [entry]
  (index-protos/get-fn-name entry))

(defn get-fn-ns
  "Returns the function namespace of a FnCallTrace timeline entry."
  [entry]
  (index-protos/get-fn-ns entry))

(defn get-fn-args
  "Returns the arguments of a FnCallTrace timeline entry."
  [entry]
  (index-protos/get-fn-args entry))

(defn get-fn-parent-idx
  "Returns the parent function index of a FnCallTrace timeline entry."
  [entry]
  (index-protos/get-parent-idx entry))

(defn get-fn-ret-idx
  "Given a FnCallTrace timeline entry returns the index of its matching FnReturnTrace or FnUnwindTrace entry."
  [entry]
  (index-protos/get-ret-idx entry))

(defn get-fn-bindings
  "Given a FnCallTrace entry return its bindings."
  [entry]
  (index-protos/bindings entry))

(defn get-bind-sym-name
  "Given a BindTrace, return its symbol name."
  [entry]
  (index-protos/get-bind-sym-name entry))

(defn get-bind-val
  "Given a BindTrace, return its value."
  [entry]
  (index-protos/get-bind-val entry))

(defn tote-thread-id
  [entry]
  (index-protos/thread-id
   (index-protos/tote-thread-timeline entry)
   0))

(defn tote-entry
  [entry]
  (index-protos/tote-thread-timeline-entry entry))

(defn get-sub-form-at-coord
  
  "Given a form and a coord inside it returns the sub-form at that coordinate."
  
  [form coord]
  (hansel-utils/get-form-at-coord form coord))

(defn fn-call-trace? [x]
  (fn-call-trace/fn-call-trace? x))

(defn expr-trace? [x]
  (expr-trace/expr-trace? x))

(defn fn-return-trace? [x]
  (fn-return-trace/fn-return-trace? x))

(defn fn-unwind-trace? [x]
  (fn-return-trace/fn-unwind-trace? x))

(defn fn-end-trace?
  "Returns true if `x` is a `fn-return-trace?` or `fn-unwind-trace?`"
  [x]
  (fn-return-trace/fn-end-trace? x))

