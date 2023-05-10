(ns flow-storm.runtime.types.fn-return-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(defprotocol FnReturnTraceP
  (get-coord [_])
  (get-ret-val [_])
  (set-fn-call-idx [_ idx])
  (set-idx [_ idx]))

(deftype FnReturnTrace
    [                              coord
                                   retVal
     ^:unsynchronized-mutable ^int fnCallIdx
     ^:unsynchronized-mutable ^int thisIdx]

  FnReturnTraceP

  (get-coord [_] (utils/str-coord->vec coord))
  (get-ret-val [_] retVal)
  (set-fn-call-idx [_ idx]
    (set! fnCallIdx (int idx)))
  (set-idx [_ idx]
    (set! thisIdx (int idx)))

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
     :coord (get-coord this)
     :result (get-ret-val this)
     :fn-call-idx (index-protos/fn-call-idx this)
     :idx (index-protos/entry-idx this)})
  
  #?@(:clj
      [Object
       (toString [_] (utils/format "[FnReturnTrace] retValType: %s" (type retVal)))])
  )

(defn make-fn-return-trace [coord ret-val]
  (->FnReturnTrace coord ret-val nil-idx nil-idx))

(defn fn-return-trace? [x]
  (and x (instance? FnReturnTrace x)))
