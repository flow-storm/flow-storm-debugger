(ns flow-storm.runtime.types.bind-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :as utils]))

(def nil-idx -1)

(deftype BindTrace
    [symName
     val
     coord
     ^int visibleAfterIdx]

  index-protos/CoordableTimelineEntryP
  (get-coord-vec [_] (utils/str-coord->vec coord))
  (get-coord-raw [_] coord)
  
  index-protos/BindTraceP

  (get-bind-sym-name [_] symName)
  (get-bind-val [_] val)
  
  index-protos/ImmutableP
  
  (as-immutable [this]
    {:type :bind
     :symbol (index-protos/get-bind-sym-name this)
     :value (index-protos/get-bind-val this)
     :coord (index-protos/get-coord-vec this)
     :visible-after visibleAfterIdx})

  #?@(:clj
      [Object
       (toString [_] (utils/format "[BindTrace] coord: %s, sym: %s, valType: %s" coord symName (type val)))]))

(defn make-bind-trace [sym-name val coord visible-after-idx]
  (->BindTrace sym-name val coord visible-after-idx))

(defn bind-trace? [x]
  (and x (instance? BindTrace x)))
