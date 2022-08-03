(ns flow-storm.json-serializer
  (:require [cognitect.transit :as transit :refer [tagged-value write-handler]]
            [flow-storm.utils :refer [log-error]]
            [flow-storm.value-types :as value-types :refer [LocalImmValue RemoteImmValue]]))

(defn serialize [o]
  (try
    (let [writer (transit/writer :json {:handlers {LocalImmValue (write-handler (fn [_ _] "flow_storm.value_types.LocalImmValue") (fn [rec] (tagged-value "map" rec)) )
                                                   RemoteImmValue (write-handler (fn [_ _] "flow_storm.value_types.RemoteImmValue") (fn [rec] (tagged-value "map" rec)) )
                                                   js/RegExp     (write-handler (fn [_ _] "regex")           str)
                                                   :default      (write-handler (fn [_ _] "object")        pr-str)}})]
      (transit/write writer o))
    (catch js/Error e (log-error (str "Error serializing " o) e) (throw e))))

(defn deserialize [^String s]
  (try
    (let [reader (transit/reader :json {:handlers {"flow_storm.value_types.LocalImmValue" (fn [tv] (value-types/map->LocalImmValue tv))
                                                   "flow_storm.value_types.RemoteImmValue" (fn [tv] (value-types/map->RemoteImmValue tv))
                                                   "object"        (fn [s] s)
                                                   "regex"         (fn [s] (re-pattern s))}})]
      (transit/read reader s))
    (catch js/Error e (log-error (str "Error deserializing " s " ERROR: " (.-message e))) (throw e))))
