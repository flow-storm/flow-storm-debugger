(ns flow-storm.json-serializer
  (:require [cognitect.transit :as transit]
            [flow-storm.utils :refer [log-error]]
            [flow-storm.types :as types])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.regex Pattern]
           [flow_storm.types ValueRef]))

(def ^ByteArrayOutputStream write-buffer (ByteArrayOutputStream. (* 1024 1024))) ;; 1Mb buffer

(defn serialize [obj]
  (locking write-buffer
    (try
      (let [writer (transit/writer write-buffer :json {:handlers {Pattern (transit/write-handler
                                                                           (fn [_] "regex")
                                                                           str)
                                                                  ValueRef (transit/write-handler
                                                                            (fn [_] "flow_storm.types.ValueRef")
                                                                            (fn [vref] (types/serialize-val-ref vref)))}
                                                       :default-handler (transit/write-handler
                                                                         (fn [_] "object")
                                                                         pr-str)})
            _ (transit/write writer obj)
            ser (.toString write-buffer)]
        (.reset write-buffer)
        ser)
      (catch Exception e (log-error (format "Error serializing %s" obj) e) (throw e)))))

(defn deserialize [^String s]
  (try
    (let [^ByteArrayInputStream in (ByteArrayInputStream. (.getBytes s))
          reader (transit/reader in :json {:handlers {"object" (transit/read-handler (fn [s] s))
                                                      "regex"  (transit/read-handler (fn [s] (re-pattern s)))
                                                      "flow_storm.types.ValueRef" (transit/read-handler (fn [vref-str]
                                                                                                          (-> vref-str
                                                                                                              read-string
                                                                                                              types/deserialize-val-ref)))}})]
      (transit/read reader))
    (catch Exception e (log-error (format "Error deserializing %s, ERROR: %s" s (.getMessage e))) (throw e))))
