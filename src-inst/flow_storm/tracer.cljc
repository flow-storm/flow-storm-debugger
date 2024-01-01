(ns flow-storm.tracer
  (:require [flow-storm.utils :as utils :refer [stringify-coord]]
            [hansel.instrument.runtime :refer [*runtime-ctx*]]
            [flow-storm.runtime.values :refer [snapshot-reference]]
            [flow-storm.runtime.indexes.api :as indexes-api]))

(declare start-tracer)
(declare stop-tracer)

(def total-order-recording (atom false))
(def recording (atom true))
(def breakpoints (atom #{}))
(def blocked-threads (atom #{}))

(defn set-recording [x]
  (if x
    (do
      (reset! recording true)
      (indexes-api/reset-all-threads-trees-build-stack nil))

    (reset! recording false)))

(defn recording? [] @recording)

(defn set-total-order-recording [x]
  (reset! total-order-recording (boolean x)))

(defn total-order-recording? []
  @total-order-recording)

#?(:clj
   (defn- block-this-thread [flow-id breakpoint]
     (let [thread-obj (Thread/currentThread)
           tname (.getName thread-obj)
           tid (.getId thread-obj)]
       (if (= tname"JavaFX Application Thread")

         (utils/log "WARNING, skipping thread block, trace is being executed by the UI thread and doing so will freeze the UI.")

         (do
           (utils/log (format "Blocking thread %d %s" tid tname))
           (indexes-api/mark-thread-blocked flow-id tid breakpoint)
           (swap! blocked-threads conj thread-obj)
           (locking thread-obj
             (.wait thread-obj))
           (indexes-api/mark-thread-unblocked flow-id tid)
           (utils/log (format "Thread %d %s unlocked, continuing ..." tid tname)))))))

#?(:clj
   (defn unblock-thread [thread-id]
     (let [thread-obj (utils/get-thread-object-by-id thread-id)]
       (swap! blocked-threads disj thread-obj)
       (locking thread-obj
         (.notifyAll thread-obj)))))

#?(:clj
   (defn unblock-all-threads []
     (doseq [thread-obj @blocked-threads]
       (locking thread-obj
         (.notifyAll thread-obj)))))

(defn add-breakpoint! [fn-ns fn-name args-pred]
  (swap! breakpoints conj (with-meta [fn-ns fn-name] {:predicate args-pred})))

(defn remove-breakpoint! [fn-ns fn-name]
  (swap! breakpoints disj [fn-ns fn-name]))

(defn clear-breakpoints! []
  (reset! breakpoints #{}))

(defn all-breakpoints []
  @breakpoints)

(defn trace-flow-init-trace

  "Send flow initialization trace"
  
  [{:keys [flow-id form-ns form]}]
  (when @recording
    (let [trace {:trace/type :flow-init
                 :flow-id flow-id
                 :ns form-ns
                 :form form
                 :timestamp (utils/get-monotonic-timestamp)}]
      (indexes-api/add-flow-init-trace trace))))

(defn trace-form-init

  "Send form initialization trace only once for each thread."
  
  [{:keys [form-id ns def-kind dispatch-val form file line]}]

  (when-not (indexes-api/get-form form-id)
    (let [trace (cond-> {:trace/type :form-init
                         :form/id form-id
                         :form/form form
                         :form/ns ns
                         :form/def-kind def-kind
                         :form/file file
                         :form/line line}
                  (= def-kind :defmethod) (assoc :multimethod/dispatch-val dispatch-val))]

      (indexes-api/add-form-init-trace trace))))

(defn trace-fn-call

  "Send function call traces"
  
  ([{:keys [form-id ns fn-name fn-args]}] ;; for using with hansel
   
   (let [{:keys [flow-id]} *runtime-ctx*]
     (trace-fn-call flow-id ns fn-name fn-args form-id)))
  
  ([flow-id fn-ns fn-name fn-args form-id]  ;; for using with storm
   (let [thread-id (utils/get-current-thread-id)
         thread-name (utils/get-current-thread-name)]
     
     #?(:clj
        (let [brks @breakpoints]
          (when (and (pos? (count brks))
                     (contains? brks [fn-ns fn-name])
                     (apply (-> (get brks [fn-ns fn-name]) meta :predicate) fn-args))
            ;; before blocking, let's make sure the thread exists
            (indexes-api/get-or-create-thread-indexes flow-id thread-id thread-name form-id)
            (block-this-thread flow-id [fn-ns fn-name]))))
     
     (when @recording            
       (let [args (mapv snapshot-reference fn-args)]
         (indexes-api/add-fn-call-trace
          flow-id
          thread-id
          thread-name
          fn-ns
          fn-name
          form-id
          args
          @total-order-recording))))))

(defn trace-fn-return

  "Send function return traces"
  
  ([{:keys [return coor form-id]}]  ;; for using with hansel
   
   (let [{:keys [flow-id]} *runtime-ctx*]
     (trace-fn-return flow-id return (stringify-coord coor) form-id)
     return))
  
  ([flow-id return coord _] ;; for using with storm
   (when @recording
     (let [thread-id (utils/get-current-thread-id)]
       (indexes-api/add-fn-return-trace
        flow-id
        thread-id
        coord
        (snapshot-reference return)
        @total-order-recording)))))

(defn trace-fn-unwind
  ([{:keys [throwable coor form-id]}]  ;; for using with hansel   
   (let [{:keys [flow-id thread-trace-limit]} *runtime-ctx*
         thread-id (utils/get-current-thread-id)]

     (when thread-trace-limit
       (when (> (count (indexes-api/get-timeline flow-id thread-id)) thread-trace-limit)
         (throw (ex-info "thread-trace-limit exceeded" {}))))

     (trace-fn-unwind flow-id throwable (stringify-coord coor) form-id)))
  
  ([flow-id throwable coord _] ;; for using with storm  
   (let [thread-id (utils/get-current-thread-id)]
       (indexes-api/add-fn-unwind-trace
        flow-id
        thread-id      
        coord
        (snapshot-reference throwable)
        @total-order-recording))))

(defn trace-expr-exec
  
  "Send expression execution trace."
  
  ([{:keys [result coor form-id]}]  ;; for using with hansel   
   (let [{:keys [flow-id thread-trace-limit]} *runtime-ctx*
         thread-id (utils/get-current-thread-id)]

     (when thread-trace-limit
       (when (> (count (indexes-api/get-timeline flow-id thread-id)) thread-trace-limit)
         (throw (ex-info "thread-trace-limit exceeded" {}))))

     (trace-expr-exec flow-id result (stringify-coord coor) form-id))
   
   result)

  ([flow-id result coord _]  ;; for using with storm
   (when @recording
     (let [thread-id (utils/get-current-thread-id)]
       (indexes-api/add-expr-exec-trace      
        flow-id
        thread-id      
        coord
        (snapshot-reference result)
        @total-order-recording)))))

(defn trace-bind
  
  "Send bind trace."
  
  ([{:keys [symb val coor]}] ;; for using with hansel

   (let [{:keys [flow-id]} *runtime-ctx*]
     (trace-bind flow-id (stringify-coord coor) (name symb) val)))

  ([flow-id coord sym-name val]  ;; for using with storm
   (when @recording
     (let [thread-id (utils/get-current-thread-id)]
       (indexes-api/add-bind-trace      
        flow-id
        thread-id
        coord
        sym-name
        (snapshot-reference val))))))

(defn hansel-config

  "Builds a hansel config from inst-opts"
  
  [{:keys [disable] :or {disable #{}}}]
  (cond-> `{:trace-form-init trace-form-init
            :trace-fn-call trace-fn-call
            :trace-fn-return trace-fn-return
            :trace-fn-unwind trace-fn-unwind
            :trace-expr-exec trace-expr-exec
            :trace-bind trace-bind}
    
    (disable :expr-exec)    (dissoc :trace-expr-exec)
    (disable :bind)         (dissoc :trace-expr-exec)
    (disable :anonymous-fn) (assoc :disable #{:anonymous-fn})))

#?(:clj
   (defn- set-clojure-storm [callbacks]
     ;; Set ClojureStorm callbacks by reflection so FlowStorm can be used
     ;; without ClojureStorm on the classpath.
     (let [tracer-class (Class/forName "clojure.storm.Tracer")
           setTraceFnsCallbacks (.getMethod tracer-class "setTraceFnsCallbacks" (into-array java.lang.Class [clojure.lang.IPersistentMap]))]       
       (.invoke setTraceFnsCallbacks nil (into-array [callbacks])))))

#?(:clj
   (defn hook-clojure-storm []  
     (set-clojure-storm
      {:trace-fn-call-fn    trace-fn-call
	   :trace-fn-return-fn  trace-fn-return
	   :trace-fn-unwind-fn  trace-fn-unwind
	   :trace-expr-fn       trace-expr-exec
	   :trace-bind-fn       trace-bind
       
       ;; this are just for backward compatibility
       ;; TODO: remove this when it feels safe
       :trace-fn-call-fn-key    trace-fn-call
	   :trace-fn-return-fn-key  trace-fn-return
	   :trace-fn-unwind-fn-key  trace-fn-unwind
	   :trace-expr-fn-key       trace-expr-exec
	   :trace-bind-fn-key       trace-bind
       })))

#?(:clj
   (defn unhook-clojure-storm []  
     (set-clojure-storm
      {:trace-fn-call-fn    nil
	   :trace-fn-return-fn  nil
	   :trace-fn-unwind-fn  nil
	   :trace-expr-fn       nil
	   :trace-bind-fn       nil
       
       ;; this are just for backward compatibility
       ;; TODO: remove this when it feels safe
       :trace-fn-call-fn-key    nil
	   :trace-fn-return-fn-key  nil
	   :trace-fn-unwind-fn-key  nil
	   :trace-expr-fn-key       nil
	   :trace-bind-fn-key       nil
       
	   })))

#?(:cljs        
   (defn hook-clojurescript-storm []
     ;; We do it like this so FlowStorm can be used without ClojureScript storm,
     ;; which we can't if we require cljs.storm at the top.
     (js* "try {
         cljs.storm.tracer.trace_expr_fn=flow_storm.tracer.trace_expr_exec;
         cljs.storm.tracer.trace_fn_call_fn=flow_storm.tracer.trace_fn_call;
         cljs.storm.tracer.trace_fn_return_fn=flow_storm.tracer.trace_fn_return;
         cljs.storm.tracer.trace_fn_unwind_fn=flow_storm.tracer.trace_fn_unwind;
         cljs.storm.tracer.trace_bind_fn=flow_storm.tracer.trace_bind;
         cljs.storm.tracer.trace_form_init_fn=flow_storm.tracer.trace_form_init;
         console.log(\"ClojureScriptStorm functions plugged in.\");
       } catch (error) {console.log(\"ClojureScriptStorm not detected.\")}")
     ;; we need to return nil here, the js* can't be the last statement or it will
     ;; generate "return try {...}" which isn't valid JS 
     nil))
