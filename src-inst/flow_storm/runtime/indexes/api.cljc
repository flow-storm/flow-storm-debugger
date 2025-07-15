(ns flow-storm.runtime.indexes.api
  
  "You can use this namespace to work with your recordings from the repl.

  Find more documentation on the docstrings of each specific function.
  
  From the UI you can retrieve the flow-id and thread-id of your recordings
  which you will need for accessing them from the repl.

  TIMELINES
  ---------
  
  Retrieving a timeline by flow-id and thread id.
  If you don't provide the flow-id 0 is assumed.
  
  (def timeline (get-timeline 18))
  (def timeline (get-timeline 0 18))

  The timeline implements many of the Clojure basic interfaces, so you can :
  
  (count timeline)
  (take 10 timeline)
  (get timeline 0)
  
  The easiest way to take a look at a thread timeline is with some code like this :

  (->> timeline
       (take 10)
       (mapv as-immutable))

  Converting all entries to immutable values is very practical since each entry will become a
  Clojure map, but it is slower and consumes more memory, which becomes a thing in very long timelines,
  so there are functions to deal with entries objects, without having to create a map out of them.
  
  Timelines entries are of 4 different kinds: FnCallTrace, FnReturnTrace, FnUnwindTrace and ExprTrace.

  You can access their data by using the following functions depending on the entry :
  
  All kinds :
  - `as-immutable`
  `

  ExprTrace, FnReturnTrace and FnUnwindTrace :
  - `get-coord-vec`
  - `fn-call-idx`

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
  - `get-form-id`

  FnBind :

  - `get-bind-sym-name`
  - `get-bind-val`
  
  You can also access the timeline as a tree by calling :

  - `callstack-root-node`
  - `callstack-node-childs`
  - `callstack-node-frame-data`

  FORMS
  -----
  
  You can retrieve forms with :

  - `get-form`
  - `get-sub-form`
  
  MULTI-THREAD TIMELINES
  ----------------------
  
  If you have recorded a multi-thread timeline on flow 0, you can retrieve with `total-order-timeline` like this :

  (def mt-timeline (total-order-timeline 0))

  which you can iterate using normal Clojure functions (map, filter, reduce, get, etc).
  The easiest way to explore it is with some code like this :
  
  (->> mt-timeline
       (take 10)
       (map as-immutable))

  Each total order timeline entry object can give you a pointer to the entry on its thread timeline and the thread id via :

  - `tote-entry`
  - `tote-thread-id`
  
  OTHER UTILITIES
  ---------------

  - `stack-for-frame`
  - `fn-call-stats`
  - `find-expr-entry`
  - `find-fn-call-entry`
  - `print-threads`
  - `get-fn-call`
  - `get-sub-form-at-coord`
  - `get-sub-form`
  - `find-entry-by-sub-form-pred`
  - `find-entry-by-sub-form-pred-all-threads`
  - `fn-call-trace?`
  - `expr-trace?`
  - `fn-return-trace?`
  - `fn-unwind-trace?`
  - `fn-end-trace?`

  "
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.timeline-index :as timeline-index :refer [ensure-indexes]]
            [flow-storm.runtime.values :as rt-values]
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
            [clojure.string :as str]
            [clojure.set :as set]
            #?(:clj [clojure.core.protocols :as cp])))

(declare discard-flow)

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
                                              (let [registry (thread-registry/make-flows-threads-registry)]                                                
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
       (set! flow-thread-registry (thread-registry/make-flows-threads-registry))     
       (set! forms-registry (index-protos/start-form-registry
                             (form-registry/make-form-registry)))
       (utils/log (str "Runtime index system started")))))

#?(:clj
   (defn stop []
     (when (utils/storm-env?)
       ((requiring-resolve 'flow-storm.tracer/unhook-clojure-storm))
       (utils/log "Storm functions unplugged"))     
     (alter-var-root #'flow-thread-registry (constantly nil))
     (alter-var-root #'forms-registry index-protos/stop-form-registry)
     (utils/log "Runtime index system stopped"))
   
   :cljs
   (defn stop []
     (set! flow-thread-registry (constantly nil))
     (set! forms-registry index-protos/stop-form-registry)
     (utils/log "Runtime index system stopped")))

(defn flow-exists? [flow-id]  
  (when flow-thread-registry
    (index-protos/flow-exists? flow-thread-registry flow-id)))

(defn check-fn-limit!
  
  "Automatically decrease the limit for the function if it exists.
  Returns true when there is a limit and it is reached, false otherwise."
  
  [*thread-fn-call-limits fn-ns fn-name]
  (when-let [fcl @*thread-fn-call-limits]
    (when-let [l (get-in fcl [fn-ns fn-name])]
      (if (not (pos? l))
        true
        (do
          (swap! *thread-fn-call-limits update-in [fn-ns fn-name] dec)
          false)))))

#?(:cljs (defn flow-threads-info [flow-id] [{:flow/id flow-id :thread/id 0 :thread/name "main" :thread/blocked? false}])
   :clj (defn flow-threads-info [flow-id]          
          (when flow-thread-registry
            (index-protos/flow-threads-info flow-thread-registry flow-id))))

(defn create-thread-tracker! [flow-id thread-id thread-name]
  (let [timeline (timeline-index/make-index flow-id thread-id thread-name)
        thread-tracker (index-protos/register-thread flow-thread-registry
                                                     flow-id
                                                     thread-id
                                                     thread-name
                                                     timeline
                                                     @fn-call-limits)]    

    (events/publish-event! (events/make-threads-updated-event flow-id (flow-threads-info flow-id)))
    
    thread-tracker))

(defn get-timeline
  ([flow-id thread-id]
   (when flow-thread-registry
     (:thread/timeline (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id))))
  ([thread-id]
   (some (fn [[fid tid]]
           (when (= thread-id tid)
             (get-timeline fid tid)))
         (index-protos/all-threads flow-thread-registry))))

(defn get-or-create-thread-tracker [flow-id thread-id thread-name]

  (when-not (flow-exists? flow-id)
    (create-flow {:flow-id flow-id}))
  
  (if-let [th-tracker (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    th-tracker
    (create-thread-tracker! flow-id thread-id thread-name)))

;;;;;;;;;;;;;;;;;;;;;;;
;; Indexes build api ;;
;;;;;;;;;;;;;;;;;;;;;;;

(defn add-form-init-trace [trace]  
  (register-form trace))

(defn add-fn-call-trace [flow-id thread-id thread-name fn-ns fn-name form-id args total-order-recording?]
  (let [{:thread/keys [timeline *fn-call-limits *thread-limited]} (get-or-create-thread-tracker flow-id thread-id thread-name)]
    (if-not @*thread-limited
      
      (if-not (check-fn-limit! *fn-call-limits fn-ns fn-name)
        ;; if we are not limited, go ahead and record fn-call
        (let [tl-idx (index-protos/add-fn-call timeline fn-ns fn-name form-id args)]
          (when (and tl-idx total-order-recording?)
            (index-protos/record-total-order-entry flow-thread-registry flow-id timeline tl-idx))
          tl-idx)

        ;; we hitted the limit, limit the thread with depth 1
        (reset! *thread-limited 1))

      ;; if this thread is already limited, just increment the depth
      (swap! *thread-limited inc))))

(defn add-fn-return-trace [flow-id thread-id coord ret-val total-order-recording?]
  (when-let [{:thread/keys [timeline *thread-limited]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (if-not @*thread-limited
      ;; when not limited, go ahead
      (let [tl-idx (index-protos/add-fn-return timeline coord ret-val)]
        (when (and tl-idx total-order-recording?)
          (index-protos/record-total-order-entry flow-thread-registry flow-id timeline tl-idx))
        tl-idx)

      ;; if we are limited decrease the limit depth or remove it when it reaches to 0
      (do
        (swap! *thread-limited (fn [l]
                                 (when (> l 1)
                                   (dec l))))
        nil))))

(defn add-fn-unwind-trace [flow-id thread-id coord throwable total-order-recording?]
  (when-let [{:thread/keys [timeline *thread-limited]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (if-not @*thread-limited
      ;; when not limited, go ahead
      (when-let [tl-idx (index-protos/add-fn-unwind timeline coord throwable)]
        (let [unwind-trace (get timeline tl-idx)
              fn-idx (index-protos/fn-call-idx unwind-trace)
              {:keys [fn-ns fn-name]} (-> (get timeline fn-idx)
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
            (index-protos/record-total-order-entry flow-thread-registry flow-id timeline tl-idx))

          (events/publish-event! ev))
        tl-idx)

      ;; if we are limited decrease the limit depth or remove it when it reaches to 0
      (do
        (swap! *thread-limited (fn [l]
                                 (when (> l 1)
                                   (dec l))))
        nil))))

(defn add-expr-exec-trace [flow-id thread-id coord expr-val total-order-recording?]
  (when-let [{:thread/keys [timeline *thread-limited]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (when (not @*thread-limited)
      (let [tl-idx (index-protos/add-expr-exec timeline coord expr-val)]
        (when (and tl-idx total-order-recording?)
          (index-protos/record-total-order-entry flow-thread-registry flow-id timeline tl-idx))
        (when (and (symbol? expr-val)
                   (= expr-val 'flow-storm/bookmark))
          (let [ev (events/make-expression-bookmark-event {:flow-id flow-id
                                                           :thread-id thread-id
                                                           :idx tl-idx
                                                           :note (:flow-storm.bookmark/note (meta expr-val))})]
            (events/publish-event! ev)))
        tl-idx))))

(defn add-bind-trace [flow-id thread-id coord symb-name symb-val]
  (when-let [{:thread/keys [timeline *thread-limited]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (when (not @*thread-limited)
      (index-protos/add-bind timeline coord symb-name symb-val))))

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
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (when timeline
      (timeline-index/timeline-entry timeline idx drift))))

(defn frame-data [flow-id thread-id idx opts]  
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (timeline-index/tree-frame-data timeline idx opts)))

(defn- coord-in-scope? [scope-coord current-coord]
  (if (empty? scope-coord)
    true
    (and (every? true? (map = scope-coord current-coord))
         (> (count current-coord) (count scope-coord)))))

(defn- partition-bindings-loops [bindings]
  (loop [[curr-b & rest-bindings] bindings
         seen-symb-coords #{}
         partitions []
         curr-part []]
    (if-not curr-b
      (cond-> partitions
        (seq curr-part) (conj curr-part))
      (let [symb-coord [(:symbol curr-b) (:coord curr-b)]]
        (if (seen-symb-coords symb-coord)
          (recur rest-bindings
                 #{symb-coord}
                 (conj partitions curr-part)
                 [curr-b])
          (recur rest-bindings
                 (conj seen-symb-coords symb-coord)
                 partitions
                 (conj curr-part curr-b)))))))

(defn bindings [flow-id thread-id idx _]
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)
        {:keys [fn-call-idx] :as entry} (timeline-index/timeline-entry timeline idx :at)
        frame-data (timeline-index/tree-frame-data timeline fn-call-idx {:include-binds? true})
        [entry-coord entry-idx] (case (:type entry)
                                  :fn-call   [[] fn-call-idx]
                                  :fn-return [(:coord entry) (:idx entry)]
                                  :fn-unwind [(:coord entry) (:idx entry)]
                                  :expr      [(:coord entry) (:idx entry)])

        ;; Bindings calculation for a given index (current locals) is a little bit involved because of loops.
        ;; To account for loops, we partition all the bindings by "iterations", which are delimited by
        ;; a first repeating [symbol coordinate].
        ;; `visible-loops-iterations` then are all iterations partitions with a first binding <= our target idx.
        ;; We are not interested in any iteration that starts after our idx.
        visible-loops-iterations (->> (partition-bindings-loops (:bindings frame-data))
                                      (take-while (fn [part] (<= (-> part first :visible-after) entry-idx))))
        ;; The last iteration will contain bindings that are visible, and bindings that are not so
        ;; `invisible-last-iteration` are all the [symbol coordinate] from the last visible iteration that
        ;; aren't still visible at idx
        invisible-last-iteration (->> (last visible-loops-iterations)
                                      (keep (fn [bind]
                                              (when (> (:visible-after bind) entry-idx)
                                                [(:symbol bind) (:coord bind)])))
                                      (into #{}))
        ;; for our final visible-bindings we reduce all our partitions keeping only the bindings
        ;; with :visible-after >= to the idx, and also which coordinates are "wrapping"
        ;; the coordinate for the entry at idx, but not included the `invisible-last-iteration` ones.
        visible-bindings (reduce (fn [vbs part]
                                   (reduce (fn [vbs' bind]
                                             (if (and (coord-in-scope? (:coord bind) entry-coord)
                                                      (>= entry-idx (:visible-after bind))
                                                      (not (invisible-last-iteration [(:symbol bind) (:coord bind)])))
                                               (assoc vbs' (:symbol bind) (:value bind))
                                               vbs'))
                                           vbs
                                           part))
                                 {}
                                 visible-loops-iterations)]
    visible-bindings))

(defn callstack-root-node
  
  "Given a flow-id and thread-id return the idx of the root node which you can
  use to start exploring the tree by calling `callstack-node-frame-data` and `callstack-node-childs`."
  
  [flow-id thread-id]
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)
        idx (index-protos/tree-root-index timeline)
        node [flow-id thread-id idx]]
    node))

(defn callstack-node-frame-data

  "Given a flow-id, thread-id and the index of a FnCallTrace returns a map
  with the frame data."

  [flow-id thread-id fn-call-idx]
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (timeline-index/tree-frame-data timeline fn-call-idx {})))

(defn callstack-node-childs

  "Given a flow-id, thread-id and the index of a FnCallTrace returns a vector
  of touples containing [flow-id thread-id idx] of all childs."
  
  [flow-id thread-id fn-call-idx]
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (into [] 
          (map (fn [idx]
                 [flow-id thread-id idx]))
          (index-protos/tree-childs-indexes timeline fn-call-idx))))

(defn stack-for-frame

  "Given a flow-id, thread-id and the index of a FnCallTrace returns the
  stack for the given frame as vector of maps containing {:keys [fn-name fn-ns fn-call-idx]}."
  
  [flow-id thread-id fn-call-idx]
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)
        {:keys [fn-call-idx-path]} (timeline-index/tree-frame-data timeline fn-call-idx {:include-path? true})]
    (reduce (fn [stack fidx]
              (let [{:keys [fn-name fn-ns fn-call-idx form-id]} (timeline-index/tree-frame-data timeline fidx {})
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
        (when-let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id id)]
          (index-protos/reset-build-stack timeline))))))

(defn fn-call-stats

  "Given a flow-id and optionally thread-id (could be nil) returns all functions stats as a vector of
  maps containing {:keys [fn-ns fn-name form-id form form-def-kind dispatch-val cnt]}"
  
  [flow-id thread-id]
  (let [thread-ids (if thread-id
                     [thread-id]
                     (all-threads-ids flow-id))
        timelines-stats (mapv (fn [tid]
                                (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id tid)
                                      stats-map (index-protos/all-stats timeline)]
                                  {:stats-map stats-map
                                   :thread-id tid})) 
                              thread-ids)]
    (persistent!
     (reduce (fn [stats {:keys [stats-map thread-id]}]
               (reduce (fn [ss [fn-call cnt]]
                         (if-let [form (get-form (:form-id fn-call))]
                           (let [fs (cond-> {:thread-id thread-id
                                            :fn-ns (:fn-ns fn-call)
                                            :fn-name (:fn-name fn-call)
                                            :form-id (:form-id fn-call)
                                            :form (:form/form form)
                                            :form-def-kind (:form/def-kind form)
                                            :dispatch-val (:multimethod/dispatch-val form)
                                            :cnt cnt}
                                      (:multimethod/dispatch-val form) (assoc :dispatch-val (:multimethod/dispatch-val form)))]
                             (conj! ss fs))
                           ss))
                       stats
                       stats-map))
             (transient [])
             timelines-stats))))


(defn make-frame-keeper [flow-id thread-id fn-ns fn-name form-id]
  (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
    (fn [tl-idx tl-entry]
      (when (and (fn-call-trace/fn-call-trace? tl-entry)
                 (if form-id (= form-id (index-protos/get-form-id tl-entry)) true)
                 (if fn-ns   (= fn-ns   (index-protos/get-fn-ns tl-entry))   true)
                 (if fn-name (= fn-name (index-protos/get-fn-name tl-entry)) true))
        (timeline-index/tree-frame-data timeline tl-idx {})))))

(defn find-fn-frames
  
  "Return all the FnCallTraces matching the provided criteria."
  
  ([flow-id thread-id pred]
   
   (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
     (into [] (keep-indexed (fn [tl-idx tl-entry]
                              (when (and (fn-call-trace/fn-call-trace? tl-entry)
                                         (pred tl-entry))
                                (timeline-index/tree-frame-data timeline tl-idx {}))))
           timeline)))
  
  ([flow-id thread-id fn-ns fn-name form-id]
   (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id thread-id)]
     (into [] (keep-indexed (make-frame-keeper flow-id thread-id fn-ns fn-name form-id))
           timeline))))

(defn discard-flow [flow-id] 
  (let [discard-keys (some->> flow-thread-registry
                              index-protos/all-threads 
                              (filter (fn [[fid _]] (= fid flow-id))))]
    
    (when flow-thread-registry
      ;; this will also discard any total-order-timeline if there is one
      (index-protos/discard-threads flow-thread-registry discard-keys)
      (events/publish-event! (events/make-flow-discarded-event flow-id)))))

(defn mark-thread-blocked [flow-id thread-id breakpoint]
  (when flow-thread-registry
    (index-protos/set-thread-blocked flow-thread-registry flow-id thread-id breakpoint)
    (events/publish-event! (events/make-threads-updated-event flow-id (flow-threads-info flow-id)))))

(defn mark-thread-unblocked [flow-id thread-id]
  (when flow-thread-registry
    (index-protos/set-thread-blocked flow-thread-registry flow-id thread-id nil)
    (events/publish-event! (events/make-threads-updated-event flow-id (flow-threads-info flow-id)))))

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
              [tl-idx tl-entry] (if (total-order-timeline/total-order-timeline-entry? tl-entry)
                                  (let [th-timeline (index-protos/tote-thread-timeline tl-entry)
                                        th-tl-idx (index-protos/tote-thread-timeline-idx tl-entry)
                                        th-tl-entry (get th-timeline th-tl-idx)]
                                    [th-tl-idx th-tl-entry])
                                  [i tl-entry])
              ;; we get the thread-id for each entry to support multi-thread-timelines
              tl-thread-id (index-protos/thread-id timeline i)]
          (if-let [e (f tl-thread-id tl-idx tl-entry)]             
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


(defn- find-in-timeline-sub-range

  "Given a timeline tries to find a entry using pred but only between
  from-idx to to-idx. It can also search backwards.
  pred should be a fn of three args (fn [form-id idx entry]) that if matches should return the entry.
  When needs-form-id? is false (default) form-id will be nil. Done for perf reasons.
  If the pred matches, returns the immutable version of the entry."
  
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
            (if-let [r (pred form-id idx entry)]
              (-> r
                  index-protos/as-immutable
                  (ensure-indexes idx))
              (recur (next-idx idx)))))))))

(defn timelines-async-interruptible-find-entry

  "Search all timelines entries with pred.
  
  If flow-id and thread-id are provided, search will only be restricted to that thread timeline.
  pred should be a function of three arguments form-id, idx and the entry that if matches should return the entry.
  form-id will be nil by default unless needs-form-id? is true.
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
                                                flow-id (index-protos/flow-id timeline)
                                                thread-id (index-protos/thread-id timeline curr-idx)
                                                to-idx (if backward?
                                                         (max (- curr-idx batch-size) 0)
                                                         (min (+ curr-idx batch-size) (count timeline)))
                                                entry (find-in-timeline-sub-range timeline curr-idx to-idx pred {:backward? backward? :needs-form-id? needs-form-id?})]
                                            (if entry
                                              
                                              ;; if we found the entry report the match and finish
                                              (on-match (assoc entry
                                                               :flow-id flow-id
                                                               :thread-id thread-id))
                                              
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
            (let [{:thread/keys [timeline]} (index-protos/get-thread-tracker flow-thread-registry flow-id tid)]
              (when (pos? (count timeline))
                (-> timeline
                    first
                    index-protos/as-immutable
                    (ensure-indexes 0)
                    (assoc :flow-id fid
                           :thread-id tid)) ))))
        (index-protos/all-threads flow-thread-registry)))

(defn build-find-fn-call-entry-predicate [{:keys [fn-ns fn-name form-id args-pred]}]
  (fn [entry-form-id _ tl-entry]
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
    (fn [entry-form-id _ tl-entry]
      (when (and (or (fn-return-trace/fn-return-trace? tl-entry)
                     (expr-trace/expr-trace? tl-entry))
                 (not (identical? (index-protos/get-expr-val tl-entry) :flow-storm.power-step/skip))
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
        
        maybe-print-entry (fn [form-id thread-id entry-idx tl-entry]
                            (when (contains? printers form-id)
                              (let [coords-map (get printers form-id)
                                    coord (index-protos/get-coord-raw tl-entry)]
                                (when (contains? coords-map coord)
                                  ;; we are interested in this coord so lets print it
                                  (let [{:keys [print-length print-level format-str transform-expr-fn]} (get coords-map coord)
                                        transform-expr-fn (or transform-expr-fn identity)
                                        val (index-protos/get-expr-val tl-entry)]
                                    (binding [*print-length* print-length
                                              *print-level* print-level]
                                      (let [transf-expr (transform-expr-fn val)]
                                        {:text (utils/format format-str
                                                             (if (string? transf-expr) ;; don't pr-str strings so we don't escape newlines
                                                               transf-expr
                                                               (pr-str transf-expr)))                                         
                                         :idx entry-idx                                       
                                         :thread-id thread-id})))))))
        threads-stacks (atom {})]
    (fn [thread-id tl-idx tl-entry]
      (let [form-id (when-let [thread-fn-call (first (get @threads-stacks thread-id))]
                      (index-protos/get-form-id thread-fn-call))]
        
        (cond
          (fn-call-trace/fn-call-trace? tl-entry)
          (do
            (swap! threads-stacks (fn [ths-stks] (update ths-stks thread-id conj tl-entry)))
            nil)
          
          (fn-return-trace/fn-end-trace? tl-entry)
          (let [p (maybe-print-entry form-id thread-id tl-idx tl-entry)]
            (swap! threads-stacks (fn [ths-stks] (update ths-stks thread-id pop)))
            p)

          (expr-trace/expr-trace? tl-entry)
          (maybe-print-entry form-id thread-id tl-idx tl-entry))))))

(defn timelines-mod-timestamps
  "Returns a set of maps, each containing the thread timeilne last-modified
  timestamp, which is the last time something was recorded on it."
  []
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

(defn print-threads
  "Prints a table with all :flow-id, :thread-id and :thread-name currently
  found in recordings "
  []
  (->> (all-threads)
       (map #(zipmap [:flow-id :thread-id :thread-name] %))
       pp/print-table))

(defn fn-call-idx
  "Given a ExprTrace, FnReturnTrace or FnUnwindTrace entry return the possition of the FnCallTrace entry
  wrapping it."
  [entry]
  (index-protos/fn-call-idx entry))

(defn get-coord-vec
  "Given a ExprTrace, FnReturnTrace or FnUnwindTrace return its coordinate vector."
  [entry]
  (index-protos/get-coord-vec entry))

(defn get-coord
  "Given a ExprTrace, FnReturnTrace or FnUnwindTrace return its coordinate string."
  [entry]
  (index-protos/get-coord-raw entry))

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

(defn get-form-id
  "Given a FnCallTrace entry return its form-id"
  [entry]
  (index-protos/get-form-id entry))

(defn get-bind-sym-name
  "Given a BindTrace, return its symbol name."
  [entry]
  (index-protos/get-bind-sym-name entry))

(defn get-bind-val
  "Given a BindTrace, return its value."
  [entry]
  (index-protos/get-bind-val entry))

(defn get-fn-call
  "Given a timeline and any index FnCallTrace"
  [timeline idx]
  (timeline-index/get-fn-call timeline idx))

(defn tote-thread-id
  "Given a entry from the total order timeline, returns the thread id of
  the timeline it points to."
  [entry]
  (index-protos/thread-id
   (index-protos/tote-thread-timeline entry)
   0))

(defn tote-timeline-idx
  "Given a entry from the total order timeline, returns the thread timeline index of
  the entry it points to."
  [entry]
  (index-protos/tote-thread-timeline-idx entry))

(defn tote-entry
  "Given a entry from the total order timeline, returns the thread timeline entry
  it points to."
  [entry]
  (let [th-timeline (index-protos/tote-thread-timeline entry)
        th-idx (index-protos/tote-thread-timeline-idx entry)]
    (get th-timeline th-idx)))

(defn get-sub-form-at-coord
  
  "Given a form and a coord inside it returns the sub-form at that coordinate."
  
  [form coord]
  (hansel-utils/get-form-at-coord form coord))

(defn get-sub-form

  "Given a timeline and a idx return it's sub-form"
  
  [timeline idx]
  (let [fn-call-entry (get-fn-call timeline idx)
        tl-entry (get timeline idx)
        form-id (index-protos/get-form-id fn-call-entry)
        expr-coord (when (or (expr-trace/expr-trace? tl-entry)
                             (fn-return-trace/fn-end-trace? tl-entry))
                     (get-coord-vec tl-entry))
        form (:form/form (get-form form-id))]
    (if expr-coord
      (get-sub-form-at-coord form expr-coord)
      form)))

(defn get-form-at-coord [form-id coord]
  (let [form (:form/form (get-form form-id))]
    (hansel-utils/get-form-at-coord form coord)))

(defn find-entry-by-sub-form-pred
  "Find a entry on the timeline that matches a predicate called with
  each entry sub-form."
  [timeline pred]
  (loop [idx 0]
    (when (< idx (count timeline))
      (let [sub-form (get-sub-form timeline idx)]
        (if (pred sub-form)
          (get timeline idx)
          (recur (inc idx)))))))

(defn find-entry-by-sub-form-pred-all-threads
  "Find a entry on all thread timelines for a flow that matches a predicate called with
  each entry sub-form."
  [flow-id pred]
  (some (fn [thread-id]
          (find-entry-by-sub-form-pred (get-timeline flow-id thread-id) pred))
        (all-threads-ids flow-id)))

(defn fn-call-trace?
  "Returns true if x is a FnCallTrace"
  [x]
  (fn-call-trace/fn-call-trace? x))

(defn expr-trace?
  "Returns true if x is a ExprTrace"
  [x]
  (expr-trace/expr-trace? x))

(defn fn-return-trace?
  "Returns true if x is a FnReturnTrace"
  [x]
  (fn-return-trace/fn-return-trace? x))

(defn fn-unwind-trace?
  "Returns true if x is a FnUnwindTrace"
  [x]
  (fn-return-trace/fn-unwind-trace? x))

(defn fn-end-trace?
  "Returns true if `x` is a `fn-return-trace?` or `fn-unwind-trace?`"
  [x]
  (fn-return-trace/fn-end-trace? x))

(defn timeline-flow-id
  "Given a timeline returns its flow-id"
  [timeline]
  (index-protos/flow-id timeline))

(defn timeline-thread-id
  "Given a timeline and a idx, returns the thread-id associated to the entry.
  On single thread timelines all index are going to return the same thread-id, which
  is not the case for total order timelines."
  [timeline idx]
  (index-protos/thread-id timeline idx))

(defn timeline-thread-name
  "Given a timeline and a idx, returns the thread-name associated to the entry.
  On single thread timelines all index are going to return the same thread-id, which
  is not the case for total order timelines."
  [timeline idx]
  (index-protos/thread-name timeline idx))

(defn- get-trasformed-entry-timeline [timeline f]
  (reify
    
    index-protos/TimelineP
    
    (flow-id [_] (index-protos/flow-id timeline))
    (thread-id [_ _] (index-protos/thread-id timeline nil))
    (thread-name [_ _] (index-protos/thread-name timeline nil))

    index-protos/FnCallStatsP

    (all-stats [_] (index-protos/all-stats timeline))

    index-protos/TreeP

    (tree-root-index [_] (index-protos/tree-root-index timeline))
    
    (tree-childs-indexes [_ fn-call-idx] (index-protos/tree-childs-indexes timeline fn-call-idx))

    #?@(:clj
        [clojure.lang.Counted       
         (count [_] (count timeline))

         clojure.lang.Seqable
         (seq [_] (map f (seq timeline)))       

         cp/CollReduce
         (coll-reduce [_ f] (cp/coll-reduce timeline (fn [acc e] (f acc (f e)))))
         
         (coll-reduce [_ f v] (cp/coll-reduce timeline (fn [acc e] (f acc (f e))) v))

         clojure.lang.ILookup
         (valAt [_ k] (f (get timeline k)))
         (valAt [_ k not-found] (if-let [e (get timeline k)]
                                  (f e)
                                  not-found))

         clojure.lang.Indexed
         (nth [_ k] (f (get timeline k)))
         (nth [_ k not-found] (if-let [e (get timeline k)]
                                (f e)
                                not-found))]

        :cljs
        [ICounted
         (-count [_] (count timeline))

         ISeqable
         (-seq [_] (map f (seq timeline)))
         
         IReduce
         (-reduce [_ f]
                  (reduce (fn [acc e] (f acc (f e))) timeline))
         (-reduce [_ f start]
                  (reduce (fn [acc e] (f acc (f e))) start timeline))

         ILookup
         (-lookup [_ k] (f (get timeline k)))
         (-lookup [_ k not-found] (if-let [e (get timeline k)]
                                    (f e)
                                    not-found))

         IIndexed
         (-nth [_ n] (f (get timeline n)))
         (-nth [_ n not-found] (if-let [e (get timeline n)]
                                 (f e)
                                 not-found))])))

(defn get-full-maps-timeline [flow-id thread-id]
  (let [timeline (get-timeline flow-id thread-id)]
    (get-trasformed-entry-timeline
     timeline
     (fn [entry]
       (let [{:keys [form-id type fn-call-idx] :as imm-entry} (as-immutable entry)
             form-id (or form-id (get-form-id (get timeline fn-call-idx)))
             expr-coord (when (or (expr-trace/expr-trace? entry)
                                  (fn-return-trace/fn-end-trace? entry))
                          (get-coord-vec entry))
             form (:form/form (get-form form-id))
             sub-form (if expr-coord
                        (get-sub-form-at-coord form expr-coord)
                        form)
             imm-entry' (assoc imm-entry
                               :sub-form sub-form
                               :form form)
             shallow-pr (fn [v]
                          (binding [*print-length* 5
                                    *print-level* 5]
                            (pr-str v)))]
         (case type
           :fn-call           (assoc imm-entry'
                                     :fn-args-preview (mapv shallow-pr (:fn-args imm-entry'))
                                     :bindings (reduce (fn [bs b]
                                                         (assoc bs (get-bind-sym-name b) (get-bind-val b)))
                                                {}
                                                (get-fn-bindings entry)))
           (:expr :fn-return) (assoc imm-entry' :result-preview (shallow-pr (:result imm-entry')))
           :fn-unwind         (assoc imm-entry' :ex-message     (ex-message (:throwable imm-entry')))))))))

(defn get-referenced-maps-timeline [flow-id thread-id]
  (let [timeline (get-timeline flow-id thread-id)
        reference-value! (fn [v] (:vid (rt-values/reference-value! v)))
        reference-entry! (fn [entry]
                           (case (:type entry)
                             :fn-call   (-> entry (update :fn-args reference-value!) (set/rename-keys {:fn-args :fn-args-ref}))
                             :fn-return (-> entry (update :result reference-value!) (set/rename-keys {:result :result-ref}))
                             :fn-unwind (-> entry (update :throwable reference-value!) (set/rename-keys {:throwable :throwable-ref}))
                             :expr      (-> entry (update :result reference-value!) (set/rename-keys {:result :result-ref}))))]
    (get-trasformed-entry-timeline
     timeline
     (fn [entry]
       (let [{:keys [form-id type fn-call-idx] :as imm-entry} (as-immutable entry)
             form-id (if (= :fn-call type)
                       form-id
                       (get-form-id (get timeline fn-call-idx)))]
         (-> (assoc imm-entry :form-id form-id)           
             reference-entry!))))))

(defn all-flows []
  (reduce (fn [acc [fid tid]]
            (update acc fid (fnil conj []) tid))
          {}
          (all-threads)))

(comment 
  
  (def tl (get-full-maps-timeline 0 70))
  (take 10 tl)
  (get tl 10)

  

  )
