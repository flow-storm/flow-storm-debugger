(ns flow-storm-debugger.ui.subs.timeline
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.subs.refs :as subs.refs]
            [flow-storm-debugger.ui.subs.taps :as subs.taps]
            [taoensso.timbre :as log]))

(defn partition-traces [traces]
  (log/debug "[SUB] partition-traces firing")
  (loop [partitions []
         batch []
         batch-last nil
         [t & tr] traces]
    (if-not t

      (if (empty? batch)
        partitions
        (conj partitions batch))

      (cond

        ;; if t is a fn call it gets it's own partition 
        (subs.flows/fn-call-trace? t)
        (recur (conj partitions [t])
               []
               nil
               tr)

        ;; if the batch is empty start a new one
        (empty? batch)
        (recur partitions
               [t]
               t
               tr)

        ;; if we are changing flow-id start a new batch
        (not= (:flow-id t) (:flow-id batch-last))
        (recur (conj partitions batch)
               [t]
               t
               tr)

        ;; else accumulate on the batch
        :else
        (recur partitions
               (conj batch t)
               t
               tr)))))

(defn- all-flows-fn-call-traces [context]
  (log/debug "[SUB]  all-flows-fn-call-traces firing")
  (let [flows-traces (fx/sub-ctx context subs.flows/all-flows-traces)]
    (->> flows-traces
         (partition-traces)
         (map (fn [part]
                (if (and (= (count part) 1) (subs.flows/fn-call-trace? (first part)))
                  
                  (assoc (first part) :trace/type :flow-fn-call)
                  
                  (let [{:keys [flow-id flow-name trace-idx timestamp]} (first part)]
                    {:trace/type :flow-group
                     :flow-name flow-name
                     :flow-id flow-id
                     :trace-idx trace-idx
                     :trace-group-count (count part)
                     :timestamp timestamp})))))))

(defn- all-ref-traces [context]
  (log/debug "[SUB]  all-ref-traces firing")
  (let [refs (fx/sub-ctx context subs.refs/refs)
        ref-traces (fn [{:keys [ref-name init-val patches ref-id timestamp]}]
                     (let [init-trace {:ref-id ref-id
                                       :ref-name ref-name
                                       :init-val init-val
                                       :timestamp timestamp}
                           patches-traces (map-indexed (fn [i p]
                                                         (assoc p
                                                                :ref-name ref-name
                                                                :patch-idx i)) patches)]
                       (-> [init-trace]
                           (into patches-traces))))]
    (->> (vals refs)
         (mapcat ref-traces)
         (map (fn [t] (assoc t :trace/type :ref))))))

(defn- all-tap-traces [context]
  (log/debug "[SUB]  all-tap-traces firing")
  (let [taps (fx/sub-ctx context subs.taps/taps)]
   (->> (vals taps)
        (mapcat :tap-values )
        (map (fn [t] (assoc t :trace/type :tap))))))

(defn timeline [context]
  (log/debug "[SUB] timeline firing")
  (let [traces (-> (fx/sub-ctx context all-flows-fn-call-traces)
                   (into (fx/sub-ctx context all-ref-traces))
                   (into (fx/sub-ctx context all-tap-traces)))]
    (sort-by :timestamp traces)))
