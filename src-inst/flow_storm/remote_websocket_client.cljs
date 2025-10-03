(ns flow-storm.remote-websocket-client
  (:require [flow-storm.utils :refer [log log-error] :as utils]
            [flow-storm.json-serializer :as serializer]))

(def remote-websocket-client nil)

(defn stop-remote-websocket-client []
  (when remote-websocket-client
    (.close remote-websocket-client))
  (set! remote-websocket-client nil))

(defn websocket-state [ws]
  (case (.-readyState ws)
    0 :connecting
    1 :open
    2 :closing
    3 :closed))

(defn remote-connected? []
  (and remote-websocket-client
       (= :open (websocket-state remote-websocket-client))))

(defn web-socket-client-object [uri-str]
  (let [WebSocket (if (and (= *target* "nodejs")
                           (exists? js/require))

                    (let [obj (try
                                (js/require "websocket")
                                (catch :default e
                                  (js/console.error "websocket node dependency not installed. Please npm install websocket to use flowstorm with nodejs" e)))]
                      (.-w3cwebsocket ^js obj))

                    js/globalThis.WebSocket)
        ws-client (WebSocket. uri-str)]
    ws-client))


(defn send [ser-packet]
  (.send remote-websocket-client ser-packet))

(defn send-event-to-debugger [ev-packet]
  (let [ser-packet (serializer/serialize [:event ev-packet])]
    (send ser-packet)))

(defn start-remote-websocket-client [{:keys [debugger-host debugger-ws-port on-connected api-call-fn]}]

  (if (remote-connected?)

    (js/console.warn "Websocket already connected. Skipping.")

    (let [debugger-host (or debugger-host "localhost")
          debugger-ws-port (or debugger-ws-port 7722)
          uri-str (utils/format "ws://%s:%s/ws" debugger-host debugger-ws-port)
          _ (log (str "About to connect to " uri-str))
          ws-client (try
                      (web-socket-client-object uri-str)
                      (catch js/Error e
                        (js/console.error (str "Can't connect to " uri-str) e)))]

      (set! (.-onerror ws-client) (fn []
                                    (log-error (utils/format "WebSocket error connection %s" uri-str))))
      (set! (.-onopen ws-client) (fn []
                                   (log (utils/format "Connection opened to %s" uri-str))

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
                                                        (log-error (str "Error on api-call-fn " [method args]) err)
                                                        [:api-response [request-id (.-message err) nil]])))
                                     (log-error "Unrecognized packet key"))
                        ret-packet-ser (serializer/serialize ret-packet)]
                    (.send ws-client ret-packet-ser))

                  (js/console.error (str "Message type not handled" msg)))

                (catch js/Error err (log-error "Error processing message : " (.-message err))))))

      (set! remote-websocket-client ws-client)

      ws-client)))
