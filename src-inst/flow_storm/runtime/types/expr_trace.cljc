(ns flow-storm.runtime.types.expr-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(defprotocol ExprTraceP
  (get-coord [_])
  (get-expr-val [_])
  (set-idx [_ idx])
  (set-fn-call-idx [_ idx]))

(deftype ExprTrace
    [coord
     exprVal
     ^:unsynchronized-mutable ^int fnCallIdx
     ^:unsynchronized-mutable ^int thisIdx]

  ExprTraceP

  (get-coord [_] (utils/str-coord->vec coord))
  (get-expr-val [_] exprVal)
  (set-idx [_ idx]
    (set! thisIdx (int idx)))
  (set-fn-call-idx [_ idx]
    (set! fnCallIdx (int idx)))

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
     :coord (get-coord this)
     :result (get-expr-val this)
     :fn-call-idx (index-protos/fn-call-idx this)
     :idx (index-protos/entry-idx this)})
  
  #?@(:clj
      [Object
       (toString [_] (utils/format "[ExprTrace] coord: %s, valType: %s" coord (type exprVal)))]))

(defn make-expr-trace [coord expr-val]
  (->ExprTrace coord expr-val nil-idx nil-idx))

(defn expr-trace? [x]
  (and x (instance? ExprTrace x)))
