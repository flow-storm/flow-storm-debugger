(ns flow-storm.runtime.types.expr-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(defn- print-it [expr]
  (utils/format "#flow-storm/expr-trace [Coord: %s, Type: %s]"
                (index-protos/get-coord-raw expr)
                (pr-str (type (index-protos/get-expr-val expr)))))

(deftype ExprTrace
    [coord
     exprVal
     ^int fnCallIdx]

  index-protos/ExpressionTimelineEntryP
  (get-expr-val [_] exprVal)
  
  index-protos/CoordableTimelineEntryP
  (get-coord-vec [_] (utils/str-coord->vec coord))
  (get-coord-raw [_] coord)
  
  index-protos/TimelineEntryP

  (entry-type [_] :expr)

  index-protos/FnChildTimelineEntryP
  (fn-call-idx [_]
    (when (not= fnCallIdx nil-idx)
      fnCallIdx))
  
  index-protos/ImmutableP
  
  (as-immutable [this]
    {:type :expr
     :coord (index-protos/get-coord-vec this)
     :result (index-protos/get-expr-val this)
     :fn-call-idx (index-protos/fn-call-idx this)})

  #?@(:cljs
      [IPrintWithWriter
       (-pr-writer [this writer _]
                   (write-all writer (print-it this)))]))

#?(:clj
   (defmethod print-method ExprTrace [expr ^java.io.Writer w]
     (.write w ^String (print-it expr))))

(defn make-expr-trace [coord expr-val fn-call-idx]
  (->ExprTrace coord expr-val fn-call-idx))

(defn expr-trace? [x]
  (and x (instance? ExprTrace x)))
