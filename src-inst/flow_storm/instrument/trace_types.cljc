(ns flow-storm.instrument.trace-types
  (:require [flow-storm.utils :as utils]
   #?(:clj [flow-storm.trace-types]
      :cljs [flow-storm.trace-types :refer [FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace]] ))
  #?(:clj (:import [flow_storm.trace_types FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace])))

(def *values-references (atom {}))

(defn get-reference-value [vid]
  (get @*values-references vid))

(defn- reference-value! [v]
  (let [vid (utils/rnd-uuid) #_(hash v)]
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
