(ns flow-storm.runtime.indexes.api
  
  "You can use this namespace to work with your recordings from the repl.

  Find more documentation on the docstrings of each specific function.
  
  From the UI you can retrieve the flow-id and thread-id of your recordings
  which you will need for accessing it from the repl.

  TIMELINES
  ---------
  
  Let's say you want to explore recordings on thread 18, you can retrieve the timeline
  by using `get-timeline` like this :

  (def timeline (get-timeline 18))

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
  
  If you have recorded a multi-thread timeline, you can retrieve with `total-order-timeline` like this :

  (def mt-timeline (total-order-timeline))

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
            [flow-storm.runtime.indexes.fn-call-stats-index :as fn-call-stats-index]
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

(def flow-thread-registry
  
  "Registry that contains all flows and threads timelines.
  It is an instance of `flow-storm.runtime.indexes.thread-registry/ThreadRegistry`."

  nil)

(def forms-registry
  
  "Registry that contains all registered forms.
  It could be anything that implements `flow-storm.runtime.indexes.protocols/FormRegistryP`
  
  Currently it can be an instance of `flow-storm.runtime.indexes.thread-registry/FormRegistry`
  or `clojure.storm/FormRegistry` when working with ClojureStorm."
  
  nil)

(def fn-call-limits

  "Stores the function calls limits for different functions."
  
  (atom nil))

(defn add-fn-call-limit [fn-ns fn-name limit]
  (swap! fn-call-limits assoc-in [fn-ns fn-name] limit))

(defn rm-fn-call-limit [fn-ns fn-name]
  (swap! fn-call-limits update fn-ns dissoc fn-name))

(defn get-fn-call-limits []
  @fn-call-limits)

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

    (utils/log "Warning, trying to register a form before FlowStorm startup. If you have #trace tags on your code you will have to evaluate them again after starting the debugger.")))

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
  (let [thread-indexes {:timeline-index (timeline-index/make-index)
                        :fn-call-stats-index (fn-call-stats-index/make-index)
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
  ;; create the `nil` (funnel) flow if it doesn't exist
   (when (and (nil? flow-id) (not (flow-exists? nil)))
     (create-flow {:flow-id nil}))
  
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
  ;; On ClojureScript we want to start the system sometimes before
  ;; the debugger gets connected, so we can capture what happens right after
  ;; a page reload. The first thing that happens when tracing is form registration,
  ;; so we hook that start path here.
  #?(:cljs (when-not flow-thread-registry (start)))
  (register-form trace))

(defn add-fn-call-trace [flow-id thread-id thread-name fn-ns fn-name form-id args total-order-recording?]
  (let [{:keys [timeline-index fn-call-stats-index fn-call-limits thread-limited]} (get-or-create-thread-indexes flow-id thread-id thread-name form-id)]
    (when timeline-index
      (if-not @thread-limited
        
        (if-not (check-fn-limit! fn-call-limits fn-ns fn-name)
          ;; if we are not limited, go ahead and record fn-call
          (do
            (let [tl-idx (index-protos/add-fn-call timeline-index fn-ns fn-name form-id args)]
              (when (and tl-idx total-order-recording?)
                (index-protos/record-total-order-entry flow-thread-registry flow-id thread-id (get timeline-index tl-idx))))
            
            (when fn-call-stats-index
              (index-protos/add-fn-call fn-call-stats-index fn-ns fn-name form-id args)))

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
            (index-protos/record-total-order-entry flow-thread-registry flow-id thread-id (get timeline-index tl-idx))))

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
                throwable throwable
                ev (events/make-function-unwinded-event {:flow-id flow-id
                                                         :thread-id thread-id
                                                         :idx tl-idx
                                                         :fn-ns fn-ns
                                                         :fn-name fn-name
                                                         :ex-type (pr-str (type throwable))
                                                         :ex-message (ex-message throwable)})]

            (when (and tl-idx total-order-recording?)
              (index-protos/record-total-order-entry flow-thread-registry flow-id thread-id (get timeline-index tl-idx)))

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
          (index-protos/record-total-order-entry flow-thread-registry flow-id thread-id (get timeline-index tl-idx)))))))

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

  "Given a flow-id and thread-id returns all functions stats as a vector of
  maps containing {:keys [fn-ns fn-name form-id form form-def-kind dispatch-val cnt]}"
  
  [flow-id thread-id]
  (let [{:keys [fn-call-stats-index]} (get-thread-indexes flow-id thread-id)]
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
          (index-protos/all-stats fn-call-stats-index))))

