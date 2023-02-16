(ns flow-storm.runtime.indexes.storm-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos])
  (:import [clojure.storm TraceIndex CallStackFrame CallTreeNode FrameIndex
            FnCallTrace ExprTrace FnReturnTrace]))

(defn expr-exec-map [^ExprTrace et]
  {:trace/type :expr-exec
   :form-id (.getFormId et)
   :coor (.getCoord et)
   :timestamp 0 ;; TODO: add timestamp
   :result (.getExprVal et) #_(snapshot-reference (.getExprVal et)) ;; TODO: add snapshot
   })

(deftype WrappedCallStackFrame [^CallStackFrame wrapped-frame]

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
               :frame-idx (.getTimelineIndex wrapped-frame)}
        fn-return-trace (assoc :ret (.getRetVal fn-return-trace)))))

  (get-expr-exec [_ idx]
    (expr-exec-map (.getExprExecution idx))))

(defn make-storm-wrapped-call-stack-frame [frame]
  (->WrappedCallStackFrame frame))

(deftype WrappedTreeNode [^CallTreeNode wrapped-node]

  index-protos/TreeNodeP

  (get-frame [_]
    (make-storm-wrapped-call-stack-frame (.getFrame wrapped-node)))

  (get-node-immutable-frame [this]
    (index-protos/get-immutable-frame (index-protos/get-frame this)))

  (has-childs? [_]
    (pos? (.size (.getChilds wrapped-node))))

  (get-childs [_]
    (.getChilds wrapped-node)))

(defn make-storm-wrapped-tree-node [node]
  (->WrappedTreeNode node))

(deftype StormFrameIndex [^FrameIndex wrapped-index]

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

(deftype StormFnCallStatsIndex [wrapped-index]

  index-protos/FnCallStatsP
  (all-stats [_] {}))

(defn make-storm-fn-call-stats-index [index]
  (->StormFnCallStatsIndex index))

(deftype StormFormRegistry []
  index-protos/FormRegistryP

  (all-forms [_]
    (TraceIndex/getAllForms))

  (get-form [_ form-id]
    (TraceIndex/getForm form-id)))

(defn make-storm-form-registry []
  (->StormFormRegistry))

(deftype StormThreadRegistry []

  index-protos/ThreadRegistryP

  (all-threads [_]
    (->> (TraceIndex/getThreadIds)
         (map (fn [tid] [nil tid]))))

  (get-thread-indexes [_ _ thread-id]
    (-> (TraceIndex/getThreadIndexes thread-id)
        (update :frame-index make-storm-frame-index)
        (update :fn-call-stats-index make-storm-fn-call-stats-index))))

(defn make-storm-thread-registry []
  (->StormThreadRegistry))
