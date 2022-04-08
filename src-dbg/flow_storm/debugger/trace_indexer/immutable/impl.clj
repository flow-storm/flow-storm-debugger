(ns flow-storm.debugger.trace-indexer.immutable.impl
  (:require [flow-storm.debugger.trace-indexer.protos :refer [TraceIndex]]
            [flow-storm.debugger.trace-indexer.immutable.callstack-tree :as callstack-tree]))


(defrecord ImmutableTraceIndexer [*state]

  TraceIndex

  (thread-exec-count [_] (count (:traces @*state)))

  (add-form [_ form-id form-ns def-kind mm-dispatch-val form]
    (swap! *state assoc-in [:forms form-id] (cond-> {:form/id form-id
                                                     :form/ns form-ns
                                                     :form/form form
                                                     :form/def-kind def-kind}
                                              (= def-kind :defmethod)
                                              (assoc :multimethod/dispatch-val mm-dispatch-val))))

  (get-form [_ form-id]
    (get (:forms @*state) form-id))

  (add-fn-call-trace [_ trace]
    (let [next-idx (count (:traces @*state))]
      (swap! *state
             (fn [state]
               (-> state
                   (update :callstack-tree (fn [cs-tree]
                                             (if (zero? next-idx)
                                               (callstack-tree/make-call-tree trace next-idx)
                                               (callstack-tree/process-fn-call-trace cs-tree next-idx trace))))
                   (update :traces conj trace))))))

  (add-exec-trace [_ {:keys [form-id]:as trace}]
    (let [next-idx (count (:traces @*state))]
      (swap! *state
             (fn [state]
               (-> state
                   (update :callstack-tree callstack-tree/process-exec-trace next-idx trace)
                   (update :traces conj trace)
                   (update-in [:forms-hot-traces form-id] (fnil conj []) (with-meta trace {:trace-idx next-idx})))))))

  (add-bind-trace [_ trace]
    (swap! *state
           (fn [state]
             (-> state
                 (update :callstack-tree callstack-tree/process-bind-trace trace)))))

  (get-trace [_ idx]
    (get (:traces @*state) idx))

  (bindings-for-trace [_ trace-idx]
    (-> (callstack-tree/find-frame (:callstack-tree @*state) trace-idx)
        :bindings))

  (interesting-expr-traces [_ form-id trace-idx]
    (let [state @*state
          trace-frame (callstack-tree/find-frame (:callstack-tree state) trace-idx)
          {:keys [min-trace-idx max-trace-idx]} @(:frame-mut-data-ref trace-frame)]
      (->> (get (:forms-hot-traces state) form-id)
           (filter (fn [t]
                     (<= min-trace-idx (:trace-idx (meta t)) max-trace-idx))))))

  (callstack-tree-root [_]
    (callstack-tree/callstack-tree-root (:callstack-tree @*state)))

  (callstack-node-frame [_ node]
    (select-keys node [:fn-name :fn-ns :call-trace-idx :args :timestamp :form-id :ret]))

  (callstack-tree-childs [_ node]
    (:calls node))

  (callstack-frame-call-trace-idx [_ trace-idx]
    (let [state @*state
          trace-frame (callstack-tree/find-frame (:callstack-tree state) trace-idx)]
      (:call-trace-idx trace-frame))))

(defn make-indexer []
  (->ImmutableTraceIndexer (atom {:traces []
                                  :callstack-tree nil
                                  :forms {}
                                  :forms-hot-traces {}}) ))
