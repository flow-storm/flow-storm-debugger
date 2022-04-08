(ns flow-storm.trace-types)

(defrecord FlowInitTrace [flow-id form-ns form timestamp])
(defrecord FormInitTrace [flow-id form-id thread-id form ns def-kind mm-dispatch-val timestamp])
(defrecord ExecTrace [flow-id form-id coor thread-id result outer-form?])
(defrecord FnCallTrace [flow-id form-id fn-name fn-ns thread-id args-vec timestamp])
(defrecord BindTrace [flow-id form-id coor thread-id timestamp symbol value])

(defn fn-call-trace? [trace]
  (instance? FnCallTrace trace))

(defn exec-trace? [trace]
  (instance? ExecTrace trace))
