(ns flow-storm.api
  (:require [websocket :as websocket]
            [flow-storm.instrument.trace-types :as inst-trace-types]
            [flow-storm.utils :refer [log log-error] :as utils]
            [flow-storm.json-serializer :as serializer]
            [flow-storm.tracer :as tracer]
            [flow-storm.core :as fs-core])
  (:require-macros [flow-storm.api]))

(def W3CWebSocket (.-w3cwebsocket websocket))

(def remote-websocket-client nil)

(defn close-remote-connection []
  (when remote-websocket-client
    (.close remote-websocket-client)))

(defn remote-connect
  "Connect to a remote debugger.
  Without arguments connects to localhost:7722.

  `config` is a map with `:host`, `:port`"

  ([] (remote-connect {:host "localhost" :port 7722}))

  ([{:keys [host port on-connected]
     :or {host "localhost"
          port 7722}
     :as config}]

   (close-remote-connection) ;; if there is any active connection try to close it first

   (let [uri-str (utils/format "ws://%s:%s/ws" host port)
         ws-client (W3CWebSocket. uri-str)
         remote-dispatch-trace (fn remote-dispatch-trace [trace]
                                 (let [packet [:trace trace]
                                       ser (serializer/serialize packet)]
                                   (.send  ws-client ser)))]

     (set! (.-onerror ws-client) (fn []
                                   (log-error (utils/format "WebSocket error connection %s" uri-str))))
     (set! (.-onopen ws-client) (fn []
                                  (log (utils/format "Connection opened to %s" uri-str))

                                  (when on-connected (on-connected))

                                  (tracer/start-trace-sender
                                   (assoc config
                                          :send-fn (fn remote-send [trace]
                                                     (try
                                                       (-> trace
                                                           inst-trace-types/ref-values!
                                                           remote-dispatch-trace)
                                                       (catch js/Error e
                                                         (log-error "Exception dispatching trace " e))))))))
     (set! (.-onclose ws-client) (fn []
                                   (log (utils/format "Connection with %s closed." uri-str))))
     (set! (.-onmessage ws-client) (fn [e]
                                     (if (= (.-type e) "message")
                                       (let [message (.-data e)
                                             [comm-id method args-map] (serializer/deserialize message)
                                             resp-val (fs-core/run-command method args-map)
                                             ret-packet [:cmd-ret [comm-id resp-val]]
                                             ret-packet-ser (serializer/serialize ret-packet)]
                                         (.send remote-websocket-client ret-packet-ser))

                                       (js/console.error (str "Message type not handled" e)))))

     (set! remote-websocket-client ws-client))))

(defn read-trace-tag [form]
  `(fs-core/instrument ~form))

(defn read-rtrace-tag [form]
  `(runi ~form))
