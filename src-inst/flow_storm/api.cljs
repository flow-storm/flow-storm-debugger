(ns flow-storm.api
  (:require [flow-storm.json-serializer :as serializer]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.taps :as rt-taps]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.debuggers-api :as dbg-api]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.utils :refer [log] :as utils]
            [flow-storm.tracer]
            [hansel.instrument.runtime])
  (:require-macros [flow-storm.api]))

(def api-loaded?
  "Used for remote connections to check this ns has been loaded"
  true)

(defn remote-connect [config]

  (println "About to remote-connect ClojureScript with " config)

  ;; NOTE: The order here is important until we replace this code with
  ;; better component state management

  (indexes-api/start)

  ;; connect to the remote websocket
  (remote-websocket-client/start-remote-websocket-client
   (assoc config
          :api-call-fn dbg-api/call-by-name
          :on-connected (fn []
                          ;; push all events thru the websocket
                          (rt-events/subscribe! (fn [ev]
                                                  (-> [:event ev]
                                                      serializer/serialize
                                                      remote-websocket-client/send)))

                          (rt-values/clear-vals-ref-registry)

                          (rt-taps/setup-tap!)
                          (println "Remote ClojureScript runtime initialized")))))

(defn stop []
  (rt-taps/remove-tap!)
  (rt-events/clear-subscription!)
  (rt-events/clear-pending-events!)
  (rt-values/clear-vals-ref-registry)
  (indexes-api/stop)
  (remote-websocket-client/stop-remote-websocket-client)
  (log "System stopped"))

(defn current-stack-trace []
  (rest (.split (.-stack (js/Error.)) "\n")))
