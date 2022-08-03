(ns flow-storm.debugger.values
  (:require [flow-storm.debugger.websocket :as websocket])
  (:import [flow_storm.value_types LocalImmValue RemoteImmValue]))

(defprotocol PLazyValue
  (val-pprint [v opts])
  (shallow-val [v]))

(extend-type LocalImmValue

  PLazyValue

  (val-pprint [this params]
    (let [v-pprint (resolve 'flow-storm.runtime.values/val-pprint)
          v (:val this)]
      (v-pprint v params)))

  (shallow-val [this]
    (let [shall-val (resolve 'flow-storm.runtime.values/shallow-val)
          v (:val this)]
      (shall-val v false))))

(def pprint-remote-value-sync
  (memoize
   (fn [vid {:keys [print-length print-level print-meta? pprint? nth-elems]}]
     (let [p (promise)]
       (websocket/async-command-request :remote-val-pprint
                                        {:vid vid
                                         :print-meta? print-meta?
                                         :print-length print-length
                                         :print-level print-level
                                         :pprint? pprint?
                                         :nth-elems nth-elems}
                                        (fn [val]
                                          (deliver p val)))
       ;; This is to avoid blocking the UI forever if something on the
       ;; target fails and we never get the value
       (deref p 5000 "TIMEOUT")))))

(extend-type RemoteImmValue

  PLazyValue

  (val-pprint [this opts]
    (let [vid (:vid this)]
      (pprint-remote-value-sync vid opts)))

  (shallow-val [this]
    (let [p (promise)
          vid (:vid this)]
      (websocket/async-command-request :remote-shallow-val
                                       {:vid vid}
                                       (fn [val]
                                         (deliver p val)))
      ;; This is to avoid blocking the UI forever if something on the
      ;; target fails and we never get the value
      (deref p 5000 "TIMEOUT"))))
