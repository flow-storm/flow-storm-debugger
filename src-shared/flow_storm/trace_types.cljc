(ns flow-storm.trace-types)

;; Just for clj-kondo
(declare map->FlowInitTrace)
(declare map->FormInitTrace)
(declare map->ExecTrace)
(declare map->FnCallTrace)
(declare map->BindTrace)

(defrecord FlowInitTrace [flow-id form-ns form timestamp])

(defrecord FormInitTrace [flow-id thread-id form-id form ns def-kind mm-dispatch-val timestamp])

(defrecord ExecTrace [flow-id thread-id form-id coor result outer-form?])

(defrecord FnCallTrace [flow-id thread-id form-id fn-name fn-ns args-vec timestamp])

(defrecord BindTrace [flow-id thread-id coor timestamp symbol value])

(defn fn-call-trace? [trace]
  (instance? FnCallTrace trace))

(defn exec-trace? [trace]
  (instance? ExecTrace trace))
