(ns flow-storm.runtime.types.fn-return-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(defn- print-ret [ret-trace]
  (utils/format "#flow-storm/return-trace [Idx: %d, Coord: %s, Type: %s]"
                (index-protos/entry-idx ret-trace)
                (index-protos/get-coord-raw ret-trace)
                (pr-str (type (index-protos/get-expr-val ret-trace)))))

(deftype FnReturnTrace
    [     coord
          retVal
     ^int fnCallIdx
     ^int thisIdx]

  index-protos/ExpressionTimelineEntryP
  (get-expr-val [_] retVal)
  
  index-protos/CoordableTimelineEntryP
  (get-coord-vec [_] (utils/str-coord->vec coord))
  (get-coord-raw [_] coord)
  
  index-protos/TimelineEntryP

  (entry-type [_] :fn-return)
  (entry-idx [_]
    (when (not= thisIdx nil-idx)
      thisIdx))
  (fn-call-idx [_]
    (when (not= fnCallIdx nil-idx)
      fnCallIdx))

  
  index-protos/ImmutableP
  
  (as-immutable [this]
    {:type :fn-return
     :coord (index-protos/get-coord-vec this)
     :result (index-protos/get-expr-val this)
     :fn-call-idx (index-protos/fn-call-idx this)
     :idx (index-protos/entry-idx this)})

  #?@(:cljs
      [IPrintWithWriter
       (-pr-writer [this writer _]
                   (write-all writer (print-ret this)))]))


#?(:clj
   (defmethod print-method FnReturnTrace [ret-trace ^java.io.Writer w]
     (.write w ^String (print-ret ret-trace))))

(defn make-fn-return-trace [coord ret-val this-idx fn-call-idx]
  (->FnReturnTrace coord ret-val fn-call-idx this-idx))

(defn fn-return-trace? [x]
  (and x (instance? FnReturnTrace x)))

(defn- print-unw [unwind-trace]
  (utils/format "#flow-storm/unwind-trace [Idx: %d, Coord: %s, ExType: %s]"
                (index-protos/entry-idx unwind-trace)
                (index-protos/get-coord-raw unwind-trace)
                (pr-str (type (index-protos/get-throwable unwind-trace)))))

(deftype FnUnwindTrace
    [     coord
          throwable
     ^int fnCallIdx
     ^int thisIdx]
  
  index-protos/CoordableTimelineEntryP
  (get-coord-vec [_] (utils/str-coord->vec coord))
  (get-coord-raw [_] coord)

  index-protos/UnwindTimelineEntryP
  (get-throwable [_]
    throwable)
  
  index-protos/TimelineEntryP

  (entry-type [_] :fn-unwind)
  (entry-idx [_]
    (when (not= thisIdx nil-idx)
      thisIdx))
  (fn-call-idx [_]
    (when (not= fnCallIdx nil-idx)
      fnCallIdx))

  
  index-protos/ImmutableP
  
  (as-immutable [this]
    {:type :fn-unwind
     :coord (index-protos/get-coord-vec this)
     :throwable (index-protos/get-throwable this)
     :fn-call-idx (index-protos/fn-call-idx this)
     :idx (index-protos/entry-idx this)})

  #?@(:cljs
      [IPrintWithWriter
       (-pr-writer [this writer _]
                   (write-all writer (print-unw this)))]))

#?(:clj
   (defmethod print-method FnUnwindTrace [unwind-trace ^java.io.Writer w]
     (.write w ^String (print-unw unwind-trace))))

(defn make-fn-unwind-trace [coord throwable this-idx fn-call-idx]
  (->FnUnwindTrace coord throwable fn-call-idx this-idx))

(defn fn-unwind-trace? [x]
  (and x (instance? FnUnwindTrace x)))

(defn fn-end-trace? [x]
  (or (fn-return-trace? x)
      (fn-unwind-trace? x)))
