(ns flow-storm.api
  (:require [flow-storm.json-serializer :as serializer]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.taps :as rt-taps]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.debuggers-api :as dbg-api])
  (:require-macros [flow-storm.api]))

(defn remote-connect [config]

  ;; connect to the remote websocket
  (remote-websocket-client/start-remote-websocket-client
   (assoc config :api-call-fn dbg-api/call-by-name))

  ;; push all events thru the websocket
  (rt-events/subscribe! (fn [ev]
                          (-> [:event ev]
                              serializer/serialize
                              remote-websocket-client/send)))

  (rt-taps/setup-tap!)
  (println "ClojureScript runtime initialized"))

(defn stop []
  (rt-taps/remove-tap!)
  (rt-events/clear-subscription!)
  (indexes-api/stop)
  (remote-websocket-client/stop-remote-websocket-client))
