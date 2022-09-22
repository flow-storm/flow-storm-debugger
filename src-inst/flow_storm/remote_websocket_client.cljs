(ns flow-storm.remote-websocket-client
  (:require [flow-storm.utils :refer [log log-error] :as utils]
            [flow-storm.json-serializer :as serializer]
            [flow-storm.runtime.events :as rt-events]))

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


(defn send [ser-packet]
  (.send remote-websocket-client ser-packet))

(defn send-event-to-debugger [ev-packet]
  (let [ser-packet (serializer/serialize [:event ev-packet])]
    (send ser-packet)))

(defn start-remote-websocket-client [{:keys [host port on-connected api-call-fn]
                                      :or {host "localhost"
                                           port 7722}}]
  (let [uri-str (utils/format "ws://%s:%s/ws" host port)
        ws-client (web-socket-client-object uri-str)]

    (set! (.-onerror ws-client) (fn []
                                  (log-error (utils/format "WebSocket error connection %s" uri-str))))
    (set! (.-onopen ws-client) (fn []
                                 (log (utils/format "Connection opened to %s" uri-str))

                                 ;; send all pending events
                                 (let [pending-events (rt-events/pop-pending-events!)]
                                   (when (seq pending-events)
                                     (log "Replaying stored events")
                                     ;; HACKY: wait a little befor sending the events to be sure
                                     ;; the debugger that just connected is ready to start
                                     ;; processing events
                                     (js/setTimeout (fn []
                                                      (doseq [ev pending-events]
                                                        (send-event-to-debugger ev)))
                                                    2000)))

                                 (when on-connected (on-connected))))
    (set! (.-onclose ws-client) (fn [] (log (utils/format "Connection with %s closed." uri-str))))

    (set! (.-onmessage ws-client)
          (fn [msg]

            (try

              (if (= (.-type msg) "message")
                (let [message (.-data msg)
                      [packet-key :as in-packet] (serializer/deserialize message)
                      ret-packet (case packet-key
                                   :api-request (let [[_ request-id method args] in-packet]
                                                  (try
                                                    (let [ret-data (api-call-fn method args)]
                                                      [:api-response [request-id nil ret-data]])
                                                    (catch js/Error err
                                                      (log-error (str "Error on api-call-fn " [method args]))
                                                      [:api-response [request-id (.-message err) nil]])))
                                   (log-error "Unrecognized packet key"))
                      ret-packet-ser (serializer/serialize ret-packet)]
                  (.send ws-client ret-packet-ser))

                (js/console.error (str "Message type not handled" msg)))

              (catch js/Error err (log-error "Error processing message : " (.-message err))))))

    (set! remote-websocket-client ws-client)

    ws-client))
