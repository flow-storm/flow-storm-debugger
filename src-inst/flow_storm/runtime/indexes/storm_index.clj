(ns flow-storm.runtime.indexes.storm-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos])
  (:import [clojure.storm TraceIndex CallStackFrame CallTreeNode FrameIndex
            FnCallTrace ExprTrace FnReturnTrace]))

(declare make-storm-wrapped-tree-node)

(defn expr-exec-map [^ExprTrace et]
  {:trace/type :expr-exec
   :form-id (.getFormId et)
   :coor (.getCoord et)
   :timestamp 0 ;; TODO: add timestamp
   :result (.getExprVal et) #_(snapshot-reference (.getExprVal et)) ;; TODO: add snapshot
   })

(defrecord WrappedCallStackFrame [^CallStackFrame wrapped-frame]

  index-protos/CallStackFrameP

  (get-immutable-frame [_]
    (let [fn-call-trace (.getFnCallTrace wrapped-frame)
          fn-return-trace (.getFnReturnTrace wrapped-frame)]
      (cond-> {:fn-ns (.getFnNamespace fn-call-trace)
               :fn-name (.getFnName fn-call-trace)
               :args-vec (.getFnArgs fn-call-trace)
               :bindings [] ;; TODO: ret bindings
               :expr-executions (->> (.getExprExecutions wrapped-frame)
                                     (map expr-exec-map)
                                     (into []))
               :form-id (.getFormId fn-call-trace)
               :frame-idx (.getTimelineIdx wrapped-frame)}
        fn-return-trace (assoc :ret (.getRetVal fn-return-trace)))))

  (get-expr-exec [_ idx]
    (expr-exec-map (.getExprExecution idx))))

(defn make-storm-wrapped-call-stack-frame [frame]
  (->WrappedCallStackFrame frame))

(defrecord WrappedTreeNode [^CallTreeNode wrapped-node]

  index-protos/TreeNodeP

  (get-frame [_]
    (when-let [frame (.getFrame wrapped-node)]
      (make-storm-wrapped-call-stack-frame frame)))

  (get-node-immutable-frame [this]
    (when-let [frame (index-protos/get-frame this)]
      (index-protos/get-immutable-frame frame)))

  (has-childs? [_]
    (pos? (.size (.getChilds wrapped-node))))

  (get-childs [_]
    (map make-storm-wrapped-tree-node (.getChilds wrapped-node))))

(defn make-storm-wrapped-tree-node [node]
  (->WrappedTreeNode node))

(defrecord StormFrameIndex [^FrameIndex wrapped-index]

  index-protos/FrameIndexP

  (timeline-count [_]
    (.getTimelineCount wrapped-index))

  (timeline-entry [_ idx]
    (let [entry (.getTimelineEntry wrapped-index idx)]
      (cond

        (instance? FnCallTrace entry)
        (let [frame-node (.getFrameNode entry)
              frame (make-storm-wrapped-call-stack-frame (.getFrame frame-node))]

          (merge {:timeline/type :frame}
                 (index-protos/get-immutable-frame frame)))

        (instance? ExprTrace entry)
        {:timeline/type :expr
         :idx (.getTimelineIdx entry)
         :form-id (.getFormId entry)
         :coor (.getCoord entry)
         :result (.getExprVal entry)
         :outer-form? false
         :timestamp 0} ;; TODO: fix timestamp

        (instance? FnReturnTrace entry)
        {:timeline/type :expr
         :idx (.getTimelineIdx entry)
         :form-id (.getFormId entry)
         :result (.getRetVal entry)
         :outer-form? true
         :timestamp 0} ;; TODO: fix timestamp
        )))

  (timeline-frame-seq [_]
    (keep (fn [entry]
            (when (instance? FnCallTrace entry)
              (let [frame-node (.getFrameNode entry)
                    frame (make-storm-wrapped-call-stack-frame (.getFrame frame-node))]
                (index-protos/get-immutable-frame frame))))
          (.getTimeline wrapped-index)))

  (timeline-seq [_]
    (seq (.getTimeline wrapped-index)))

  (frame-data [_ idx]
    (let [entry (.getTimelineEntry wrapped-index idx)]
      (let [frame-node (.getFrameNode entry)
            frame (make-storm-wrapped-call-stack-frame (.getFrame frame-node))]
        (index-protos/get-immutable-frame frame))))

  (callstack-tree-root-node [_]
    (make-storm-wrapped-tree-node (.getRootNode wrapped-index))))

(defn make-storm-frame-index [index]
  (->StormFrameIndex index))

(defrecord StormFnCallStatsIndex [wrapped-index]

  index-protos/FnCallStatsP
  (all-stats [_] {}))

(defn make-storm-fn-call-stats-index [index]
  (->StormFnCallStatsIndex index))

(defrecord StormFormRegistry []
  index-protos/FormRegistryP

  (all-forms [_]
    (TraceIndex/getAllForms))

  (get-form [_ form-id]
    "(needs-to-be-implemented)"
    #_(TraceIndex/getForm form-id)))

(defn make-storm-form-registry []
  (->StormFormRegistry))

(defrecord StormThreadRegistry []

  index-protos/ThreadRegistryP

  (all-threads [_]
    (->> (TraceIndex/getThreadIds)
         (map (fn [tid] [nil tid]))))

  (get-thread-indexes [_ _ thread-id]
    (-> (TraceIndex/getThreadIndexes thread-id)
        (update :frame-index make-storm-frame-index)
        (update :fn-call-stats-index make-storm-fn-call-stats-index)))

  (discard-threads [_ flow-threads-ids]
    ;; TODO: do something about this
    ))

(defn make-storm-thread-registry []
  (->StormThreadRegistry))
