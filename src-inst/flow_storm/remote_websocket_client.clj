(ns flow-storm.remote-websocket-client
  (:refer-clojure :exclude [send])
  (:require [flow-storm.json-serializer :as serializer]
            [flow-storm.utils :refer [log log-error] :as utils])
  (:import [org.java_websocket.client WebSocketClient]
           [java.net URI]
           [org.java_websocket.handshake ServerHandshake]))

(def remote-websocket-client nil)

(defn stop-remote-websocket-client []
  (when remote-websocket-client
    (.close remote-websocket-client))
  (alter-var-root #'remote-websocket-client (constantly nil)))

(defn remote-connected? []
  (boolean remote-websocket-client))

(defn send [ser-packet]
  ;; websocket library isn't clear about thread safty of send
  ;; lets synchronize just in case
  (locking remote-websocket-client
    (.send ^WebSocketClient remote-websocket-client ^String ser-packet)))

(defn send-event-to-debugger [ev-packet]
  (let [ser-packet (serializer/serialize [:event ev-packet])]
    (send ser-packet)))

(defn start-remote-websocket-client [{:keys [debugger-host debugger-ws-port on-connected api-call-fn]}]
  (let [debugger-host (or debugger-host "localhost")
        debugger-ws-port (or debugger-ws-port 7722)
        uri-str (format "ws://%s:%s/ws" debugger-host debugger-ws-port)
        _ (println "About to connect to " uri-str)
        ^WebSocketClient ws-client (proxy
                                       [WebSocketClient]
                                       [(URI. uri-str)]

                                     (onOpen [^ServerHandshake handshake-data]
                                       (log (format "Connection opened to %s" uri-str))

                                       (when on-connected (on-connected)))

                                     (onMessage [^String message]
                                       (let [[packet-key :as in-packet] (serializer/deserialize message)
                                             ret-packet (case packet-key
                                                          :api-request (let [[_ request-id method args] in-packet]
                                                                         (try
                                                                           (let [ret-data (api-call-fn method args)]
                                                                             [:api-response [request-id nil ret-data]])
                                                                           (catch Exception e
                                                                             [:api-response [request-id (.getMessage e) nil]])))
                                                          (log-error "Unrecognized packet key"))
                                             ret-packet-ser (serializer/serialize ret-packet)]

                                         (.send remote-websocket-client ret-packet-ser)))

                                     (onClose [code reason remote?]
                                       (log (format "Connection with %s closed. code=%s reson=%s remote?=%s"
                                                    uri-str code reason remote?))
                                       ((requiring-resolve 'flow-storm.runtime.debuggers-api/stop-runtime)))

                                     (onError [^Exception e]
                                       (log-error (format "WebSocket error connection %s" uri-str) e)))]
    (.setConnectionLostTimeout ws-client 0)
    (.connect ws-client)

    (alter-var-root #'remote-websocket-client (constantly ws-client))
    ws-client))
