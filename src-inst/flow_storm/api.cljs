(ns flow-storm.api
  (:require [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.outputs :as rt-outputs]
            [flow-storm.runtime.debuggers-api :as dbg-api]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.utils :refer [log]]
            [hansel.instrument.runtime])
  (:require-macros [flow-storm.api]))

(defn stop

  "Stop the flow-storm runtime part gracefully"

  []
  (rt-outputs/remove-tap!)
  (rt-events/clear-dispatch-fn!)
  (rt-events/clear-pending-events!)
  (rt-values/clear-vals-ref-registry)
  (indexes-api/stop)
  (remote-websocket-client/stop-remote-websocket-client)
  (log "System stopped"))

(defn current-stack-trace

  "Utility that returns the current stack-trace"

  []
  (rest (.split (.-stack (js/Error.)) "\n")))

(defn set-thread-trace-limit

  "Set a trace limit to all threads. When the limit is positive, if any thread timeline goes
  beyond the limit the thread code will throw an exception."

  [limit]
  (dbg-api/set-thread-trace-limit limit))

(defn data-window-push-val
  ([dw-id val] (data-window-push-val dw-id val nil))
  ([dw-id val stack-key] (data-window-push-val dw-id val stack-key nil))
  ([dw-id val stack-key extra]
   (let [vdata (rt-values/extract-data-aspects val extra)
         extra (assoc extra
                      :dw-id dw-id
                      :stack-key stack-key
                      :root? true)]
     (rt-events/publish-event! (rt-events/make-data-window-push-val-data-event dw-id vdata extra)))))

(defn data-window-val-update [dw-id new-val]
  (rt-events/publish-event! (rt-events/make-data-window-update-event dw-id {:new-val new-val})))
