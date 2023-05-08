(ns flow-storm.debugger.state
  (:require [flow-storm.state-management :refer [defstate]]))

(def initial-state {:flows {}
                    :selected-flow-id nil})

;; so linter doesn't complain
(declare state)
(declare fn-call-stats-map)
(declare flow-thread-indexers)

(defstate state
  :start (fn [_] (atom initial-state))
  :stop (fn [] (atom initial-state)))

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
          :thread/callstack-tree-hidden-fns #{}}))

(defn get-thread [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id]))

(defn current-idx [flow-id thread-id]
  (:thread/curr-idx (get-thread flow-id thread-id)))

(defn set-idx [flow-id thread-id idx]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-idx] idx))

(defn set-current-frame [flow-id thread-id frame-data]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-frame] frame-data))

(defn current-frame [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id :thread/curr-frame]))

(defn next-idx-in-frame [flow-id thread-id]
  (let [curr-idx (current-idx flow-id thread-id)
        {:keys [expr-executions]} (current-frame flow-id thread-id)]
    (or (->> expr-executions
          (drop-while #(<= (:idx %) curr-idx))
          first
          :idx)
        curr-idx)))

(defn prev-idx-in-frame [flow-id thread-id]
  (let [curr-idx (current-idx flow-id thread-id)
        {:keys [expr-executions]} (current-frame flow-id thread-id)]
    (loop [[{:keys [idx]} & rest-expr] expr-executions
           prev-idx nil]
      (if-not idx
        ;; if we reach the end just return curr-idx
        curr-idx

        ;; else keep searching forward while the idx
        ;; we are looking at is < than our curr-idx
        (if (< idx curr-idx)
          (recur rest-expr idx)
          (or prev-idx curr-idx))))))

(defn callstack-tree-hide-fn [flow-id thread-id fn-name fn-ns]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns] conj {:name fn-name :ns fn-ns}))

(defn callstack-tree-hidden? [flow-id thread-id fn-name fn-ns]
  (let [hidden-set (get-in @state [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns])]
    (contains? hidden-set {:name fn-name :ns fn-ns})))

(defn set-sytem-fully-started []
  (swap! state assoc :system-fully-started? true))

(defn system-fully-started? []
  (get @state :system-fully-started?))
