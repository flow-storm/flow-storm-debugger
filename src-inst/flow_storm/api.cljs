(ns flow-storm.api
  (:require [flow-storm.instrument.trace-types :as inst-trace-types]
            [flow-storm.utils :refer [log log-error] :as utils]
            [flow-storm.json-serializer :as serializer]
            [flow-storm.tracer :as tracer]
            [flow-storm.core :as fs-core])
  (:require-macros [flow-storm.api]))

(def remote-websocket-client nil)

(defn close-remote-connection []
  (when remote-websocket-client
    (.close remote-websocket-client)))

(defn web-socket-client-object [uri-str]
  (let [WebSocket (if (and (= *target* "nodejs")
                           (exists? js/require))

                    (let [obj (try
                                (js/require "websocket")
                                (catch :default e
                                  (js/console.error "websocket node dependency not installed. Please npm install websocket to use flowstorm with nodejs" e)))]
                      (.-w3cwebsocket ^js obj))

                    js/window.WebSocket)
        ws-client (WebSocket. uri-str)]
    ws-client))

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
         ws-client (web-socket-client-object uri-str)
         remote-dispatch-trace (fn remote-dispatch-trace [trace]
                                 (let [packet [:trace trace]
                                       ser (serializer/serialize packet)]
                                   (.send  ws-client ser)))]

     (set! (.-onerror ws-client) (fn []
                                   (log-error (utils/format "WebSocket error connection %s" uri-str))))
     (set! (.-onopen ws-client) (fn []
                                  (log (utils/format "Connection opened to %s" uri-str))

                                  (tracer/start-trace-sender
                                   (assoc config
                                          :send-fn (fn remote-send [trace]
                                                     (try
                                                       (-> trace
                                                           inst-trace-types/ref-values!
                                                           remote-dispatch-trace)
                                                       (catch js/Error e
                                                         (log-error "Exception dispatching trace " e))))))

                                  (when on-connected (on-connected))))
     (set! (.-onclose ws-client) (fn []
                                   (log (utils/format "Connection with %s closed." uri-str))))
     (set! (.-onmessage ws-client) (fn [e]
                                     (if (= (.-type e) "message")
                                       (let [message (.-data e)
                                             [comm-id method args-map] (serializer/deserialize message)
                                             ret-packet (fs-core/run-command comm-id method args-map)
                                             ret-packet-ser (serializer/serialize ret-packet)]
                                         (.send remote-websocket-client ret-packet-ser))

                                       (js/console.error (str "Message type not handled" e)))))

     (set! remote-websocket-client ws-client))))
