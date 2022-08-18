(ns flow-storm.debugger.state
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]])
  (:import [java.util HashMap Map$Entry]))

(def orphans-flow-id -1)

(def initial-state {:flows {}
                    :selected-flow-id nil})

;; so linter doesn't complain
(declare state)
(declare fn-call-stats-map)
(declare flow-thread-indexers)

(defstate state
  :start (atom initial-state)
  :stop nil)

;;;;;;;;;;;
;; Utils ;;
;;;;;;;;;;;

(defn create-flow [flow-id form-ns form timestamp]
  ;; if a flow for `flow-id` already exist we discard it and
  ;; will be GCed

  (swap! state assoc-in [:flows flow-id] {:flow/id flow-id
                                          :flow/threads {}
                                          ;; the form that started the flow
                                          :flow/execution-expr {:ns form-ns
                                                                :form form}
                                          :timestamp timestamp}))

(defn remove-flow [flow-id]
  (swap! state update :flows dissoc flow-id))

(defn all-flows-ids []
  (keys (get @state :flows)))

(defn get-flow [flow-id]
  (get-in @state [:flows flow-id]))

(defn create-thread [flow-id thread-id]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id]
         {:thread/id thread-id
          :thread/curr-idx nil
          :thread/callstack-tree-hidden-fns #{}
          :thread/callstack-expanded-traces #{}
          :thread/callstack-selected-idx nil}))

(defn get-thread [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id]))

(defn current-idx [flow-id thread-id]
  (:thread/curr-idx (get-thread flow-id thread-id)))

(defn set-idx [flow-id thread-id idx]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-idx] idx))

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
