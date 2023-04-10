(ns flow-storm.runtime.types.expr-trace
  #?(:clj (:require [flow-storm.utils :as utils])))

(defprotocol ExprTraceP
  (get-form-id [_])
  (get-timeline-idx [_])
  (set-timeline-idx [_ idx])
  (get-timestamp [_])
  (get-coord [_])
  (get-expr-val [_])
  (get-frame-node [_])
  (set-frame-node [_ node]))

(deftype ExprTrace
    [^int                          formId
     ^:unsynchronized-mutable ^int timelineIdx
     ^long                         timestamp
                                   coord
                                   exprVal
     ^:unsynchronized-mutable      frameNode]

  ExprTraceP

  (get-form-id [_] formId)
  (get-timeline-idx [_] timelineIdx)
  (set-timeline-idx [_ idx] (set! timelineIdx (int idx)))
  (get-timestamp [_] timestamp)
  (get-coord [_] coord)
  (get-expr-val [_] exprVal)
  (get-frame-node [_] frameNode)
  (set-frame-node [_ node] (set! frameNode node))

  #?@(:clj
      [Object
       (toString [_] (utils/format "[ExprTrace] coord: %s, formId: %d, valType: %s" coord formId (type exprVal)))]))

(defn make-expr-trace [form-id timestamp coord expr-val]
  (->ExprTrace form-id 0 timestamp coord expr-val nil))

(defn expr-trace? [x]
  (and x (instance? ExprTrace x)))
