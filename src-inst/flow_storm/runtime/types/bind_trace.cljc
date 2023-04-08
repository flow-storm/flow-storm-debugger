(ns flow-storm.runtime.types.bind-trace
  (:require [flow-storm.utils :as utils]))

(defprotocol BindTraceP
  (get-timestamp [_])
  (get-sym-name [_])
  (get-val [_])
  (get-coord [_]))

(deftype BindTrace
    [^long timestamp
           symName
           val
           coord]

  BindTraceP

  (get-timestamp [_] timestamp)
  (get-sym-name [_] symName)
  (get-val [_] val)
  (get-coord [_] coord)

  Object
  (toString [_] (utils/format "[BindTrace] coord: %s, sym: %s, valType: %s" coord symName (type val))))

(defn make-bind-trace [timestamp sym-name val coord]
  (->BindTrace timestamp sym-name val coord))

(defn bind-trace? [x]
  (and x (instance? BindTrace x)))
