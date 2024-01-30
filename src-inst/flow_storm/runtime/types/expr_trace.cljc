(ns flow-storm.runtime.types.expr-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(defprotocol ExprTraceP  
  (set-idx [_ idx])
  (set-fn-call-idx [_ idx]))

(deftype ExprTrace
    [coord
     exprVal
     ^:unsynchronized-mutable ^int fnCallIdx
     ^:unsynchronized-mutable ^int thisIdx]

  index-protos/ExpressionTimelineEntryP
  (get-expr-val [_] exprVal)
  
  ExprTraceP
    
  (set-idx [_ idx]
    (set! thisIdx (int idx)))
  (set-fn-call-idx [_ idx]
    (set! fnCallIdx (int idx)))

  index-protos/CoordableTimelineEntryP
  (get-coord-vec [_] (utils/str-coord->vec coord))
  (get-coord-raw [_] coord)
  
  index-protos/TimelineEntryP

  (entry-type [_] :expr)
  (fn-call-idx [_]
    (when (not= fnCallIdx nil-idx)
      fnCallIdx))
  (entry-idx [_]
    (when (not= thisIdx nil-idx)
      thisIdx))
  
  index-protos/ImmutableP
  
  (as-immutable [this]
    {:type :expr
     :coord (index-protos/get-coord-vec this)
     :result (index-protos/get-expr-val this)
     :fn-call-idx (index-protos/fn-call-idx this)
     :idx (index-protos/entry-idx this)})
  
  #?@(:clj
      [Object
       (toString [_] (utils/format "[%d ExprTrace] coord: %s, valType: %s" thisIdx coord (type exprVal)))]))

(defn make-expr-trace [coord expr-val]
  (->ExprTrace coord expr-val nil-idx nil-idx))

(defn expr-trace? [x]
  (and x (instance? ExprTrace x)))
