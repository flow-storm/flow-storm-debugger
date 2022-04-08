(ns flow-storm.debugger.trace-indexer.immutable.callstack-tree
  (:require [clojure.zip :as zip]))

(defn- make-tree-node [{:keys [form-id fn-name fn-ns args-vec timestamp]} trace-idx]
  {:fn-name fn-name
   :call-trace-idx trace-idx
   :fn-ns fn-ns
   :frame-mut-data-ref (atom {:min-trace-idx Long/MAX_VALUE
                              :max-trace-idx 0})
   :args args-vec
   :timestamp timestamp
   :form-id form-id
   :bindings {}
   :calls []})

(defn make-call-tree [fn-call-trace trace-idx]
  (let [frame (make-tree-node fn-call-trace trace-idx)]
    {:zipper (zip/zipper :calls
                         :calls
                         (fn [node children] (assoc node :calls children))
                         frame)
     :trace-idx->frame {0 frame}}))

(defn process-fn-call-trace [callstack-tree trace-idx fn-call-trace]
  (let [frame (make-tree-node fn-call-trace trace-idx)]
    (-> callstack-tree
        (update :zipper (fn [z]
                          (swap! (-> z zip/node :frame-mut-data-ref)
                                 (fn [data]
                                   (-> data
                                       (update :min-trace-idx min trace-idx)
                                       (update :max-trace-idx max trace-idx))))
                          (-> z
                              (zip/append-child frame)
                              zip/down
                              zip/rightmost)))
        (update :trace-idx->frame assoc trace-idx frame))))

(defn process-bind-trace [callstack-tree {:keys [symbol value]}]
  (-> callstack-tree
      (update :zipper (fn [z]
                        (-> z
                            (zip/edit (fn [node]
                                        (update node :bindings assoc symbol value))))))))

(defn process-exec-trace [callstack-tree trace-idx {:keys [result outer-form?]}]
  (let [callstack-tree-1 (update callstack-tree :zipper
                                 (fn [z]
                                   (swap! (-> z zip/node :frame-mut-data-ref)
                                          (fn [data]
                                            (cond-> data
                                              true        (update :min-trace-idx min trace-idx)
                                              true        (update :max-trace-idx max trace-idx)
                                              outer-form? (assoc :ret result
                                                                 :ret-trace-idx trace-idx))))
                                   z))
        callstack-tree-2 (update callstack-tree-1 :trace-idx->frame assoc trace-idx (zip/node (:zipper callstack-tree-1)))
        callstack-tree-3 (update callstack-tree-2 :zipper
                                 (fn [z]
                                   (if outer-form?
                                     (if-let [up (zip/up z)]
                                       up
                                       z)
                                     z)))]
    callstack-tree-3))

(defn callstack-tree-root [{:keys [zipper]}]
  (zip/root zipper))

(defn find-frame [{:keys [trace-idx->frame]} trace-idx]
  (trace-idx->frame trace-idx))