(defn find-fn-frames
  
  "Return all the FnCallTraces matching the provided criteria."
  
  ([flow-id thread-id pred]
   (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
     (persistent!
      (reduce (fn [r tl-entry]
                (if (and (fn-call-trace/fn-call-trace? tl-entry)
                         (pred tl-entry))
                  (conj! r (timeline-index/tree-frame-data timeline-index (index-protos/entry-idx tl-entry) {}))
                  r))
              (transient [])
              timeline-index))))
  ([flow-id thread-id fn-ns fn-name form-id]
   (find-fn-frames flow-id thread-id
                   (fn [fn-call]
                     (and (if form-id (= form-id (index-protos/get-form-id fn-call)) true)
                          (if fn-ns   (= fn-ns   (index-protos/get-fn-ns fn-call))   true)
                          (if fn-name (= fn-name (index-protos/get-fn-name fn-call)) true))))))

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

(defn find-fn-call [fq-fn-call-symb from-idx {:keys [backward?]}]  
  (some (fn [[flow-id thread-id]]
          (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)]
            (when-let [fn-call (timeline-index/timeline-find-entry
                                timeline-index
                                from-idx
                                backward?
                                (fn [_ entry]
                                  (and (fn-call-trace/fn-call-trace? entry)
                                       (= (index-protos/get-fn-ns entry)   (namespace fq-fn-call-symb))
                                       (= (index-protos/get-fn-name entry) (name fq-fn-call-symb)))))]
              (assoc fn-call
                     :flow-id flow-id
                     :thread-id thread-id))))
        (index-protos/all-threads flow-thread-registry)))

(defn find-flow-fn-call [flow-id]
  (some (fn [[fid tid]]
          (when (= flow-id fid)
            (let [{:keys [timeline-index]} (get-thread-indexes fid tid)]
              (when-let [fn-call (timeline-index/timeline-find-entry timeline-index
                                                                   0
                                                                   false
                                                                   (fn [_ entry]
                                                                     (fn-call-trace/fn-call-trace? entry)))]
                (assoc fn-call
                       :flow-id fid
                       :thread-id tid)))))
        (index-protos/all-threads flow-thread-registry)))

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
  - collect?, when true return a collection of all matches, otherwise return on first match. Defaults to false.

  Absent criteria that doesn't have a default value will always match."
  
  [{:keys [flow-id thread-id backward? from-idx form-id fn-ns fn-name args-pred collect?] :as criteria}]
  (let [search-pred (fn [entry-form-id tl-entry]
                      (and (fn-call-trace/fn-call-trace? tl-entry)
                           (if form-id      (= form-id entry-form-id)                    true)
                           (if fn-ns (= (index-protos/get-fn-ns tl-entry) fn-ns)         true)
                           (if fn-name (= (index-protos/get-fn-name tl-entry) fn-name)   true)
                           (if args-pred (args-pred (index-protos/get-fn-args tl-entry)) true)))
        matcher (fn [[fid tid]]
                  (when (and (or (not (contains? criteria :flow-id))
                                 (= flow-id fid))
                             (or (not (contains? criteria :thread-id))
                                 (= thread-id tid)))
                    (let [{:keys [timeline-index]} (get-thread-indexes fid tid)
                          from-idx (or from-idx (if backward? (dec (count timeline-index)) 0))]
                      (when-let [entry (timeline-index/timeline-find-entry timeline-index
                                                                           from-idx
                                                                           backward?
                                                                           search-pred)]
                        (assoc entry :flow-id fid :thread-id tid)))))]
    (if collect?
      (keep matcher (index-protos/all-threads flow-thread-registry))
      (some matcher (index-protos/all-threads flow-thread-registry)))))

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
  - coord, a vector with a coordinate to match, like [3 1 2].
  - custom-pred-form, a string with a form to use as a custom predicate over expression values, like \"(fn [v] (map? v))\"
  - skip-threads, a set of threads ids to skip.
  - collect?, when true return a collection of all matches, otherwise return on first match. Defaults to false.
  
  Absent criteria that doesn't have a default value will always match.
  "
  
  [{:keys [flow-id thread-id from-idx identity-val equality-val custom-pred-form coord form-id backward? skip-threads collect?] :as criteria}]
  
  (try
    (let [coord (when coord (utils/stringify-coord coord))
          custom-pred-fn #?(:clj (when custom-pred-form (eval (read-string custom-pred-form)))
                            :cljs (do
                                    (utils/log (str "Custom stepping is not supported in ClojureScript yet " custom-pred-form))
                                    (constantly true)))
          
          search-pred (fn [entry-form-id tl-entry]
                        (and (or (fn-return-trace/fn-return-trace? tl-entry)
                                 (expr-trace/expr-trace? tl-entry))                             
                             (if identity-val (identical? (index-protos/get-expr-val tl-entry) identity-val) true)
                             (if equality-val (= (index-protos/get-expr-val tl-entry) equality-val)          true)
                             (if coord        (= coord (index-protos/get-coord-raw tl-entry))                true)
                             (if form-id      (= form-id entry-form-id)                                      true)
                             (if custom-pred-fn (custom-pred-fn (index-protos/get-expr-val tl-entry))        true)))
          matcher (fn [[fid tid]]
                    (when (and (or (not (contains? criteria :flow-id))
                                   (= flow-id fid))
                               (or (not (contains? criteria :thread-id))
                                   (= thread-id tid))
                               (not (and (set? skip-threads)
                                         (skip-threads tid))))
                      (let [{:keys [timeline-index]} (get-thread-indexes fid tid)
                            from-idx (or from-idx (if backward? (dec (count timeline-index)) 0))]                  
                        (when-let [entry (timeline-index/timeline-find-entry timeline-index
                                                                             from-idx
                                                                             backward?
                                                                             search-pred)]
                          (assoc entry :flow-id fid :thread-id tid)))))]
      (if collect?
        (keep matcher (index-protos/all-threads flow-thread-registry))
        (some matcher (index-protos/all-threads flow-thread-registry))))
    #?(:clj (catch Exception e (utils/log "Exception searching for timeline entry" (.getMessage e)))
       :cljs (catch js/Error e (utils/log "Exception searching for timeline entry" (.-message e))))))

