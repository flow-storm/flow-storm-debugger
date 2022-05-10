(ns flow-storm.instrument.trace-types
  (:require [flow-storm.trace-types])
  (:import [flow_storm.trace_types FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace]))

(def *values-references (atom {}))

(defn get-reference-value [vid]
  (get @*values-references vid))

(defn- reference-value! [v]
  (let [vid (java.util.UUID/randomUUID)#_(hash v)]
    (swap! *values-references assoc vid v)
    vid))

(defprotocol PRefTrace
  (ref-values! [trace]))

(extend-type FlowInitTrace

  PRefTrace
  (ref-values! [trace] trace))

(extend-type FormInitTrace

  PRefTrace
  (ref-values! [trace]
    (update trace :mm-dispatch-val reference-value!)))

(extend-type ExecTrace

  PRefTrace
  (ref-values! [trace]
    (update trace :result reference-value!)))

(extend-type FnCallTrace

  PRefTrace
  (ref-values! [trace]
    (update trace :args-vec reference-value!)))

(extend-type BindTrace

  PRefTrace
  (ref-values! [trace]
    (update trace :value reference-value!)))
