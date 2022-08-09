(ns flow-storm.api
  (:require [flow-storm.json-serializer :as serializer]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.tracer :as tracer]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.core :as fs-core]
            [flow-storm.utils :refer [log-error] :as utils]
            [flow-storm.events :as events])
  (:require-macros [flow-storm.api]))

(defn remote-connect

  "Connect to a remote debugger.
  Without arguments connects to localhost:7722.

  `config` is a map with `:host`, `:port`

   Use `flow-storm.api/stop` to shutdown the system nicely."

  ([] (remote-connect {:host "localhost" :port 7722}))

  ([config]

   ;; connect to the remote websocket
   (remote-websocket-client/start-remote-websocket-client
    (assoc config :run-command fs-core/run-command))

   (events/setup-tap! true)

   ;; start the tracer
   (tracer/start-tracer
    (assoc config
           :send-fn (fn remote-send [trace]
                      (try
                        (let [packet [:trace (rt-values/wrap-trace-values! trace true)]
                              ser (serializer/serialize packet)]
                          (remote-websocket-client/send ser))
                        (catch js/Error e (log-error "Exception dispatching trace " e))))))))

(defn stop []
  (events/remove-tap!)
  (tracer/stop-tracer)
  (remote-websocket-client/stop-remote-websocket-client))
