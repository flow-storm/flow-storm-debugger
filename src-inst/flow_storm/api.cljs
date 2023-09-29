(ns flow-storm.api
  (:require [flow-storm.json-serializer :as serializer]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.taps :as rt-taps]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.debuggers-api :as dbg-api]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.utils :refer [log] :as utils :refer-macros [env-prop]]
            [flow-storm.tracer :as tracer]
            [hansel.instrument.runtime])
  (:require-macros [flow-storm.api]))

(def api-loaded?
  "Used for remote connections to check this ns has been loaded"
  true)

(defn setup-runtime

  "This is meant to be called by preloads to initialize the runtime side of things"

  []
  (println "Setting up runtime")

  (indexes-api/start)

  (println "Index started")

  (let [recording? (if (= (env-prop "flowstorm.startRecording") "false") false true)]
    (tracer/set-recording recording?)
    (println "Recording set to " recording?))

  (let [fn-call-limits (utils/parse-thread-fn-call-limits (env-prop "flowstorm.threadFnCallLimits"))]
    (doseq [[fn-ns fn-name l] fn-call-limits]
      (indexes-api/add-fn-call-limit fn-ns fn-name l)
      (println "Added function limit " fn-ns fn-name l)))

  (rt-values/clear-vals-ref-registry)
  (println "Value references cleared")

  (rt-taps/setup-tap!)
  (println "Runtime setup ready"))

(defn remote-connect [config]

  (println "Connecting with the debugger with config : " config)

  ;; connect to the remote websocket
  (remote-websocket-client/start-remote-websocket-client
   (assoc config
          :api-call-fn dbg-api/call-by-name
          :on-connected (fn []
                          ;; subscribe and automatically push all events thru the websocket
                          ;; if there were any events waiting to be dispatched
                          (rt-events/set-dispatch-fn
                           (fn [ev]
                             (-> [:event ev]
                                 serializer/serialize
                                 remote-websocket-client/send)))

                          (println "Debugger connection ready. Events dispatch function set and pending events pushed.")))))

(defn stop []
  (rt-taps/remove-tap!)
  (rt-events/clear-dispatch-fn!)
  (rt-events/clear-pending-events!)
  (rt-values/clear-vals-ref-registry)
  (indexes-api/stop)
  (remote-websocket-client/stop-remote-websocket-client)
  (log "System stopped"))

(defn current-stack-trace []
  (rest (.split (.-stack (js/Error.)) "\n")))
