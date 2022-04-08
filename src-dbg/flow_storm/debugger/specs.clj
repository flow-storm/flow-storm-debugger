(ns flow-storm.debugger.specs
  #_(:require [clojure.spec.alpha :as s]
            [flow-storm.tracer])
  #_(:import [flow_storm.tracer FormInitTrace BindTrace FnCallTrace ExecTrace]))

;; (s/def ::timestamp number?)
;; (s/def :flow/id number?)
;; (s/def :form/id number?)
;; (s/def :thread/id number?)
;; (s/def :form/coord (s/coll-of number? :kind vector?))
;; (s/def :fn/name string?)
;; (s/def :fn/ns string?)

;; (s/def ::init-trace    #(instance? FormInitTrace %))
;; (s/def ::bind-trace    #(instance? BindTrace %))
;; (s/def ::fn-call-trace #(instance? FnCallTrace %))
;; (s/def ::exec-trace    #(instance? ExecTrace %))

;; (s/def :form/ns string?)
;; (s/def :form/form any?)
