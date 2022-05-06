(ns flow-storm.trace-types)

(defrecord LocalImmValue [val]

  clojure.lang.IDeref
  (deref [_] val))

(defrecord RemoteImmValue [conn vid]

  clojure.lang.IDeref
  (deref [_]
    ;; TODO: take the value from conn
    ))

(defprotocol LocalValueWrapper
  (wrap-local-values [trace]))

(defprotocol RemoteValueWrapper
  (wrap-remote-values [trace conn]))

(defrecord FlowInitTrace [flow-id form-ns form timestamp]

  LocalValueWrapper
  (wrap-local-values [trace] trace))

(defrecord FormInitTrace [flow-id form-id thread-id form ns def-kind mm-dispatch-val timestamp]

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :mm-dispatch-val ->LocalImmValue)))

(defrecord ExecTrace [flow-id form-id coor thread-id result outer-form?]

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :result ->LocalImmValue)))

(defrecord FnCallTrace [flow-id form-id fn-name fn-ns thread-id args-vec timestamp]

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :args-vec ->LocalImmValue)))

(defrecord BindTrace [flow-id form-id coor thread-id timestamp symbol value]

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :value ->LocalImmValue)))

(defn fn-call-trace? [trace]
  (instance? FnCallTrace trace))

(defn exec-trace? [trace]
  (instance? ExecTrace trace))
