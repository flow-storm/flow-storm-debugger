(ns flow-storm.debugger.state
  (:require [flow-storm.state-management :refer [defstate]]))

(def initial-state {:flows {}
                    :selected-flow-id nil
                    :printers {}})

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

(defn update-thread-info [thread-id info]
  (swap! state assoc-in [:threads-info thread-id] info))

(defn get-thread-info [thread-id]
  (get-in @state [:threads-info thread-id]))

(defn get-flow [flow-id]
  (get-in @state [:flows flow-id]))

(defn create-thread [flow-id thread-id]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id]
         {:thread/id thread-id
          :thread/curr-timeline-entry nil
          :thread/callstack-tree-hidden-fns #{}}))

(defn get-thread [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id]))

(defn current-timeline-entry [flow-id thread-id]
  (:thread/curr-timeline-entry (get-thread flow-id thread-id)))

(defn current-idx [flow-id thread-id]
  (:idx (current-timeline-entry flow-id thread-id)))

(defn set-current-timeline-entry [flow-id thread-id entry]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-timeline-entry] entry))

(defn set-current-frame [flow-id thread-id frame-data]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-frame] frame-data))

(defn current-frame [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id :thread/curr-frame]))

(defn callstack-tree-hide-fn [flow-id thread-id fn-name fn-ns]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns] conj {:name fn-name :ns fn-ns}))

(defn callstack-tree-hidden? [flow-id thread-id fn-name fn-ns]
  (let [hidden-set (get-in @state [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns])]
    (contains? hidden-set {:name fn-name :ns fn-ns})))

(defn set-sytem-fully-started []
  (swap! state assoc :system-fully-started? true))

(defn system-fully-started? []
  (get @state :system-fully-started?))

(defn add-printer [form-id coord printer-data]
  (swap! state assoc-in [:printers form-id coord] printer-data))

(defn printers []
  (get @state :printers))

(defn remove-printer [form-id coord]
  (swap! state update-in [:printers form-id] dissoc coord))

(defn update-printer [form-id coord k new-val]
  (swap! state assoc-in [:printers form-id coord k] new-val))
