(ns flow-storm.runtime.indexes.storm-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos])
  (:import [clojure.storm TraceIndex CallStackFrame CallTreeNode FrameIndex
            FnCallTrace ExprTrace FnReturnTrace]))

(declare make-storm-wrapped-tree-node)

(defn expr-exec-map [^ExprTrace et]
  {:trace/type :expr-exec
   :idx (.getTimelineIdx et)
   :form-id (.getFormId et)
   :coor (.getCoord et)
   :timestamp (.getTimestamp et)
   :result (.getExprVal et)})

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
        (let [frame-node (.getFrameNode entry)]
          (when-let [frame (.getFrame frame-node)]
            (merge {:timeline/type :frame}
                   (-> (make-storm-wrapped-call-stack-frame frame)
                       index-protos/get-immutable-frame))))

        (instance? ExprTrace entry)
        {:timeline/type :expr
         :idx (.getTimelineIdx entry)
         :form-id (.getFormId entry)
         :coor (.getCoord entry)
         :result (.getExprVal entry)
         :timestamp (.getTimestamp entry)
         :outer-form? false}

        (instance? FnReturnTrace entry)
        {:timeline/type :expr
         :idx (.getTimelineIdx entry)
         :form-id (.getFormId entry)
         :result (.getRetVal entry)
         :outer-form? true
         :coor []
         :timestamp (.getTimestamp entry)})))

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
    (let [entry (.getTimelineEntry wrapped-index idx)
          frame-node (.getFrameNode entry)
          frame (make-storm-wrapped-call-stack-frame (.getFrame frame-node))]
      (index-protos/get-immutable-frame frame)))

  (callstack-tree-root-node [_]
    (make-storm-wrapped-tree-node (.getRootNode wrapped-index))))

(defn make-storm-frame-index [index]
  (->StormFrameIndex index))

(defrecord StormFnCallStatsIndex [wrapped-index]

  index-protos/FnCallStatsP
  (all-stats [_]
    (.getStats wrapped-index)))

(defn make-storm-fn-call-stats-index [index]
  (->StormFnCallStatsIndex index))

(defrecord StormFormRegistry []
  index-protos/FormRegistryP

  (all-forms [_]
    (TraceIndex/getAllForms))

  (get-form [_ form-id]
    (if form-id
      (TraceIndex/getForm form-id)
      (println "ERROR : can't get form for id null"))))

(defn make-storm-form-registry []
  (->StormFormRegistry))

(defrecord StormThreadRegistry []

  index-protos/ThreadRegistryP

  (all-threads [_]
    (->> (TraceIndex/getThreadIds)
         (map (fn [tid] [nil tid]))))

  (flow-threads-info [_ _]
    (->> (TraceIndex/getThreadIds)
         (map (fn [tid]
                {:thread/id tid
                 :thread/name (TraceIndex/getThreadName tid)}))))

  (get-thread-indexes [_ _ thread-id]
    (-> (TraceIndex/getThreadIndexes thread-id)
        (update :frame-index make-storm-frame-index)
        (update :fn-call-stats-index make-storm-fn-call-stats-index)))

  (discard-threads [_ flow-threads-ids]
    flow-threads-ids
    ;; TODO: do something about this
    ))

(defn make-storm-thread-registry []
  (->StormThreadRegistry))
