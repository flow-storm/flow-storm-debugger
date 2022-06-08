(ns flow-storm.debugger.trace-types
  (:require [flow-storm.debugger.websocket :as websocket]
            [clojure.pprint :as pp])
  (:import [flow_storm.trace_types FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace]))

(defrecord LocalImmValue [val])

(defprotocol LocalValueWrapper
  (wrap-local-values [trace]))

(defprotocol RemoteValueWrapper
  (wrap-remote-values [trace conn]))

(defrecord RemoteImmValue [vid])

(defn- wrap-remote-value [v conn]
  (with-meta (->RemoteImmValue v) {:ws-conn conn}))

(defprotocol PDeref
  (deref-ser [v opts]))

(extend-type LocalImmValue

  PDeref
  (deref-ser [this {:keys [print-length print-level pprint? nth-elem]}]
    (let [print-fn (if pprint? pp/pprint print)]
      (with-out-str
        (binding [*print-level* print-level
                  *print-length* print-length]
          (print-fn (cond-> (:val this)
                      nth-elem (nth nth-elem))))))))

(def get-remote-value-sync
  (memoize
   (fn [conn vid {:keys [print-length print-level pprint? nth-elem]}]
     (let [p (promise)]
       (websocket/async-command-request conn
                                        :get-remote-value
                                        {:vid vid
                                         :print-length print-length
                                         :print-level print-level
                                         :pprint? pprint?
                                         :nth-elem nth-elem}
                                        (fn [val]
                                          (deliver p val)))
       ;; This is to avoid blocking the UI forever if something on the
       ;; target fails and we never get the value
       (deref p 5000 "TIMEOUT")))))

(extend-type RemoteImmValue

  PDeref
  (deref-ser [this opts]
    (let [conn (-> this meta :ws-conn)
          vid (:vid this)]
      (get-remote-value-sync conn vid opts))))

(extend-type FlowInitTrace

  LocalValueWrapper
  (wrap-local-values [trace] trace)

  RemoteValueWrapper
  (wrap-remote-values [trace _]
    trace))

(extend-type FormInitTrace

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :mm-dispatch-val ->LocalImmValue))

  RemoteValueWrapper
  (wrap-remote-values [trace conn]
    (update trace :mm-dispatch-val wrap-remote-value conn)))

(extend-type ExecTrace

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :result ->LocalImmValue))

  RemoteValueWrapper
  (wrap-remote-values [trace conn]
    (update trace :result wrap-remote-value conn)))

(extend-type FnCallTrace

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :args-vec ->LocalImmValue))

  RemoteValueWrapper
  (wrap-remote-values [trace conn]
    (update trace :args-vec wrap-remote-value conn)))

(extend-type BindTrace

  LocalValueWrapper
  (wrap-local-values [trace]
    (update trace :value ->LocalImmValue))

  RemoteValueWrapper
  (wrap-remote-values [trace conn]
    (update trace :value wrap-remote-value conn)))
