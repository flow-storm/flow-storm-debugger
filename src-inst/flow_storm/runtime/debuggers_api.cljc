(ns flow-storm.runtime.debuggers-api
  (:require [flow-storm.runtime.indexes.api :as index-api]
            [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.json-serializer :as serializer]
            #?@(:clj  [[flow-storm.utils :as utils :refer [log]]]
                :cljs [[flow-storm.utils :as utils :refer [log] :refer-macros [env-prop]]])
            [flow-storm.runtime.events :as rt-events]            
            [flow-storm.runtime.values :as rt-values :refer [reference-value! deref-value]]
            [flow-storm.runtime.outputs :as rt-outputs]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.indexes.total-order-timeline :as total-order-timeline]
            [flow-storm.jobs :as jobs]
            [flow-storm.tracer :as tracer]
            [clojure.string :as str]
            #?@(:clj [[hansel.api :as hansel]                      
                      [hansel.instrument.utils :as hansel-inst-utils]])
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]))

;; Utilities for long interruptible tasks

(def interruptible-tasks
  "A map from task-id -> interrupt-ch"
  (atom {}))

(defn interrupt-all-tasks []
  (doseq [[task-id {:keys [interrupt]}] @interruptible-tasks]
    (interrupt)
    (swap! interruptible-tasks dissoc task-id)))

(defn start-task [task-id]
  (let [{:keys [start]} (get @interruptible-tasks task-id)]
    (start)))

(defn submit-interruptible-task [{:keys [task-id start interrupt]}]
  ;; Let's only allow one interruptible task at a time for now
  (interrupt-all-tasks)  
  (swap! interruptible-tasks assoc task-id {:start (fn []
                                                     #?(:clj (future (start))
                                                        :cljs (start))
                                                          #_(println (utils/format "Task %s started" task-id)))
                                            :interrupt (fn []                                                              
                                                         (interrupt)
                                                         (println (utils/format "Task %s interrupted" task-id)))})
  (rt-events/publish-event! (rt-events/make-task-submitted-event task-id))
  task-id)

(defn submit-async-interruptible-batched-timelines-keep-task

  "Submits a timelines batched processing task. See `flow-storm.runtime.indexes.api/async-interruptible-batched-timelines-keep` for
  f docs.
  Doesn't start the task. Returns a task id.
  You can start it by calling `start-task` and interrupt it with `interrupt-task` providing the returned task id.
  Progress and end will be reported through the event system via task-progress-event and task-finished-event.
  task-progress-event will be called after each batch and carry {:keys [flow-id thread-id batch]} which are collected entries for the batch
  just processed. Interruption can only happen between batches.
  
  timelines are the subset of timelines to be processed and
  can be built using `timelines-for`"
  
  [timelines f]
  (let [task-id (str (utils/rnd-uuid))
        task (index-api/async-interruptible-batched-timelines-keep
              f
              timelines
              {:on-batch (fn [batch]
                           (rt-events/publish-event! (rt-events/make-task-progress-event task-id {:batch batch})))
               :on-end (fn []
                         (rt-events/publish-event! (rt-events/make-task-finished-event task-id))
                         (swap! interruptible-tasks dissoc task-id))})]
    (submit-interruptible-task (assoc task :task-id task-id))))

(defn submit-find-interruptible-task
  
  "Submits a timelines find entry tasks. Returns a task id.
  Doesn't start the task.
  You can start it by calling `start-task` and interrupt it with `interrupt-task` providing the
  returned task id.
  Progress, match and end will be reported through the event system via task-progress-event and task-finished-event."
  
  [pred fid-tid-timelines config {:keys [result-transform]}]
  (let [task-id (str (utils/rnd-uuid))
        result-transform (or result-transform identity)
        task (index-api/timelines-async-interruptible-find-entry
              pred
              fid-tid-timelines
              config
              {:on-progress (fn [perc]
                              (rt-events/publish-event! (rt-events/make-task-progress-event task-id {:progress perc})))
               :on-match (fn [match-val]
                           (rt-events/publish-event! (rt-events/make-task-finished-event task-id (result-transform match-val)))
                           (swap! interruptible-tasks dissoc task-id))
               :on-end (fn []
                         (rt-events/publish-event! (rt-events/make-task-finished-event task-id))
                         (swap! interruptible-tasks dissoc task-id))})]
    (submit-interruptible-task (assoc task :task-id task-id))))

