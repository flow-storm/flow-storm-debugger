(ns flow-storm.debugger.trace-indexer.mutable.impl
  (:require [flow-storm.debugger.trace-indexer.protos :refer [TraceIndex]]
            [flow-storm.debugger.trace-indexer.mutable.callstack-tree :as callstack-tree]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.utils :as utils :refer [log]]
            [flow-storm.trace-types :as trace-types]
            [flow-storm.debugger.trace-types :refer [deref-ser]]
            [clojure.string :as str])
  (:import [java.util ArrayList HashMap]))

(deftype MutableTraceIndexer [^ArrayList traces
                               callstack-tree
                              ^HashMap forms
                              ^HashMap forms-hot-traces]

  TraceIndex

  (thread-exec-count [this]
    (locking this
      (.size traces)))

  (add-form [this form-id form-ns def-kind mm-dispatch-val form]
    (locking this
      (.put forms form-id (cond-> {:form/id form-id
                                  :form/ns form-ns
                                  :form/form form
                                  :form/def-kind def-kind}
                           (= def-kind :defmethod)
                           (assoc :multimethod/dispatch-val mm-dispatch-val)))))

  (get-form [this form-id]
    (locking this
      (.get forms form-id)))

  (add-fn-call-trace [this trace]
    (locking this
      (let [next-idx (.size traces)]

        (callstack-tree/process-fn-call-trace callstack-tree next-idx)

        (.add traces trace))))

  (add-exec-trace [this {:keys [form-id]:as trace}]
    (locking this
      (let [next-idx (.size traces)
            trace (with-meta trace {:trace-idx next-idx})]

        (callstack-tree/process-exec-trace callstack-tree next-idx trace)
        (.add traces trace)

        (when-not (.get forms-hot-traces form-id)
          (.put forms-hot-traces form-id (ArrayList.)))

        (.add ^ArrayList (.get forms-hot-traces form-id)
              trace))))

  (add-bind-trace [this trace]
    (locking this
      (callstack-tree/process-bind-trace callstack-tree trace)))

  (get-trace [this idx]
    (locking this
      (.get traces idx)))

  (bindings-for-trace [this trace-idx]
    (locking this
      (let [coor-in-scope? (fn [scope-coor current-coor]
                             (if (empty? scope-coor)
                               true
                               (and (every? true? (map = scope-coor current-coor))
                                    (> (count current-coor) (count scope-coor)))))
            curr-trace (.get traces trace-idx)
            bind-traces (callstack-tree/bind-traces-for-trace callstack-tree trace-idx)
            applicable-binds (keep (fn [bt]
                                     (when (and (coor-in-scope? (:coor bt) (:coor curr-trace))
                                                (<= (:timestamp bt) (:timestamp curr-trace)))
                                       [(:symbol bt) (:value bt)]))
                                   bind-traces)]
       (into {} applicable-binds))))

  (interesting-expr-traces [this form-id trace-idx]
    (locking this
      (let [[min-trace-idx max-trace-idx] (callstack-tree/frame-min-max-traces callstack-tree trace-idx)
           hot-traces (.get forms-hot-traces form-id)]
       (->> hot-traces
            (filter (fn [t]
                      (<= min-trace-idx (:trace-idx (meta t)) max-trace-idx)))
            doall))))

  (callstack-tree-root [this]
    (locking this
      (callstack-tree/get-tree-root callstack-tree)))

  (callstack-node-frame [this node]
    (locking this
      (let [{:keys [call-trace-idx ret]} (callstack-tree/get-node-info node)]
        (when call-trace-idx
          (let [{:keys [form-id fn-name fn-ns args-vec timestamp]} (.get traces call-trace-idx)]
            {:fn-name fn-name
             :fn-ns fn-ns
             :call-trace-idx call-trace-idx
             :args args-vec
             :timestamp timestamp
             :form-id form-id
             :ret ret})))))

  (callstack-tree-childs [this node]
    (locking this
      (callstack-tree/tree-node-childs node)))

  (callstack-frame-call-trace-idx [this trace-idx]
    (locking this
      (callstack-tree/frame-call-trace-index callstack-tree trace-idx)))

  (find-fn-calls [this fn-ns fn-name form-id]
    (locking this
      (->> traces
          (keep-indexed (fn [idx trace]
                          (when (and (= fn-ns (:fn-ns trace))
                                     (= fn-name (:fn-name trace))
                                     (= form-id (:form-id trace)))
                            (with-meta trace {:trace-idx idx}))))
          doall)))

  (search-next-fn-call-trace [this search-str from-idx print-level on-result-cb on-progress]
    (locking this
      (let [search-thread (Thread.
                           (fn []
                             (let [total-traces (count traces)
                                   match-stack (loop [i 0
                                                      stack ()]
                                                 (when (and (< i total-traces)
                                                            (not (.isInterrupted (Thread/currentThread))))


                                                   (when (zero? (mod i 10000))
                                                     (on-progress (* 100 (/ i total-traces))))

                                                   (let [{:keys [fn-name args-vec] :as t} (.get traces i)]
                                                     (if (trace-types/fn-call-trace? t)


                                                       (if (and (> i from-idx)
                                                                (or (str/includes? fn-name search-str)
                                                                    (str/includes? (deref-ser args-vec {:print-length 10 :print-level print-level :pprint? false}) search-str)))

                                                         ;; if matches
                                                         (conj stack i)

                                                         ;; else
                                                         (recur (inc i) (conj stack i)))
                                                       ;; it is a exec-trace, check if it is returning
                                                       (if (:outer-form? t)
                                                         (recur (inc i) (pop stack))
                                                         (recur (inc i) stack))))))]
                               (when (.isInterrupted (Thread/currentThread))
                                 (log "Search stopped"))
                               (on-result-cb match-stack))))]
        (reset! ui-vars/long-running-task-thread search-thread)
        (.start search-thread)))))

(defn make-indexer []
  (->MutableTraceIndexer (ArrayList.)
                         (callstack-tree/make-callstack-tree)
                         (HashMap.)
                         (HashMap.)))
