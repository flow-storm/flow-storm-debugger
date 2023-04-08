(ns flow-storm.runtime.types.fn-call-trace
  (:require [flow-storm.utils :as utils]))

(defprotocol FnCallTraceP
  (get-fn-name [_])
  (get-fn-ns [_])
  (get-form-id [_])
  (get-timestamp [_])
  (get-fn-args [_])
  (get-frame-node [_])
  (set-frame-node [_ node]))

(deftype FnCallTrace
    [                         fnName
                              fnNs
     ^int                     formId
     ^long                    timestamp
                              fnArgs
     ^:unsynchronized-mutable frameNode]

  FnCallTraceP

  (get-fn-name [_] fnName)
  (get-fn-ns [_] fnNs)
  (get-form-id [_] formId)
  (get-timestamp [_] timestamp)
  (get-fn-args [_] fnArgs)
  (get-frame-node [_] frameNode)
  (set-frame-node [_ node]
    (set! frameNode node))

  Object
  (toString [_] (utils/format "[FnCallTrace] %s/%s form-id: %d" fnNs fnName formId)))

(defn make-fn-call-trace [fn-ns fn-name form-id timestamp fn-args]
  (->FnCallTrace fn-name fn-ns form-id timestamp fn-args nil ))

(defn fn-call-trace? [x]
  (and x (instance? FnCallTrace x)))
