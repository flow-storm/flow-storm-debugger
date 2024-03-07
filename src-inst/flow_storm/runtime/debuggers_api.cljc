(ns flow-storm.runtime.debuggers-api
  (:require [flow-storm.runtime.indexes.api :as index-api]
            [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.json-serializer :as serializer]
            #?@(:clj  [[flow-storm.utils :as utils :refer [log]]]
                :cljs [[flow-storm.utils :as utils :refer [log] :refer-macros [env-prop]]])
            [flow-storm.runtime.events :as rt-events]            
            [flow-storm.runtime.values :as rt-values :refer [reference-value! deref-value]]
            [flow-storm.runtime.taps :as rt-taps]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.indexes.total-order-timeline :as total-order-timeline]
            #?@(:clj [[flow-storm.mem-reporter :as mem-reporter]])
            [flow-storm.tracer :as tracer]
            [clojure.string :as str]
            #?@(:clj [[hansel.api :as hansel]                      
                      [hansel.instrument.utils :as hansel-inst-utils]])
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]))

;; TODO: build script
;; Maybe we can figure out this ns names by scanning (all-ns) so
;; we don't need to list them here
;; Also maybe just finding main is enough, we can add to it a fn
;; that returns the rest of the functions we need
(def debugger-main-ns 'flow-storm.debugger.main)

;; Utilities for long interruptible tasks

(def interruptible-tasks
  "A map from task-id -> interrupt-ch"
  (atom {}))

(defn interrupt-all-tasks []
  (doseq [[task-id {:keys [interrupt-task]}] @interruptible-tasks]
    (interrupt-task)
    (swap! interruptible-tasks dissoc task-id)))

(defn start-task [task-id]
  (let [{:keys [start-task]} (get @interruptible-tasks task-id)]
    (start-task)))

(defn- transduce-sub-range

  "Given a coll return the sub range [from to] transduced with xf."

  [coll from to xf]
  
  (when (<= 0 from to (count coll))
    ;; this locking is here to support the timeline which is mutable,
    ;; but I think the timeline should expose a way of iterating sub sequences
    (locking coll 
      (let [rf (fn
                 ([acc] (persistent! acc))
                 ([acc tle] (conj! acc tle)))
            f (xf rf)]
        (loop [i from
               batch-res (transient [])]
          (if (< i to)
            (let [tl (get coll i)
                  batch-res' (f batch-res tl)]
              (if (reduced? batch-res')
                batch-res'
                (recur (inc i) batch-res')))
            (f batch-res)))))))

(defn- async-interruptible-batched-collect

  "Process coll with xf in batches of size batch-size. Returns a function
  which can be used to interrupt the collection processing between batches.
  on-batch should be a callback of 1 arg, that will be called with the result
  of processing each batch.
  on-end will be called with no args for signaling the end."

  [coll batch-size xf {:keys [on-batch on-end]}]

  (let [interrupted? (atom false)
        interrupt-task (fn interrupt-task [] (reset! interrupted? true))
        start-task (fn start-task []
                     (let [collect-next-batch (fn collect-next-batch [from]
                                                (if-not @interrupted?
                                                  (if (< from (count coll))
                                                    (let [to (min (+ from batch-size) (count coll))
                                                          batch-res (transduce-sub-range coll from to xf)]
                                                      (if (reduced? batch-res)
                                                        (do
                                                          (on-batch (deref batch-res))
                                                          (on-end))
                                                        (do
                                                          (on-batch batch-res)
                                                          #?(:clj (recur to)
                                                             :cljs (js/setTimeout collect-next-batch 0 to)))))
                                                    (on-end))

                                                  ;; if we are interrupted call on-end
                                                  (on-end))
                                                )]
                       (collect-next-batch 0)))]
    {:interrupt-task interrupt-task
     :start-task     start-task}))

(def collect-batch-size 10000)

(defn submit-interruptible-task [{:keys [task-id start-task interrupt-task]}]
  (interrupt-all-tasks)  
  (swap! interruptible-tasks assoc task-id {:start-task (fn []
                                                          #?(:clj (future (start-task))
                                                             :cljs (start-task))
                                                          (println (utils/format "Task %s started" task-id)))
                                            :interrupt-task (fn []                                                              
                                                              (interrupt-task)
                                                              (println (utils/format "Task %s interrupted" task-id)))})
  (rt-events/publish-event! (rt-events/make-task-submitted-event task-id))
  task-id)

(defn submit-batched-collect-interruptible-task

  "Submits a timeline entry collection tasks. Uses the xf transducer to collect entries.
  Doesn't start the task. Returns a task id.
  You can start it by calling `start-task` and interrupt it with `interrupt-task` providing the returned task id.
  Progress, match and end will be reported through the event system via task-progress-event and task-finished-event.
  task-progress-event will be called after each batch and carry {:batch []} which are collected entries for the batch
  just processed. Interruption can only happen between batches. Batch size is `collect-batch-size`."
  
  [timeline xf]
  (let [task-id (str (utils/rnd-uuid))
        task (async-interruptible-batched-collect timeline
                                                  collect-batch-size
                                                  xf
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
  
  [pred config {:keys [result-transform]}]
  (let [task-id (str (utils/rnd-uuid))
        result-transform (or result-transform identity)
        {:keys [start interrupt]} (index-api/timelines-async-interruptible-find-entry
                                   pred
                                   config
                                   {:on-progress (fn [perc]
                                                   (rt-events/publish-event! (rt-events/make-task-progress-event task-id {:progress perc})))
                                    :on-match (fn [match-val]
                                                (rt-events/publish-event! (rt-events/make-task-finished-event task-id (result-transform match-val)))
                                                (swap! interruptible-tasks dissoc task-id))
                                    :on-end (fn []
                                              (rt-events/publish-event! (rt-events/make-task-finished-event task-id))
                                              (swap! interruptible-tasks dissoc task-id))})]
    (submit-interruptible-task {:task-id task-id
                                :start-task start
                                :interrupt-task interrupt})))

(defn runtime-config []
  {:env-kind #?(:clj :clj :cljs :cljs)
   :storm? (utils/storm-env?)
   :recording? (tracer/recording?)
   :total-order-recording? (tracer/total-order-recording?)
   :breakpoints (tracer/all-breakpoints)})

(defn val-pprint [vref opts]
  (rt-values/val-pprint-ref vref opts))

(defn shallow-val [vref]
  (rt-values/shallow-val vref))

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
  (let [stats (index-api/fn-call-stats flow-id thread-id)]
    (->> stats
         (mapv (fn [fstats]
                 (update fstats :dispatch-val reference-value!))))))

(defn all-fn-call-stats []
  (reduce (fn [r [flow-id thread-id]]
            (let [thread-stats (index-api/fn-call-stats flow-id thread-id)]
              (reduce (fn [rr {:keys [fn-ns fn-name cnt]}]
                        (update rr (str (symbol fn-ns fn-name)) #(+ (or % 0) cnt)))
                      r
                      thread-stats)))
          {}
          (index-api/all-threads)))

(defn render-fn-frames-transd

  "Given render-args a vector of args indexes to render returns a transducer
  that will render fn-frames."
  
  [render-args]
  (let [print-opts {:print-length 3
                    :print-level  3
                    :print-meta?  false
                    :pprint?      false}]
    (map (fn [{:keys [args-vec ret throwable] :as fn-frame}]
           (let [fr (-> fn-frame
                        reference-frame-data!
                        (assoc :args-vec-str  (:val-str (rt-values/val-pprint args-vec (assoc print-opts :nth-elems render-args)))))]
             (if throwable
               (assoc fr :throwable-str (ex-message throwable))
               (assoc fr :ret-str       (:val-str (rt-values/val-pprint ret print-opts)))))))))

(defn collect-fn-frames-task [flow-id thread-id fn-ns fn-name form-id render-args]  
  (let [timeline (index-api/get-timeline flow-id thread-id)
        xf (comp (index-api/fn-calls-transd fn-ns fn-name form-id)
                 (index-api/frame-data-transd timeline)
                 (render-fn-frames-transd render-args))]    
    (submit-batched-collect-interruptible-task timeline xf)))

(defn find-fn-call-task [fq-fn-call-symb from-idx {:keys [backward?]}]
  (let [criteria {:fn-ns (namespace fq-fn-call-symb)
                  :fn-name (name fq-fn-call-symb)
                  :from-idx from-idx
                  :backward? backward?}]
    (submit-find-interruptible-task (index-api/build-find-fn-call-entry-predicate criteria)
                                    criteria                                    
                                    {:result-transform reference-timeline-entry!})))

(defn find-flow-fn-call [flow-id]
  (some-> (index-api/find-flow-fn-call flow-id)
          reference-timeline-entry!))

(defn find-expr-entry-task [{:keys [identity-val equality-val] :as criteria}]
  (let [criteria (cond-> criteria
                   identity-val (update :identity-val deref-value)
                   equality-val (update :equality-val deref-value)
                   true         (assoc  :needs-form-id? true))]
    
    (submit-find-interruptible-task (index-api/build-find-expr-entry-predicate criteria)
                                    criteria                                    
                                    {:result-transform reference-timeline-entry!})))

(defn total-order-timeline-task []
  (let [timeline (index-api/total-order-timeline)
        xf (total-order-timeline/detailed-timeline-transd index-api/forms-registry)]    
    (submit-batched-collect-interruptible-task timeline xf)))

(defn thread-prints-task [{:keys [flow-id thread-id printers]}]
  (let [timeline (index-api/get-timeline flow-id thread-id)
        xf (index-api/thread-prints-transd printers)]    
    (submit-batched-collect-interruptible-task timeline xf)))

(defn search-next-timeline-entry-task [flow-id thread-id query-str from-idx {:keys [print-length print-level] :or {print-level 2 print-length 10}}]
  (submit-find-interruptible-task (fn search-next-timeline-entry-pred [_ tl-entry]
                                    (when (or (expr-trace/expr-trace? tl-entry)
                                              (fn-return-trace/fn-return-trace? tl-entry))

                                      (let [result (index-protos/get-expr-val tl-entry)]
                                        (when (str/includes? (:val-str (rt-values/val-pprint-ref result {:print-length print-length
                                                                                                         :print-level print-level
                                                                                                         :pprint? false}))
                                                             query-str)
                                          tl-entry))))                                  
                                  {:from-idx from-idx
                                   :flow-id flow-id
                                   :thread-id thread-id}
                                  {:result-transform reference-timeline-entry!}))

(def discard-flow index-api/discard-flow)

(def all-flows-threads index-api/all-threads)

(defn clear-recordings []
  (let [flows-ids (->> (all-flows-threads)
                       (map first)
                       (into #{}))]
    (doseq [fid flows-ids]
      (discard-flow fid))
    
    (rt-values/clear-vals-ref-registry)))

(def flow-threads-info index-api/flow-threads-info)

(defn goto-location [flow-id {:keys [thread/id thread/idx]}]
  (rt-events/publish-event! (rt-events/make-goto-location-event flow-id id idx)))

(defn stack-for-frame [flow-id thread-id fn-call-idx]
  (index-api/stack-for-frame flow-id thread-id fn-call-idx))

(defn set-recording [enable?]
  (tracer/set-recording enable?)
  (rt-events/publish-event! (rt-events/make-recording-updated-event enable?)))

(def set-thread-trace-limit tracer/set-thread-trace-limit)

(defn toggle-recording []
  (if (tracer/recording?)
    (set-recording false)
    (set-recording true)))

(defn set-total-order-recording [x]
  (tracer/set-total-order-recording x))

(defn jump-to-last-expression-in-this-thread
  ([] (jump-to-last-expression-in-this-thread nil))
  ([flow-id]
   (let [thread-id (utils/get-current-thread-id)
         last-ex-loc (when-let [cnt (count (index-api/get-timeline flow-id thread-id))]
                       {:thread/id thread-id
                        :thread/name (utils/get-current-thread-name)
                        :thread/idx (dec cnt)})]
     (if last-ex-loc
       (goto-location flow-id last-ex-loc)
       (log "No recordings for this thread yet")))))

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
   (defn instrument-var [kind var-symb {:keys [disable-events?] :as config}]
     (let [inst-fn (case kind
                     :clj  hansel/instrument-var-clj
                     :cljs hansel/instrument-var-shadow-cljs)]
       
       (log (format "Instrumenting var %s %s" var-symb config))

       (inst-fn var-symb (merge (tracer/hansel-config config)
                                config))
       
       (when-not disable-events?
         (publish-event! kind (rt-events/make-var-instrumented-event (name var-symb) (namespace var-symb)) config)))))

#?(:clj
   (defn uninstrument-var [kind var-symb {:keys [disable-events?] :as config}]
     (let [inst-fn (case kind
                     :clj  hansel/uninstrument-var-clj
                     :cljs hansel/uninstrument-var-shadow-cljs)]

       (log (format "Uninstrumenting var %s %s" var-symb config))
       (inst-fn var-symb (merge (tracer/hansel-config config)
                                config))
       (when-not disable-events?
         (publish-event! kind (rt-events/make-var-uninstrumented-event (name var-symb) (namespace var-symb)) config)))))

#?(:clj
   (defn instrument-namespaces [kind ns-prefixes {:keys [disable-events?] :as config}]
     (log (format "Instrumenting namespaces %s" (pr-str ns-prefixes)))
     (let [inst-fn (case kind
                     :clj  hansel/instrument-namespaces-clj
                     :cljs hansel/instrument-namespaces-shadow-cljs)
           {:keys [affected-namespaces]} (inst-fn ns-prefixes (merge (tracer/hansel-config config)
                                                                     config))]
       
       (when-not disable-events?
         (doseq [ns-symb affected-namespaces]
           (publish-event! kind (rt-events/make-ns-instrumented-event (name ns-symb)) config))))))

#?(:clj
   (defn uninstrument-namespaces [kind ns-prefixes {:keys [disable-events?] :as config}]
     (log (format "Uninstrumenting namespaces %s" (pr-str ns-prefixes)))
     (let [inst-fn (case kind
                     :clj  hansel/uninstrument-namespaces-clj
                     :cljs hansel/uninstrument-namespaces-shadow-cljs)
           {:keys [affected-namespaces]} (inst-fn ns-prefixes (merge (tracer/hansel-config config)
                                                                     config))]
       
       (when-not disable-events?
         (doseq [ns-symb affected-namespaces]
           (publish-event! kind (rt-events/make-ns-uninstrumented-event (name ns-symb)) config))))))

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
             :shallow-val shallow-val
             :get-form get-form
             :timeline-count timeline-count
             :timeline-entry timeline-entry
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
             :search-next-timeline-entry-task search-next-timeline-entry-task
             :find-expr-entry-task find-expr-entry-task
             :find-fn-call-task find-fn-call-task

             :discard-flow discard-flow             
             :tap-value tap-value
             :interrupt-all-tasks interrupt-all-tasks
             :start-task start-task
             :clear-recordings clear-recordings
             :flow-threads-info flow-threads-info
             :all-flows-threads all-flows-threads
             :stack-for-frame stack-for-frame
             :toggle-recording toggle-recording
             :set-total-order-recording set-total-order-recording
             :all-fn-call-stats all-fn-call-stats
             :ping ping
             #?@(:clj
                 [:def-value def-value
                  :all-namespaces all-namespaces
                  :all-vars-for-namespace all-vars-for-namespace
                  :get-var-meta get-var-meta
                  :instrument-var instrument-var
                  :uninstrument-var uninstrument-var
                  :instrument-namespaces instrument-namespaces
                  :uninstrument-namespaces uninstrument-namespaces
                  :unblock-thread unblock-thread
                  :unblock-all-threads unblock-all-threads
                  :add-breakpoint! add-breakpoint!
                  :remove-breakpoint! remove-breakpoint!])})

(defn call-by-name [fun-key args]  
  (let [f (get api-fn fun-key)]
    (apply f args)))

#?(:clj
   (defn setup-runtime

     "Setup runtime based on jvm properties. Returns a config map."

     []
     (let [theme-prop (System/getProperty "flowstorm.theme")
           title-prop (System/getProperty "flowstorm.title")
           styles-prop (System/getProperty "flowstorm.styles")
           fn-call-limits (utils/parse-thread-fn-call-limits (System/getProperty "flowstorm.threadFnCallLimits"))
           config (cond-> {}
                    theme-prop            (assoc :theme (keyword theme-prop))
                    styles-prop           (assoc :styles styles-prop)
                    title-prop            (assoc :title  title-prop))]

       (tracer/set-recording (if (= (System/getProperty "flowstorm.startRecording") "false") false true))

       (doseq [[fn-ns fn-name l] fn-call-limits]
         (index-api/add-fn-call-limit fn-ns fn-name l))

       config))

   :cljs ;; ------------------------------------------------------------------------------------------------------------
   (defn setup-runtime

     "This is meant to be called by preloads to initialize the runtime side of things"

     []
     (println "Setting up runtime")

     (index-api/start)

     (println "Index started")

     (let [recording? (if (= (env-prop "flowstorm.startRecording") "false") false true)]
       (tracer/set-recording recording?)
       (println "Recording set to " recording?))

     (let [fn-call-limits (utils/parse-thread-fn-call-limits (env-prop "flowstorm.threadFnCallLimits"))]
       (doseq [[fn-ns fn-name l] fn-call-limits]
         (index-api/add-fn-call-limit fn-ns fn-name l)
         (println "Added function limit " fn-ns fn-name l)))

     (rt-values/clear-vals-ref-registry)
     (println "Value references cleared")

     (rt-taps/setup-tap!)
     (println "Runtime setup ready")))

#?(:clj
   (defn start-runtime [events-dispatch-fn skip-debugger-start? {:keys [skip-index-start?] :as config}]
     (let [config (merge config (setup-runtime))]

       ;; NOTE: The order here is important until we replace this code with
       ;; better component state management

       (when-not skip-index-start?
         (index-api/start))

       ;; start the debugger UI
       (when-not skip-debugger-start?
         (let [start-debugger (requiring-resolve (symbol (name debugger-main-ns) "start-debugger"))]
           (start-debugger config)))

       (rt-events/set-dispatch-fn events-dispatch-fn)

       (rt-values/clear-vals-ref-registry)

       (rt-taps/setup-tap!)

       (mem-reporter/run-mem-reporter))))

#?(:clj
   (defn remote-connect [config]

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

                               (start-runtime enqueue-event! true config)

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
