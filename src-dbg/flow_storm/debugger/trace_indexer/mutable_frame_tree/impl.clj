(ns flow-storm.debugger.trace-indexer.mutable-frame-tree.impl
  (:require [flow-storm.debugger.trace-indexer.protos :refer [TraceIndexP FormStoreP]]
            [flow-storm.debugger.trace-types :refer [deref-ser]]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.utils :as utils :refer [log]]
            [clojure.string :as str])
  (:import [java.util ArrayList ArrayDeque HashMap]))

(defrecord ExprData [idx form-id coor result outer-form? timestamp])
(defrecord BindData [coor symbol value timestamp])

(defprotocol CallStackFrameP
  (get-immutable-frame [_])
  (get-expr-exec [_ idx])
  (add-binding [_ bind-trace])
  (add-expr-exec [_ idx exec-trace]))

(deftype CallStackFrame [fn-ns
                         fn-name
                         args-vec
                         frame-idx
                         form-id
                         ^ArrayList bindings
                         ^ArrayList expr-executions]
  CallStackFrameP

  (get-immutable-frame [_]

    (let [frame-ret (when-let [last-expr (last expr-executions)]
                      (when (:outer-form? last-expr)
                        (:result last-expr)))]
      (cond-> {:fn-ns fn-ns
               :fn-name fn-name
               :args-vec args-vec
               :bindings (seq bindings) ;; return a immutable seq
               :expr-executions (seq expr-executions) ;; return a immutable seq
               :form-id form-id
               :frame-idx frame-idx}
        frame-ret (assoc :ret frame-ret))))

  (get-expr-exec [_ idx]
    (.get expr-executions idx))

  (add-binding [_ {:keys [coor symbol value timestamp]}]
    (.add bindings (map->BindData {:coor coor
                                   :symbol symbol
                                   :value value
                                   :timestamp timestamp})))

  (add-expr-exec [_ idx {:keys [coor form-id result outer-form? timestamp]}]
    (.add expr-executions (map->ExprData {:idx idx
                                          :form-id form-id
                                          :coor coor
                                          :result result
                                          :outer-form? outer-form?
                                          :timestamp timestamp}))
    (-> expr-executions .size dec)))

(defprotocol TreeNodeP
  (get-frame [_])
  (get-node-immutable-frame [_])
  (has-childs? [_])
  (add-child [_ node])
  (get-childs [_]))

(deftype TreeNode [^CallStackFrame frame
                   ^ArrayList childs]
  TreeNodeP

  (get-frame [_] frame)
  (get-node-immutable-frame [_] (get-immutable-frame frame))
  (has-childs? [_] (.isEmpty childs))
  (add-child [_ node] (.add childs node))
  (get-childs [_] childs))

