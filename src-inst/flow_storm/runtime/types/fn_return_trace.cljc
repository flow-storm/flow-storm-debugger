(ns flow-storm.runtime.types.fn-return-trace
  #?(:clj (:require [flow-storm.utils :as utils])))

(defprotocol FnReturnTraceP
  (get-form-id [_])
  (get-timestamp [_])
  (get-coord [_])
  (get-ret-val [_])
  (get-timeline-idx [_])
  (set-timeline-idx [_ idx])
  (get-frame-node [_])
  (set-frame-node [_ node]))

(deftype FnReturnTrace
    [^int                          formId
     ^:unsynchronized-mutable ^int timelineIdx
     ^long                         timestamp
                                   coord
                                   retVal
     ^:unsynchronized-mutable      frameNode]

  FnReturnTraceP

  (get-form-id [_] formId)
  (get-timestamp [_] timestamp)
  (get-coord [_] coord)
  (get-ret-val [_] retVal)
  (get-timeline-idx [_] timelineIdx)
  (set-timeline-idx [_ idx] (set! timelineIdx (int idx)))
  (get-frame-node [_] frameNode)
  (set-frame-node [_ node]
    (set! frameNode node))

  #?@(:clj
      [Object
       (toString [_] (utils/format "[FnReturnTrace] retValType: %s" (type retVal)))])
  )

(defn make-fn-return-trace [form-id timestamp coord ret-val]
  (->FnReturnTrace form-id 0 timestamp coord ret-val nil))

(defn fn-return-trace? [x]
  (and x (instance? FnReturnTrace x)))