(defn total-order-timeline
  
  "Retrieves the total order timeline if there is one recorded.
  Look at this namespace docstring for more info."
  
  []
  (index-protos/total-order-timeline flow-thread-registry))

(defn detailed-total-order-timeline []
  (let [timeline (total-order-timeline)]
    (total-order-timeline/build-detailed-timeline timeline forms-registry)))

(defn thread-prints [{:keys [flow-id thread-id printers]}]
  ;; printers is a map of {form-id {coord-vec-1 {:format-str :print-length :print-level}}}
  (let [{:keys [timeline-index]} (get-thread-indexes flow-id thread-id)
        printers (utils/update-values
                  printers
                  (fn [corrds-map]
                    (utils/update-keys
                     corrds-map
                     (fn [coord-vec]
                       (let [scoord (str/join "," coord-vec)]
                         #?(:cljs scoord
                            :clj (.intern scoord)))))))
        maybe-print-entry (fn [prints-so-far curr-fn-call tl-entry]
                            (let [form-id (index-protos/get-form-id curr-fn-call)]
                              (if (contains? printers form-id)
                                (let [coords-map (get printers form-id)
                                      coord (index-protos/get-coord-raw tl-entry)]
                                  (if (contains? coords-map coord)
                                    ;; we are interested in this coord so lets print it
                                    (let [{:keys [print-length print-level format-str]} (get coords-map coord)
                                          val (index-protos/get-expr-val tl-entry)]
                                      (binding [*print-length* print-length
                                                *print-level* print-level]
                                        (conj! prints-so-far {:text (utils/format format-str (pr-str val))
                                                              :idx (index-protos/entry-idx tl-entry)})))
                                    
                                    ;; else skip if we aren't interested in this coord))
                                    prints-so-far))
                                
                                ;; else skip if we aren't interested in this form id
                                prints-so-far)))]
    (locking timeline-index
      (loop [idx 0
             thread-stack ()
             prints (transient [])]
        (if-not (< idx (count timeline-index))
          
          (persistent! prints)
          
          (let [tl-entry (get timeline-index idx)]
            (cond
             (fn-call-trace/fn-call-trace? tl-entry)
             (recur (inc idx) (conj thread-stack tl-entry) prints)

             (fn-return-trace/fn-end-trace? tl-entry)
             (recur (inc idx) (pop thread-stack) (maybe-print-entry prints (first thread-stack) tl-entry))

             (expr-trace/expr-trace? tl-entry)
             (recur (inc idx) thread-stack (maybe-print-entry prints (first thread-stack) tl-entry)))))))))

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

(defn tote-flow-id
  [entry]
  (index-protos/tote-flow-id entry))

(defn tote-thread-id
  [entry]
  (index-protos/tote-thread-id entry))

(defn tote-entry
  [entry]
  (index-protos/tote-entry entry))

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