#?(:clj
   (defn get-storm-instrumentation [kind]
     {:instrument-only-prefixes (try
                                  (case kind
                                    :clj  (->> (utils/call-jvm-method "clojure.storm.Emitter" "getInstrumentationOnlyPrefixes" [])
                                               (mapv (fn [p] (clojure.lang.Compiler/demunge p))))
                                    :cljs (into [] ((requiring-resolve 'cljs.storm.api/get-instr-prefixes))))
                                  (catch Exception _ []))     
      :skip-prefixes (try
                       (case kind
                         :clj  (->> (utils/call-jvm-method "clojure.storm.Emitter" "getInstrumentationSkipPrefixes" [])
                                    (mapv (fn [p] (clojure.lang.Compiler/demunge p))))
                         :cljs (into [] ((requiring-resolve 'cljs.storm.api/get-skip-prefixes))))
                       (catch Exception _ []))
      :skip-regex (try
                    (case kind
                      :clj  (when-let [pat (utils/call-jvm-method "clojure.storm.Emitter" "getInstrumentationSkipRegex" [])]
                              (.pattern pat))
                      :cljs (when-let [pat ((requiring-resolve 'cljs.storm.api/get-skip-regex))]
                              (.pattern pat)))
                    (catch Exception _ nil))}))

(defn runtime-config []
  (let [storm? (utils/storm-env?)
        env-kind #?(:clj :clj :cljs :cljs)]
    (cond-> {:env-kind env-kind
             :storm? storm?
             :flow-storm-nrepl-middleware? (utils/flow-storm-nrepl-middleware?)
             :recording? (tracer/recording?)
             :total-order-recording? (tracer/multi-timeline-recording?)
             :breakpoints (tracer/all-breakpoints)})))

(defn val-pprint [vref opts]
  (rt-values/val-pprint-ref vref opts))

(defn data-window-push-val-data [dw-id vref {:keys [update? root?] :as extra}]
  (let [v (rt-values/deref-value vref)
        vdata (-> (rt-values/extract-data-aspects v)
                  (merge extra))]
    (rt-events/publish-event!
     (if update?
       (rt-events/make-data-window-update-event dw-id vdata)
       (rt-events/make-data-window-push-val-data-event dw-id vdata root?)))))

#?(:clj (def def-value rt-values/def-value))

(def tap-value rt-values/tap-value)

(defn get-form [form-id]
  (let [form (index-api/get-form form-id)]
    (if (:multimethod/dispatch-val form)
      (update form :multimethod/dispatch-val reference-value!)
      form)))

(defn timeline-count [flow-id thread-id]
  (let [timeline (index-api/get-timeline flow-id thread-id)]
    (count timeline)))

(defn- reference-frame-data! [{:keys [dispatch-val return/kind] :as frame-data}]
  (cond-> frame-data
    true             (dissoc :frame)
    dispatch-val     (update :dispatch-val reference-value!)
    true             (update :args-vec reference-value!)
    (= kind :return) (update :ret reference-value!)
    (= kind :unwind) (update :throwable reference-value!)
    true             (update :bindings (fn [bs] (mapv #(update % :value reference-value!) bs)))
    true             (update :expr-executions (fn [ee] (mapv #(update % :result reference-value!) ee)))))

(defn reference-timeline-entry! [entry]
  (case (:type entry)
    :fn-call   (update entry :fn-args reference-value!)
    :fn-return (update entry :result reference-value!)
    :fn-unwind (update entry :throwable reference-value!)
    :bind      (update entry :value reference-value!)
    :expr      (update entry :result reference-value!)))

(defn timeline-entry [flow-id thread-id idx drift]
  (some-> (index-api/timeline-entry flow-id thread-id idx drift)
          reference-timeline-entry!)) 

(defn multi-thread-timeline-count [flow-id]
  (count (index-api/total-order-timeline flow-id)))

(defn frame-data [flow-id thread-id idx opts]
  (let [frame-data (index-api/frame-data flow-id thread-id idx opts)]
    (reference-frame-data! frame-data)))

(defn bindings [flow-id thread-id idx opts]
  (let [bs-map (index-api/bindings flow-id thread-id idx opts)]
    (reduce-kv (fn [bs s v]
                 (assoc bs s (reference-value! v)))
               {}
               bs-map)))

(defn callstack-tree-root-node [flow-id thread-id]
  (index-api/callstack-root-node flow-id thread-id))

(defn callstack-node-childs [[flow-id thread-id fn-call-idx]]
  (index-api/callstack-node-childs flow-id thread-id fn-call-idx))

(defn callstack-node-frame [[flow-id thread-id fn-call-idx]]
  (let [frame-data (index-api/callstack-node-frame-data flow-id thread-id fn-call-idx)]
    (reference-frame-data! frame-data)))

(defn fn-call-stats [flow-id thread-id]
  (let [stats (->> (index-api/fn-call-stats flow-id thread-id)
                   (mapv (fn [fstats]
                           (update fstats :dispatch-val reference-value!))))]
    stats))

(defn collect-fn-frames-task [flow-id thread-id fn-ns fn-name form-id render-args render-ret?]  
  (let [frame-keeper (index-api/make-frame-keeper flow-id thread-id fn-ns fn-name form-id)
        print-opts {:print-length 3
                    :print-level  3
                    :print-meta?  false
                    :pprint?      false}]    
    (submit-async-interruptible-batched-timelines-keep-task
     (index-api/timelines-for {:flow-id flow-id :thread-id thread-id})
     (fn [_ tl-entry]
       (when-let [{:keys [args-vec ret throwable] :as fn-frame} (frame-keeper tl-entry)]
         (let [fr (-> fn-frame
                      reference-frame-data!
                      (assoc :args-vec-str  (:val-str (rt-values/val-pprint args-vec (assoc print-opts :nth-elems render-args)))))]
           
           (if render-ret?
             (if throwable
               (assoc fr :throwable-str (ex-message throwable))
               (assoc fr :ret-str       (:val-str (rt-values/val-pprint ret print-opts))))
             fr)))))))

(defn find-fn-call-task [fq-fn-call-symb from-idx {:keys [backward? flow-id]}]
  (let [criteria (cond-> {:fn-ns (namespace fq-fn-call-symb)
                          :fn-name (name fq-fn-call-symb)
                          :from-idx from-idx
                          :backward? backward?}
                   flow-id (assoc :flow-id flow-id))]
    (submit-find-interruptible-task (index-api/build-find-fn-call-entry-predicate criteria)
                                    (index-api/timelines-for criteria)
                                    criteria                                    
                                    {:result-transform reference-timeline-entry!})))

(defn find-flow-fn-call [flow-id]
  (some-> (index-api/find-flow-fn-call flow-id)
          reference-timeline-entry!))

(defn find-expr-entry-task [{:keys [identity-val equality-val fn-name] :as criteria}]
  (let [criteria (cond-> criteria
                   identity-val (update :identity-val deref-value)
                   equality-val (update :equality-val deref-value)
                   true         (assoc  :needs-form-id? true))
        search-pred (if fn-name
                      (index-api/build-find-fn-call-entry-predicate criteria)
                      (index-api/build-find-expr-entry-predicate criteria))]
    
    (submit-find-interruptible-task search-pred
                                    (index-api/timelines-for criteria)
                                    criteria                                    
                                    {:result-transform reference-timeline-entry!})))

(defn total-order-timeline-task [{:keys [flow-id only-functions?]}]
  (let [timeline (index-api/total-order-timeline flow-id)
        detail-mapper (total-order-timeline/make-detailed-timeline-mapper index-api/forms-registry)
        keeper (if only-functions?
                 (fn [thread-id tl-entry]
                   (when (fn-call-trace/fn-call-trace? tl-entry)
                     (detail-mapper thread-id tl-entry)))
                 (fn [thread-id tl-entry]
                   (detail-mapper thread-id tl-entry)))]    
    (submit-async-interruptible-batched-timelines-keep-task [timeline] keeper)))

(defn thread-prints-task [{:keys [flow-id thread-id printers]}]
  (let [flow-mt-timeline (index-api/total-order-timeline flow-id)
        use-mt-timeline? (and (nil? thread-id)
                              (pos? (count flow-mt-timeline)))
        tp-keeper (index-api/make-thread-prints-keeper printers)
        timelines (if use-mt-timeline?
                    [flow-mt-timeline]
                    (index-api/timelines-for {:flow-id flow-id :thread-id thread-id}))]
    (submit-async-interruptible-batched-timelines-keep-task
     timelines
     tp-keeper)))

(defn search-collect-timelines-entries-task [{:keys [search-type predicate-code-str query-str val-ref] :as criteria} {:keys [print-length print-level] :or {print-level 2 print-length 10}}]
  (let [keeper (case search-type
                 :pr-str (fn [thread-id tl-entry]
                           (when (or (expr-trace/expr-trace? tl-entry)
                                     (fn-return-trace/fn-return-trace? tl-entry))

                             (let [result (index-protos/get-expr-val tl-entry)
                                   pprint-val (:val-str (rt-values/val-pprint-ref result {:print-length print-length
                                                                                          :print-level print-level
                                                                                          :pprint? false}))]
                               (when (str/includes? pprint-val query-str)
                                 (-> tl-entry
                                     index-protos/as-immutable
                                     reference-timeline-entry!
                                     (assoc :entry-preview pprint-val
                                            :thread-id thread-id))))))
                 :custorm-predicate #?(:clj (let [custom-pred-fn (try
                                                                   (eval (read-string predicate-code-str))
                                                                   (catch Exception _ (constantly false)))]
                                              (fn [thread-id tl-entry]                                                            
                                                (try
                                                  (when (or (expr-trace/expr-trace? tl-entry)
                                                            (fn-return-trace/fn-return-trace? tl-entry))

                                                    (let [result (index-protos/get-expr-val tl-entry)]
                                                      (when (custom-pred-fn result) 
                                                        
                                                        (let [pprint-val (:val-str (rt-values/val-pprint-ref result {:print-length 3
                                                                                                                     :print-level 3
                                                                                                                     :pprint? false}))]
                                                          (-> tl-entry
                                                              index-protos/as-immutable
                                                              reference-timeline-entry!
                                                              (assoc :entry-preview pprint-val
                                                                     :thread-id thread-id))))))
                                                  (catch Exception _ nil))))
                                       :cljs (do #_:clj-kondo/ignore predicate-code-str identity))
                 :val-identity (let [search-val (deref-value val-ref)]
                                 (fn [thread-id tl-entry]
                                   (when (or (expr-trace/expr-trace? tl-entry)
                                             (fn-return-trace/fn-return-trace? tl-entry))

                                     (let [result (index-protos/get-expr-val tl-entry)]
                                       (when (identical? search-val result) 
                                         
                                         (let [pprint-val (:val-str (rt-values/val-pprint-ref result {:print-length 3
                                                                                                      :print-level 3
                                                                                                      :pprint? false}))]
                                           (-> tl-entry
                                               index-protos/as-immutable
                                               reference-timeline-entry!
                                               (assoc :entry-preview pprint-val
                                                      :thread-id thread-id)))))))))]
    (submit-async-interruptible-batched-timelines-keep-task
     (index-api/timelines-for criteria)   
     keeper)))

(def discard-flow index-api/discard-flow)

(def all-flows-threads index-api/all-threads)

(defn clear-flows []
  (let [flows-ids (->> (all-flows-threads)
                       (map first)
                       (into #{}))]
    (doseq [fid flows-ids]
      (discard-flow fid))))

(defn clear-runtime-state []
  (clear-flows)
  (rt-values/clear-vals-ref-registry)
  (rt-outputs/clear-outputs))

(def clear-outputs rt-outputs/clear-outputs)
(def flow-threads-info index-api/flow-threads-info)

(defn goto-location [flow-id {:keys [thread/id thread/idx]}]
  (rt-events/publish-event! (rt-events/make-goto-location-event flow-id id idx)))

(defn stack-for-frame [flow-id thread-id fn-call-idx]
  (index-api/stack-for-frame flow-id thread-id fn-call-idx))

(defn set-recording [enable?]
  (tracer/set-recording enable?)
  (rt-events/publish-event! (rt-events/make-recording-updated-event enable?)))

(defn set-multi-timeline-recording [enable?]
  (tracer/set-multi-timeline-recording enable?)
  (rt-events/publish-event! (rt-events/make-multi-timeline-recording-updated-event enable?)))

(def set-thread-trace-limit tracer/set-thread-trace-limit)

(defn toggle-recording []
  (if (tracer/recording?)
    (do
      (set-recording false)
      (set-multi-timeline-recording false))
    (set-recording true)))

(defn toggle-multi-timeline-recording []
  (if (tracer/multi-timeline-recording?)
    (set-multi-timeline-recording false)
    (do
      (set-multi-timeline-recording true)
      (set-recording true))))

(defn switch-record-to-flow [flow-id]
  (tracer/set-current-flow-id flow-id))

(defn jump-to-last-expression-in-this-thread []
  (let [flow-id (tracer/get-current-flow-id)
        thread-id (utils/get-current-thread-id)
        last-ex-loc (when-let [cnt (count (index-api/get-timeline flow-id thread-id))]
                      {:thread/id thread-id
                       :thread/name (utils/get-current-thread-name)
                       :thread/idx (dec cnt)})]
    (if last-ex-loc
      (goto-location flow-id last-ex-loc)
      (log "No recordings for this thread yet"))))

#?(:clj
   (defn unblock-thread [thread-id]     
     (tracer/unblock-thread thread-id)))

#?(:clj
   (defn unblock-all-threads []
     (tracer/unblock-all-threads)))

#?(:clj
   (defn add-breakpoint!
     ([fq-fn-symb opts] (add-breakpoint! fq-fn-symb opts (constantly true)))
     ([fq-fn-symb {:keys [disable-events?]} args-pred]
      (tracer/add-breakpoint! (namespace fq-fn-symb) (name fq-fn-symb) args-pred)
      (when-not disable-events?
        (rt-events/publish-event! (rt-events/make-break-installed-event fq-fn-symb))))))

#?(:clj
   (defn remove-breakpoint! [fq-fn-symb {:keys [disable-events?]}]
     (tracer/remove-breakpoint! (namespace fq-fn-symb) (name fq-fn-symb))

     (when-not disable-events?
       (rt-events/publish-event! (rt-events/make-break-removed-event fq-fn-symb)))))

#?(:clj
   (defn clear-breakpoints! []
     (let [brks (tracer/all-breakpoints)]
       (tracer/clear-breakpoints!)
       (doseq [[fn-ns fn-name] brks]
         (rt-events/publish-event! (rt-events/make-break-removed-event (symbol fn-ns fn-name)))))))

(defn ping [] :pong)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; To be used form the repl connections ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn publish-event! [kind ev config]
     ;; do it on a different thread since we
     ;; don't want to block for event publication
     (future
       (case kind
         :clj (rt-events/publish-event! ev)

         ;; sending a event for ClojureScript from the Clojure (shadow) side is kind of
         ;; hacky. We evaluate the publish-event! expresion in the cljs runtime.
         :cljs (hansel-inst-utils/eval-in-ns-fn-cljs 'cljs-user
                                                     `(flow-storm.runtime.events/publish-event! ~ev)
                                                     config)))
     nil))

#?(:clj
   (defn vanilla-instrument-var [kind var-symb {:keys [disable-events?] :as config}]
     (let [inst-fn (case kind
                     :clj  hansel/instrument-var-clj
                     :cljs hansel/instrument-var-shadow-cljs)]
       
       (log (format "Instrumenting var %s %s" var-symb config))

       (inst-fn var-symb (merge (tracer/hansel-config config)
                                config))
       
       (when-not disable-events?
         (publish-event! kind (rt-events/make-vanilla-var-instrumented-event (name var-symb) (namespace var-symb)) config)))))

#?(:clj
   (defn vanilla-uninstrument-var [kind var-symb {:keys [disable-events?] :as config}]
     (let [inst-fn (case kind
                     :clj  hansel/uninstrument-var-clj
                     :cljs hansel/uninstrument-var-shadow-cljs)]

       (log (format "Uninstrumenting var %s %s" var-symb config))
       (inst-fn var-symb (merge (tracer/hansel-config config)
                                config))
       (when-not disable-events?
         (publish-event! kind (rt-events/make-vanilla-var-uninstrumented-event (name var-symb) (namespace var-symb)) config)))))

#?(:clj
   (defn vanilla-instrument-namespaces [kind ns-prefixes {:keys [disable-events?] :as config}]
     (log (format "Instrumenting namespaces %s" (pr-str ns-prefixes)))
     (let [inst-fn (case kind
                     :clj  hansel/instrument-namespaces-clj
                     :cljs hansel/instrument-namespaces-shadow-cljs)
           {:keys [affected-namespaces]} (inst-fn ns-prefixes (merge (tracer/hansel-config config)
                                                                     config))]
       
       (when-not disable-events?
         (doseq [ns-symb affected-namespaces]
           (publish-event! kind (rt-events/make-vanilla-ns-instrumented-event (name ns-symb)) config))))))

#?(:clj
   (defn vanilla-uninstrument-namespaces [kind ns-prefixes {:keys [disable-events?] :as config}]
     (log (format "Uninstrumenting namespaces %s" (pr-str ns-prefixes)))         
     (let [inst-fn (case kind
                     :clj  hansel/uninstrument-namespaces-clj
                     :cljs hansel/uninstrument-namespaces-shadow-cljs)
           {:keys [affected-namespaces]} (inst-fn ns-prefixes (merge (tracer/hansel-config config)
                                                                     config))]
       
       (when-not disable-events?
         (doseq [ns-symb affected-namespaces]
           (publish-event! kind (rt-events/make-vanilla-ns-uninstrumented-event (name ns-symb)) config))))))

#?(:clj
   (defn modify-storm-instrumentation [kind {:keys [inst-kind op prefix regex]} {:keys [disable-events?] :as config}]
     (case kind
       :clj  (let [[method args] (cond
                                   (and (= inst-kind :inst-only-prefix) (= op :add)) ["addInstrumentationOnlyPrefix"    [prefix]]
                                   (and (= inst-kind :inst-skip-prefix) (= op :add)) ["addInstrumentationSkipPrefix"    [prefix]]
                                   (and (= inst-kind :inst-skip-regex)  (= op :set)) ["setInstrumentationSkipRegex"     [regex]]
                                   (and (= inst-kind :inst-only-prefix) (= op :rm))  ["removeInstrumentationOnlyPrefix" [prefix]]
                                   (and (= inst-kind :inst-skip-prefix) (= op :rm))  ["removeInstrumentationSkipPrefix" [prefix]]
                                   (and (= inst-kind :inst-skip-regex)  (= op :rm))  ["removeInstrumentationSkipRegex"  []])]
               (utils/call-jvm-method "clojure.storm.Emitter" method args))
       
       :cljs (cond
               (and (= inst-kind :inst-only-prefix) (= op :add)) ((requiring-resolve 'cljs.storm.api/add-instr-only-prefix) prefix)
               (and (= inst-kind :inst-skip-prefix) (= op :add)) ((requiring-resolve 'cljs.storm.api/add-instr-skip-prefix) prefix)
               (and (= inst-kind :inst-skip-regex)  (= op :set)) ((requiring-resolve 'cljs.storm.api/set-instr-skip-regex)  regex)
               (and (= inst-kind :inst-only-prefix) (= op :rm))  ((requiring-resolve 'cljs.storm.api/rm-instr-only-prefix)  prefix)
               (and (= inst-kind :inst-skip-prefix) (= op :rm))  ((requiring-resolve 'cljs.storm.api/rm-instr-skip-prefix)  prefix)
               (and (= inst-kind :inst-skip-regex)  (= op :rm))  ((requiring-resolve 'cljs.storm.api/rm-instr-skip-regex))))
     (when-not disable-events?
       (publish-event! kind (rt-events/make-storm-instrumentation-updated-event (get-storm-instrumentation kind)) config))))

#?(:clj
   (defn all-namespaces
     ([kind] (all-namespaces kind nil))
     ([kind build-id]
      (case kind
        :clj (mapv (comp str ns-name) (all-ns))
        :cljs (hansel-inst-utils/cljs-get-all-ns build-id)))))

#?(:clj
   (defn all-vars-for-namespace
     ([kind ns-name] (all-vars-for-namespace kind ns-name nil))
     ([kind ns-name build-id]
      (case kind
        :clj (->> (ns-interns (symbol ns-name)) keys (mapv str))
        :cljs (hansel-inst-utils/cljs-get-ns-interns (symbol ns-name) build-id)))))

#?(:clj
   (defn get-var-meta
     ([kind var-symb] (get-var-meta kind var-symb {}))
     ([kind var-symb {:keys [build-id]}]
      (case kind
        :clj (-> (meta (find-var var-symb)) (update :ns (comp str ns-name)))
        :cljs (hansel-inst-utils/eval-in-ns-fn-cljs 'cljs.user `(meta (var ~var-symb)) {:build-id build-id})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils for calling by name, used by the websocket api calls ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def api-fn {:runtime-config runtime-config
             :val-pprint val-pprint
             :data-window-push-val-data data-window-push-val-data
             :get-form get-form
             :timeline-count timeline-count
             :timeline-entry timeline-entry
             :multi-thread-timeline-count multi-thread-timeline-count
             :frame-data frame-data
             :bindings bindings
             :callstack-tree-root-node callstack-tree-root-node
             :callstack-node-childs callstack-node-childs
             :callstack-node-frame callstack-node-frame
             :fn-call-stats fn-call-stats

             ;; collectors tasks
             :collect-fn-frames-task collect-fn-frames-task
             :total-order-timeline-task total-order-timeline-task
             :thread-prints-task thread-prints-task

             ;; finders tasks
             :search-collect-timelines-entries-task search-collect-timelines-entries-task
             :find-expr-entry-task find-expr-entry-task
             :find-fn-call-task find-fn-call-task

             :discard-flow discard-flow             
             :tap-value tap-value
             :interrupt-all-tasks interrupt-all-tasks
             :start-task start-task
             :clear-runtime-state clear-runtime-state
             :clear-outputs clear-outputs
             :flow-threads-info flow-threads-info
             :all-flows-threads all-flows-threads
             :stack-for-frame stack-for-frame
             :toggle-recording toggle-recording
             :toggle-multi-timeline-recording toggle-multi-timeline-recording
             :switch-record-to-flow switch-record-to-flow
             :set-thread-trace-limit set-thread-trace-limit
             :ping ping
             #?@(:clj
                 [:def-value def-value
                  :all-namespaces all-namespaces
                  :all-vars-for-namespace all-vars-for-namespace
                  :get-var-meta get-var-meta
                  
                  :vanilla-instrument-var vanilla-instrument-var
                  :vanilla-uninstrument-var vanilla-uninstrument-var
                  :vanilla-instrument-namespaces vanilla-instrument-namespaces
                  :vanilla-uninstrument-namespaces vanilla-uninstrument-namespaces

                  :get-storm-instrumentation get-storm-instrumentation
                  :modify-storm-instrumentation modify-storm-instrumentation
                  
                  :unblock-thread unblock-thread
                  :unblock-all-threads unblock-all-threads
                  :add-breakpoint! add-breakpoint!
                  :remove-breakpoint! remove-breakpoint!])})

(defn call-by-name [fun-key args]  
  (let [f (get api-fn fun-key)]
    (apply f args)))

(defn runtime-started? []
  (index-api/indexes-started?))

#?(:clj
   (defn start-runtime
     "Start the runtime. Will not do anything if the runtime has been already started."

     []
     
     ;; NOTE: The order here is important until we replace this code with
     ;; better component state management

     (if (runtime-started?)
       
       (log "Runtime already started, skipping ...")
       
       (let [_ (log "Starting up runtime")
             fn-call-limits (utils/parse-thread-fn-call-limits (System/getProperty "flowstorm.threadFnCallLimits"))]

         (tracer/set-recording (if (= (System/getProperty "flowstorm.startRecording") "true") true false))

         (doseq [[fn-ns fn-name l] fn-call-limits]
           (index-api/add-fn-call-limit fn-ns fn-name l))
         
         (index-api/start)

         (rt-values/clear-vals-ref-registry)

         (rt-outputs/setup-tap!)

         (jobs/run-jobs)
         
         (log "Runtime started"))))

   :cljs
   (defn start-runtime

     "This is meant to be called by preloads to initialize the runtime side of things.
  Will not do anything if the runtime has been already started."

     []
     (if (runtime-started?)
       
       (log "Runtime already started, skipping ...")

       (do
         (println "Starting up runtime")

         (index-api/start)

         (let [recording? (if (= (env-prop "flowstorm.startRecording") "true") true false)]
           (tracer/set-recording recording?)
           (println "Recording set to " recording?))

         (let [fn-call-limits (utils/parse-thread-fn-call-limits (env-prop "flowstorm.threadFnCallLimits"))]
           (doseq [[fn-ns fn-name l] fn-call-limits]
             (index-api/add-fn-call-limit fn-ns fn-name l)))

         (rt-values/clear-vals-ref-registry)
         
         (rt-outputs/setup-tap!)
         (jobs/run-jobs)
         
         (println "Runtime started")))))

(defn stop-runtime []

  (interrupt-all-tasks)
  
  (jobs/stop-jobs)

  (rt-outputs/remove-tap!)
  
  (rt-outputs/clear-outputs)

  (rt-values/clear-vals-ref-registry)

  (rt-events/clear-pending-events!)
  
  (index-api/clear-fn-call-limits)

  (remote-websocket-client/stop-remote-websocket-client)

  (tracer/clear-breakpoints!)

  (index-api/stop))

#?(:clj
   (defn remote-connect [config]

     (start-runtime)
     
     ;; connect to the remote websocket
     (remote-websocket-client/start-remote-websocket-client
      (assoc config
             :api-call-fn call-by-name
             :on-connected (fn []
                             (let [enqueue-event! (fn [ev]
                                                    (-> [:event ev]
                                                        serializer/serialize
                                                        remote-websocket-client/send))]
                               (log "Connected to remote websocket")

                               (rt-events/set-dispatch-fn enqueue-event!)

                               (log "Remote Clojure runtime initialized"))))))

   :cljs ;;----------------------------------------------------------------------------------------------
   (defn remote-connect [config]
     ;; connect to the remote websocket
     
     (try
       (remote-websocket-client/start-remote-websocket-client
        (assoc config               
               :api-call-fn call-by-name
               :on-connected (fn []
                               ;; subscribe and automatically push all events thru the websocket
                               ;; if there were any events waiting to be dispatched
                               (rt-events/set-dispatch-fn
                                (fn [ev]
                                  (-> [:event ev]
                                      serializer/serialize
                                      remote-websocket-client/send)))

                               (println "Debugger connection ready. Events dispatch function set and pending events pushed."))))
       (catch :default e (utils/log-error "Couldn't connect to the debugger" e))))
   
)
