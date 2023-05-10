(ns flow-storm.runtime.types.bind-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(defprotocol BindTraceP
  (get-sym-name [_])
  (get-val [_])
  (get-coord [_])
  (set-visible-after [_ idx]))

(deftype BindTrace
    [symName
     val
     coord
     ^:unsynchronized-mutable ^int visibleAfterIdx]

  BindTraceP

  (get-sym-name [_] symName)
  (get-val [_] val)
  (get-coord [_] (utils/str-coord->vec coord))
  (set-visible-after [_ idx]
    (set! visibleAfterIdx (int idx)))

  index-protos/ImmutableP
  
  (as-immutable [this]
    {:type :bind
     :symbol (get-sym-name this)
     :value (get-val this)
     :coord (get-coord this)
     :visible-after visibleAfterIdx})

  #?@(:clj
      [Object
       (toString [_] (utils/format "[BindTrace] coord: %s, sym: %s, valType: %s" coord symName (type val)))]))

(defn make-bind-trace [sym-name val coord]
  (->BindTrace sym-name val coord nil-idx))

(defn bind-trace? [x]
  (and x (instance? BindTrace x)))
