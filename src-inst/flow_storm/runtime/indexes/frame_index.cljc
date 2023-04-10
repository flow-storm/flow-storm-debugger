(ns flow-storm.runtime.indexes.frame-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils :refer [make-mutable-stack ms-peek ms-push ms-pop ms-count
                                                                      make-mutable-list ml-get ml-add ml-count]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]
            [flow-storm.runtime.types.bind-trace :as bind-trace]))

(def fn-expr-limit
  #?(:cljs 9007199254740992 ;; MAX safe integer     
     :clj 10000))

(defn expr-exec-map [et]
  {:idx (expr-trace/get-timeline-idx et)
   :form-id (expr-trace/get-form-id et)
   :coor (expr-trace/get-coord et)
   :timestamp (expr-trace/get-timestamp et)
   :result (expr-trace/get-expr-val et)
   :outer-form? false})

(defn fn-return-map [rt]
  (when rt
    {:idx (fn-return-trace/get-timeline-idx rt)
     :form-id (fn-return-trace/get-form-id rt)
     :result (fn-return-trace/get-ret-val rt)
     :outer-form? true
     :coor (fn-return-trace/get-coord rt)
     :timestamp (fn-return-trace/get-timestamp rt)}))

(defn binding-map [bt]
  {:coor (bind-trace/get-coord bt)
   :timestamp (bind-trace/get-timestamp bt)
   :symbol (bind-trace/get-sym-name bt)
   :value (bind-trace/get-val bt)})

(deftype CallStackFrame [fn-call-trace
                         ^int timeline-idx                         
                         bindings
                         expr-executions
                         ^:unsynchronized-mutable ret-trace
                         parent-timeline-idx]
  
  index-protos/CallStackFrameP

  (get-immutable-frame [_]

    (if fn-call-trace      
      (let [fn-name (fn-call-trace/get-fn-name fn-call-trace)
            fn-ns (fn-call-trace/get-fn-ns fn-call-trace)
            fn-args (fn-call-trace/get-fn-args fn-call-trace)
            form-id (fn-call-trace/get-form-id fn-call-trace)]
        {:fn-ns fn-ns
         :fn-name fn-name
         :args-vec fn-args
         :bindings (mapv binding-map bindings) 
         :expr-executions (mapv expr-exec-map expr-executions)
         :ret (when ret-trace (fn-return-trace/get-ret-val ret-trace))
         :form-id form-id
         :frame-idx timeline-idx
         :parent-frame-idx parent-timeline-idx})
      {:root? true}))

  (add-binding-to-frame [_ b]
    (ml-add bindings b))

  (add-expr-exec-to-frame [_ expr]
    (ml-add expr-executions expr))

  (set-return [_ trace]
    (set! ret-trace trace))

  (get-parent-timeline-idx [_]
    (or parent-timeline-idx 0))

  (get-timeline-idx [_]
    timeline-idx)
  
  #?@(:clj
      [java.lang.Object
       (toString [_]                 
                 (format "%s idx: %d, bindings-cnt: %d, expr-executions-cnt:%d"
                         fn-call-trace timeline-idx (ml-count bindings) (ml-count expr-executions)))]))

(defn make-call-stack-frame [fn-call-trace timeline-idx parent-idx]
  (->CallStackFrame fn-call-trace
                    timeline-idx
                    (make-mutable-list)
                    (make-mutable-list)
                    nil
                    parent-idx))

(deftype TreeNode [^CallStackFrame frame
                   childs]
  
  index-protos/TreeNodeP

  (get-frame [_] frame)
  (get-node-immutable-frame [_] (index-protos/get-immutable-frame frame))
  (has-childs? [_] (zero? (ml-count childs)))
  (add-child [_ node]
    (locking childs
      (ml-add childs node)))
  (get-childs [_]
    (locking childs
      (doall (seq childs)))))

(defn make-tree-node [frame]
  (->TreeNode frame (make-mutable-list)))

