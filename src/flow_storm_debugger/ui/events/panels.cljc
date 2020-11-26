(ns flow-storm-debugger.ui.events.panels
  (:require [clojure.set :refer [map-invert]]
            [flow-storm-debugger.ui.events.flows :as events.flows]
            [flow-storm-debugger.ui.events.refs :as events.refs]
            [flow-storm-debugger.ui.events.taps :as events.taps]))


(def tool->idx {:flows    0
                :refs     1
                :taps     2
                :timeline 3})

(def idx->tool (map-invert tool->idx))

(defn focus-thing [db thing]
  (case (:thing/type thing)
    :flow (let [{:keys [flow-id trace-idx]} thing]
            (-> db
                (assoc :selected-tool-idx (tool->idx :flows))
                (assoc :selected-flow-id flow-id)
                (assoc-in [:flows flow-id :trace-idx] trace-idx)))
    :ref  (let [{:keys [ref-id patch-idx]} thing]
            (let [db' (-> db
                          (assoc :selected-tool-idx (tool->idx :refs))
                          (assoc :selected-ref-id ref-id))]
              (if patch-idx
                (assoc-in db' [:refs ref-id :patches-applied] (inc patch-idx))
                (assoc-in db' [:refs ref-id :patches-applied] 0))))    
    :tap  (let [{:keys [tap-id tap-trace-idx]} thing]
            (-> db
                (assoc :selected-tool-idx (tool->idx :taps))
                (assoc :selected-tap-id tap-id)
                (assoc-in [:taps tap-id :tap-trace-idx] tap-trace-idx)))))

(defn remove-current-tool-tab [{:keys [selected-tool-idx] :as db}]
  (case (idx->tool selected-tool-idx)
    :flows (events.flows/remove-selected-flow db)
    :refs  (events.refs/remove-selected-ref db)
    :taps  (events.taps/remove-selected-tap db)
    :timeline db))

(defn remove-all-current-tool-tabs [{:keys [selected-tool-idx] :as db}]
  (case (idx->tool selected-tool-idx)
    :flows (events.flows/remove-all-flows db)
    :refs  (events.refs/remove-all-refs db)
    :taps  (events.taps/remove-all-taps db)
    :timeline db)
  )