(defn- collect-frames [node fn-ns fn-name form-id]
  (let [^CallStackFrame curr-frame (get-frame node)
        frame-data (get-immutable-frame curr-frame)
        node-fn-ns (:fn-ns frame-data)
        node-fn-name (:fn-name frame-data)
        node-fn-args-vec (:args-vec frame-data)
        node-fn-form-id (:form-id frame-data)
        node-frame-idx (:frame-idx frame-data)]

    (cond-> (->> (get-childs node)
                 (mapcat #(collect-frames % fn-ns fn-name form-id))
                 (filter (comp not nil?)))

      (and (= fn-ns node-fn-ns)
           (= fn-name node-fn-name)
           (= form-id node-fn-form-id))
      (conj frame-data))))

(deftype MutableFrameTreeIndexer [root-node
                                  ^ArrayDeque build-stack
                                  ^HashMap forms
                                  ^HashMap timeline-index
                                  ^:unsynchronized-mutable timeline-count]

  TraceIndexP

  (thread-timeline-count [this]
    (locking this
      timeline-count))

  (add-fn-call-trace [this {:keys [fn-name fn-ns args-vec form-id]}]
    (locking this
      (let [new-frame (->CallStackFrame fn-ns
                                        fn-name
                                        args-vec
                                        timeline-count
                                        form-id
                                        (ArrayList.)
                                        (ArrayList.))
            new-node (->TreeNode new-frame (ArrayList.))
            curr-node (.peek build-stack)
            curr-idx timeline-count]
        (add-child curr-node new-node)
        (.put timeline-index curr-idx [new-frame nil])
        (set! timeline-count (inc timeline-count))
        (.push build-stack new-node))))

  (add-exec-trace [this {:keys [outer-form?] :as exec-trace}]
    (locking this
      (let [^TreeNode curr-node (.peek build-stack)
            ^CallStackFrame curr-frame (get-frame curr-node)
            curr-idx timeline-count
            frame-exec-idx (add-expr-exec curr-frame curr-idx exec-trace)]
        (.put timeline-index curr-idx [curr-frame frame-exec-idx])
        (set! timeline-count (inc timeline-count))
        (when outer-form? (.pop build-stack)))))

  (add-bind-trace [this bind-trace]
    (locking this
      (let [^TreeNode curr-node (.peek build-stack)
            ^CallStackFrame curr-frame (get-frame curr-node)]
        (add-binding curr-frame bind-trace))))

  (timeline-entry [this idx]
    (locking this
      (let [[frame frame-exec-idx] (.get timeline-index idx)]
        (if frame-exec-idx

          ;; timeline idx points to a expression execution inside frame
          (-> (get-expr-exec frame frame-exec-idx)
              (assoc :timeline/type :expr))

          ;; timeline idx just points to a frame
          (-> (get-immutable-frame frame)
              (assoc :timeline/type :frame))))))

  (callstack-tree-root [this]
    (locking this
      root-node))

  (callstack-node-frame [this node]
    (locking this
      (let [^CallStackFrame frame (get-frame node)]
        (get-immutable-frame frame))))

  (callstack-tree-childs [this node]
    (locking this
      (get-childs node)))

  (frame-data-for-idx [this idx]
    (locking this
      (let [[frame _] (.get timeline-index idx)]
        (get-immutable-frame frame))))

  (find-fn-frames [this fn-ns fn-name form-id]
    (locking this
      (collect-frames root-node fn-ns fn-name form-id)))

  (search-next-frame-idx [this search-str from-idx print-level on-result-cb on-progress]
     (locking this
       (let [search-thread (Thread.
                            (fn []
                              (let [total-traces (.size timeline-index)
                                    match-stack (loop [i 0
                                                       stack ()]
                                                  (when (and (< i total-traces)
                                                             (not (.isInterrupted (Thread/currentThread))))

                                                    (when (zero? (mod i 10000))
                                                      (on-progress (* 100 (/ i total-traces))))

                                                    (let [[curr-frame x] (.get timeline-index i)
                                                          curr-frame-data (get-immutable-frame curr-frame)]
                                                      (if (nil? x)

                                                        ;; it is a fn-call
                                                        (let [{:keys [fn-name args-vec]} curr-frame-data]
                                                          (if (and (> i from-idx)
                                                                   (or (str/includes? fn-name search-str)
                                                                       (str/includes? (deref-ser args-vec {:print-length 10 :print-level print-level :pprint? false}) search-str)))

                                                            ;; if matches
                                                            (conj stack i)

                                                            ;; else
                                                            (recur (inc i) (conj stack i))))

                                                        ;; else expr, check if it is returning
                                                        (if (:outer-form? (get-expr-exec curr-frame x))
                                                          (recur (inc i) (pop stack))
                                                          (recur (inc i) stack))))))]
                                (when (.isInterrupted (Thread/currentThread))
                                  (log "Search stopped"))
                                (on-result-cb match-stack))))]
         (reset! ui-vars/long-running-task-thread search-thread)
         (.start search-thread))))

  FormStoreP

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
      (.get forms form-id))))


(defn make-indexer []
  (let [root-frame (->CallStackFrame nil nil nil nil nil nil nil)
        root-node (->TreeNode root-frame (ArrayList.))
        build-stack (ArrayDeque.)
        forms (HashMap.)
        timeline-index (HashMap.)]
    (.push build-stack root-node)
    (->MutableFrameTreeIndexer root-node
                               build-stack
                               forms
                               timeline-index
                               0)))
