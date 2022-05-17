(ns flow-storm.json-serializer
  (:require [cognitect.transit :as transit]
            [flow-storm.utils :refer [log-error]]
            [flow-storm.trace-types])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.regex Pattern]
           [flow_storm.trace_types FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace]))

(def ^ByteArrayOutputStream write-buffer (ByteArrayOutputStream. (* 1024 1024))) ;; 1Mb buffer

(defn serialize [obj]
  (locking write-buffer
    (try
      (let [writer (transit/writer write-buffer :json {:handlers (merge (transit/record-write-handlers FlowInitTrace
                                                                                                       FormInitTrace
                                                                                                       ExecTrace
                                                                                                       FnCallTrace
                                                                                                       BindTrace)
                                                                        {Pattern (transit/write-handler
                                                                                  (fn [_] "regex")
                                                                                  str)})
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
          reader (transit/reader in :json {:handlers (merge (transit/record-read-handlers FlowInitTrace
                                                                                          FormInitTrace
                                                                                          ExecTrace
                                                                                          FnCallTrace
                                                                                          BindTrace)
                                                            {"object" (transit/read-handler (fn [s] s))
                                                             "regex"  (transit/read-handler (fn [s] (re-pattern s)))})})]
      (transit/read reader))
    (catch Exception e (log-error (format "Error deserializing %s, ERROR: %s" s (.getMessage e))) (throw e))))
