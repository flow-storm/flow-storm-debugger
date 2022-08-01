(ns flow-storm.debugger.state
  (:require [flow-storm.debugger.trace-indexer.protos :as indexer]
            [mount.core :as mount :refer [defstate]])
  (:import [java.util HashMap Map$Entry]))

(def orphans-flow-id -1)

(def initial-state {:flows {}
                    :selected-flow-id nil})

;; so linter doesn't complain
(declare state)
(declare fn-call-stats-map)

(defstate state
  :start (atom initial-state)
  :stop nil)

(defstate ^HashMap fn-call-stats-map
  :start (HashMap.)
  :stop  nil)

;;;;;;;;;;;
;; Utils ;;
;;;;;;;;;;;

(defn clear-flow-fn-call-stats [flow-id]
  (locking fn-call-stats-map
    (let [flow-keys (->> (.entrySet fn-call-stats-map)
                         (keep (fn [^Map$Entry entry]
                                 (let [key (.getKey entry)]
                                   (when (= flow-id (first key))
                                     key))))
                         doall)]
      (doseq [key flow-keys]
        (.remove fn-call-stats-map key)))))

(defn create-flow [flow-id exec-form-ns exec-form timestamp]
  ;; if a flow for `flow-id` already exist we discard it and
  ;; will be GCed

  (swap! state assoc-in [:flows flow-id] {:flow/id flow-id
                                          :flow/threads {}
                                          :flow/execution-expr {:ns exec-form-ns
                                                                :form exec-form}
                                          :timestamp timestamp})

  (clear-flow-fn-call-stats flow-id))

(defn remove-flow [flow-id]
  (swap! state update :flows dissoc flow-id)
  (clear-flow-fn-call-stats flow-id))

(defn get-flow [flow-id]
  (get-in @state [:flows flow-id]))

(defn create-thread [flow-id thread-id trace-indexer]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id]
         {:thread/id thread-id
          :thread/trace-indexer trace-indexer
          :thread/curr-idx nil
          :thread/callstack-tree-hidden-fns #{}
          :thread/callstack-expanded-traces #{}
          :thread/callstack-selected-idx nil
          :thread/fn-call-stats {}}))

(defn get-thread [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id]))

(defn thread-trace-indexer [flow-id thread-id]
  (:thread/trace-indexer (get-thread flow-id thread-id)))

(defn thread-trace-count [flow-id thread-id]
  (indexer/thread-timeline-count (thread-trace-indexer flow-id thread-id)))

(defn current-idx [flow-id thread-id]
  (:thread/curr-idx (get-thread flow-id thread-id)))

(defn set-idx [flow-id thread-id idx]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-idx] idx))

(defn update-fn-call-stats [flow-id thread-id {:keys [fn-ns fn-name form-id]}]
  (locking fn-call-stats-map
    (let [k [flow-id thread-id fn-ns fn-name form-id]
          curr-val (.getOrDefault fn-call-stats-map k 0)]
      (.put fn-call-stats-map k (inc curr-val)))))

(defn fn-call-stats [target-flow-id target-thread-id]
  (locking fn-call-stats-map
    (let [indexer (thread-trace-indexer target-flow-id target-thread-id)]
      (->> (.entrySet fn-call-stats-map)
           (keep (fn [^Map$Entry entry]
                   (let [[flow-id thread-id fn-ns fn-name form-id] (.getKey entry)
                         {:keys [form/form form/def-kind multimethod/dispatch-val]} (indexer/get-form indexer form-id)]
                     (when (and (= target-flow-id flow-id)
                                (= target-thread-id thread-id))
                       {:fn-ns fn-ns
                        :fn-name fn-name
                        :form-id form-id
                        :form form
                        :form-def-kind def-kind
                        :dispatch-val dispatch-val
                        :cnt (.getValue entry)}))))))))

(defn callstack-tree-hide-fn [flow-id thread-id fn-name fn-ns]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns] conj {:name fn-name :ns fn-ns}))

(defn callstack-tree-hidden? [flow-id thread-id fn-name fn-ns]
  (let [hidden-set (get-in @state [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns])]
    (contains? hidden-set {:name fn-name :ns fn-ns})))

(defn callstack-tree-item-expanded? [flow-id thread-id frame-idx]
  (let [expanded-set (get-in @state [:flows flow-id :flow/threads thread-id :thread/callstack-expanded-traces])]
    (contains? expanded-set frame-idx)))

(defn callstack-tree-expand-calls [flow-id thread-id fn-call-trace-indexes]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/callstack-expanded-traces] into fn-call-trace-indexes))

(defn callstack-tree-select-path [flow-id thread-id select-path]
  (let [[target-id & parents-ids] select-path]
    (swap! state update-in [:flows flow-id :flow/threads thread-id]
           (fn [thread]
             (-> thread
                 (assoc :thread/callstack-expanded-traces (into #{} parents-ids))
                 (assoc :thread/callstack-selected-idx target-id))))))

(defn callstack-tree-collapse-calls [flow-id thread-id fn-call-trace-indexes]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/callstack-expanded-traces] (fn [traces] (apply disj traces fn-call-trace-indexes))))

(defn callstack-tree-collapse-all-calls [flow-id thread-id]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/callstack-expanded-traces] #{}))
