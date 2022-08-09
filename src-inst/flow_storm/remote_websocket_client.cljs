(ns flow-storm.remote-websocket-client
  (:require [flow-storm.json-serializer :as serializer]
            [flow-storm.utils :refer [log log-error] :as utils]
            [flow-storm.core :as fs-core]))

(def remote-websocket-client nil)

(defn stop-remote-websocket-client []
  (when remote-websocket-client
    (.close remote-websocket-client))
  (set! remote-websocket-client nil))

(defn remote-connected? []
  (boolean remote-websocket-client))

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

(defn start-remote-websocket-client [{:keys [host port on-connected]
                                      :or {host "localhost"
                                           port 7722}}]
  (let [uri-str (utils/format "ws://%s:%s/ws" host port)
        ws-client (web-socket-client-object uri-str)]

    (set! (.-onerror ws-client) (fn []
                                  (log-error (utils/format "WebSocket error connection %s" uri-str))))
    (set! (.-onopen ws-client) (fn []
                                 (log (utils/format "Connection opened to %s" uri-str))
                                 (when on-connected (on-connected))))
    (set! (.-onclose ws-client) (fn []
                                  (log (utils/format "Connection with %s closed." uri-str))))
    (set! (.-onmessage ws-client) (fn [e]
                                    (if (= (.-type e) "message")
                                      (let [message (.-data e)
                                            [comm-id method args-map] (serializer/deserialize message)
                                            ret-packet (fs-core/run-command comm-id method args-map)
                                            ret-packet-ser (serializer/serialize ret-packet)]
                                        (.send ws-client ret-packet-ser))

                                      (js/console.error (str "Message type not handled" e)))))
    (set! remote-websocket-client ws-client)

    ws-client))

(defn send [ser-packet]
  (.send remote-websocket-client ser-packet))

(defn send-event-to-debugger [ev-packet]
  (let [ser-packet (serializer/serialize ev-packet)]
    (send ser-packet)))
