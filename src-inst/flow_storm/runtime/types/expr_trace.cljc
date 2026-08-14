(ns flow-storm.runtime.types.expr-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(defn- print-it [expr]
  (utils/format "#flow-storm/expr-trace {:coord %s, :result %s, :fn-call-idx %s}"
                (index-protos/get-coord-vec expr)
                (pr-str (type (index-protos/get-expr-val expr)))
                (index-protos/fn-call-idx expr)))

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
  
  #?@(:clj
      [clojure.lang.ILookup
       (valAt [this k] (.valAt this k nil))
       (valAt [this k not-found]
              (case k
                :coord (index-protos/get-coord-vec this)
                :result (index-protos/get-expr-val this)
                :fn-call-idx (index-protos/fn-call-idx this)
                not-found))]
      
      :cljs
      [ILookup
       (-lookup [this k] (-lookup this k nil))
       (-lookup [this k not-found]
                (case k
                  :coord (index-protos/get-coord-vec this)
                  :result (index-protos/get-expr-val this)
                  :fn-call-idx (index-protos/fn-call-idx this)
                  not-found))

       IPrintWithWriter
       (-pr-writer [this writer _]
                   (write-all writer (print-it this)))]
      ))

#?(:clj
   (defmethod print-method ExprTrace [expr ^java.io.Writer w]
     (.write w ^String (print-it expr))))

(defn make-expr-trace [coord expr-val fn-call-idx]
  (->ExprTrace coord expr-val fn-call-idx))

(defn expr-trace? [x]
  (and x (instance? ExprTrace x)))