(deftype MutableFrameIndex [root-node
                            build-stack
                            timeline]

  index-protos/BuildIndexP

  (add-form-init [_ _]) ; don't do anything for form-init 
  
  (add-fn-call [this trace]
    (locking this
      (let [frame-idx (ml-count timeline)
            curr-node (ms-peek build-stack)
            
            parent-frame-idx (-> (index-protos/get-frame curr-node)
                                 index-protos/get-timeline-idx)
            new-frame (make-call-stack-frame trace frame-idx parent-frame-idx)            
            new-node (make-tree-node new-frame)]
        (fn-call-trace/set-frame-node trace new-node)
        (index-protos/add-child curr-node new-node)
        (ml-add timeline trace)
        (ms-push build-stack new-node))))

  (add-fn-return [this trace]
    (locking this
      (when (> (ms-count build-stack) 1)
        (let [curr-node (ms-peek build-stack)
              curr-frame (index-protos/get-frame curr-node)
              curr-idx (ml-count timeline)]
          
          (index-protos/set-return curr-frame trace)
          (fn-return-trace/set-frame-node trace curr-node)
          (fn-return-trace/set-timeline-idx trace curr-idx)
          (ml-add timeline trace)
          (ms-pop build-stack)))))
  
  (add-expr-exec [this trace]
    (locking this
      ;; discard all expressions for the dummy frame
      (when (> (ms-count build-stack) 1)
        (let [curr-node (ms-peek build-stack)
              curr-frame (index-protos/get-frame curr-node)
              ^long exec-cnt (ml-count (.-expr-executions ^CallStackFrame curr-frame))
              ^long bind-cnt (ml-count (.-bindings ^CallStackFrame curr-frame))
              curr-idx (ml-count timeline)]
          (when (<= (+ exec-cnt bind-cnt) fn-expr-limit)
            (expr-trace/set-frame-node trace curr-node)
            (expr-trace/set-timeline-idx trace curr-idx)
            (index-protos/add-expr-exec-to-frame curr-frame trace)
            (ml-add timeline trace))))))
  
  (add-bind [this bind-trace]
    (locking this
      (let [curr-node (ms-peek build-stack)
            curr-frame (index-protos/get-frame curr-node)
            ^long exec-cnt (ml-count (.-expr-executions ^CallStackFrame curr-frame))
            ^long bind-cnt (ml-count (.-bindings ^CallStackFrame curr-frame))]
        (when (<= (+ exec-cnt bind-cnt) fn-expr-limit)
          (index-protos/add-binding-to-frame curr-frame bind-trace)))))

  index-protos/FrameIndexP
  
  (timeline-count [this]
    (locking this
      (ml-count timeline)))
  
  (timeline-entry [this idx]
    (locking this
      (let [tl-entry (ml-get timeline idx)]
        (cond
          (fn-call-trace/fn-call-trace? tl-entry)
          (-> (fn-call-trace/get-frame-node tl-entry)
              index-protos/get-frame
              index-protos/get-immutable-frame
              (assoc :timeline/type :frame))

          (fn-return-trace/fn-return-trace? tl-entry)
          (-> (fn-return-map tl-entry)
              (assoc :timeline/type :expr))
          
          (expr-trace/expr-trace? tl-entry)
          (-> (expr-exec-map tl-entry)
              (assoc :timeline/type :expr))))))

  (timeline-frame-seq [this]
    (locking this
      (->> timeline
           (keep (fn [tl-entry]
                   (when (fn-call-trace/fn-call-trace? tl-entry)
                     (-> (fn-call-trace/get-frame-node tl-entry)
                         index-protos/get-frame 
                         index-protos/get-immutable-frame))))
           doall)))

  (timeline-seq [this]
    (locking this
      (doall (seq timeline))))

  (frame-data [this idx]
    (locking this
      (let [tl-entry (ml-get timeline idx)
            frame-node (cond
                         (fn-call-trace/fn-call-trace? tl-entry) (fn-call-trace/get-frame-node tl-entry)                       
                         (fn-return-trace/fn-return-trace? tl-entry) (fn-return-trace/get-frame-node tl-entry) 
                         (expr-trace/expr-trace? tl-entry) (expr-trace/get-frame-node tl-entry))]
        (-> frame-node
            index-protos/get-frame 
            index-protos/get-immutable-frame))))

  (reset-build-stack [_]
    (loop [stack build-stack]
      (when (> (ms-count stack) 1)
        (ms-pop stack)
        (recur stack))))
  
  (callstack-tree-root-node [this]
    (locking this
      root-node)))

(defn make-index []
  (let [root-frame (make-call-stack-frame nil 0 0)
        root-node (make-tree-node root-frame)
        build-stack (make-mutable-stack)
        timeline (make-mutable-list)]
    (ms-push build-stack root-node)
    (->MutableFrameIndex root-node
                         build-stack
                         timeline)))


