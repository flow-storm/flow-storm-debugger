(ns flow-storm.runtime.types.fn-call-trace
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils])
  #?(:clj (:require [flow-storm.utils :as utils])))

(def nil-idx -1)

(defprotocol FnCallTraceP
  (get-fn-name [_])
  (get-fn-ns [_])
  (get-form-id [_])
  (get-fn-args [_])
  (get-parent-idx [_])
  (set-parent-idx [_ idx])
  (get-ret-idx [_])
  (set-ret-idx [_ idx])    
  (add-binding [_ bind])
  (set-idx [_ idx])
  (bindings [_]))

(deftype FnCallTrace
    [                         fnName
                              fnNs
     ^int                     formId     
                              fnArgs
                              frameBindings
     ^:unsynchronized-mutable ^int thisIdx
     ^:unsynchronized-mutable ^int parentIdx
     ^:unsynchronized-mutable ^int retIdx]

  FnCallTraceP

  (get-fn-name [_] fnName)
  (get-fn-ns [_] fnNs)
  (get-form-id [_] formId)
  (get-fn-args [_] fnArgs)
  (get-ret-idx [_]
    (when (not= retIdx nil-idx)
      retIdx))
  (set-ret-idx [_ idx]
    (set! retIdx (int idx)))
  (get-parent-idx [_]
    (when (not= parentIdx nil-idx)
      parentIdx))
  (set-parent-idx [_ idx]
    (set! parentIdx (int idx)))
  (set-idx [_ idx]
    (set! thisIdx (int idx)))
  (add-binding [_ bind]
    (index-utils/ml-add frameBindings bind))
  (bindings [_]
    (into [] frameBindings))

  index-protos/TimelineEntryP

  (entry-type [_] :fn-call)
  (entry-idx [_]
    (when (not= thisIdx nil-idx)
      thisIdx))
  (fn-call-idx [this]
    (index-protos/entry-idx this))
  
  index-protos/ImmutableP
  
  (as-immutable [this]
    {:type :fn-call
     :fn-name fnName
     :fn-ns fnNs
     :form-id formId
     :fn-args fnArgs
     :fn-call-idx (index-protos/entry-idx this)
     :idx (index-protos/entry-idx this)
     :parent-indx (get-parent-idx this)
     :ret-idx (get-ret-idx this)})

  #?@(:clj
      [Object
       (toString [_] (utils/format "[%d FnCallTrace] %s/%s form-id: %d ret: %d" thisIdx fnNs fnName formId retIdx))]))

(defn make-fn-call-trace [fn-ns fn-name form-id fn-args]
  (->FnCallTrace fn-name
                 fn-ns
                 form-id
                 fn-args
                 (index-utils/make-mutable-list)
                 nil-idx
                 nil-idx
                 nil-idx))

(defn fn-call-trace? [x]
  (and x (instance? FnCallTrace x)))
