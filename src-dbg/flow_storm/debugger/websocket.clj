(ns flow-storm.debugger.websocket
  (:require [flow-storm.utils :refer [log log-error]]
            [flow-storm.json-serializer :as serializer]
            [mount.core :as mount :refer [defstate]]
            [flow-storm.debugger.config :refer [config]]
            [flow-storm.debugger.events-queue])
  (:import [org.java_websocket.server WebSocketServer]
           [org.java_websocket.handshake ClientHandshake]
           [org.java_websocket WebSocket]
           [org.java_websocket.exceptions WebsocketNotConnectedException]
           [java.net InetSocketAddress]
           [java.util UUID]))

(declare start-websocket-server)
(declare stop-websocket-server)
(declare websocket-server)

(defstate websocket-server
  :start (start-websocket-server config)
  :stop  (stop-websocket-server))

(defn async-remote-api-request [method args callback]
  (let [conn @(:remote-connection websocket-server)]

    (if (nil? conn)
     (log-error "No process connected.")
     (try
       (let [request-id (str (UUID/randomUUID))
             packet-str (serializer/serialize [:api-request request-id method args])]
         (.send conn packet-str)
         (swap! (:pending-commands-callbacks websocket-server) assoc request-id callback))
       (catch WebsocketNotConnectedException _)
       (catch Exception e
         (log-error "Error sending async command, maybe the connection is down, try to reconnect" e))))))

(defn sync-remote-api-request
  ([method args] (sync-remote-api-request method args 10000))
  ([method args timeout]
   (let [p (promise)]

     (async-remote-api-request method args (fn [resp] (deliver p resp)))

     (let [v (deref p timeout :flow-storm/timeout)]
       (if (= v :flow-storm/timeout)
         (do
           (log-error "Timeout waiting for sync-remote-api-request response")
           nil)
         v)))))

(defn process-remote-api-response [[request-id err-msg resp :as packet]]
  (let [callback (get @(:pending-commands-callbacks websocket-server) request-id)]
    (if err-msg

      (do
       (log-error (format "Error on process-remote-api-response : %s" packet))
       ;; TODO: we should report errors to callers
       (callback nil))

     (callback resp))))


(defn- create-ws-server [{:keys [port on-message on-connection-open]}]
  (let [ws-ready-promise (promise)
        server (proxy
                   [WebSocketServer]
                   [(InetSocketAddress. port)]

                 (onStart []
                   (log (format "WebSocket server started, listening on %s" port))
                   (deliver ws-ready-promise true))

                 (onOpen [^WebSocket conn ^ClientHandshake handshake-data]
                   (when on-connection-open
                     (on-connection-open conn))
                   (log "Got a connection"))

                 (onMessage [conn message]
                   (on-message conn message))

                 (onClose [conn code reason remote?]
                   (log (format "Connection with debugger closed. code=%s reson=%s remote?=%s"
                                code reason remote?)))

                 (onError [conn ^Exception e]
                   (log-error "WebSocket error" e)))]

    [server ws-ready-promise]))

(defn stop-websocket-server []
  (log "[Stopping WebSocket subsystem]")
  (when-let [wss (:ws-server websocket-server)]
    (.stop wss)
    (log "WebSocket server stopped"))
  (when-let [events-thread (:events-thread websocket-server)]
    (.interrupt events-thread))
  nil)

(defn start-websocket-server [{:keys [dispatch-event on-connection-open]}]
  (log "[Starting WebSocket subsystem]")
  (let [remote-connection (atom nil)
        [ws-server ready] (create-ws-server
                           {:port 7722
                            :on-connection-open (fn [conn]
                                                  (reset! remote-connection conn)
                                                  (when on-connection-open
                                                    (on-connection-open conn)))
                            :on-message (fn [_ msg]
                                          (try
                                            (let [[msg-kind msg-body] (serializer/deserialize msg)]
                                              (case msg-kind
                                                :event (dispatch-event msg-body)
                                                :api-response (process-remote-api-response msg-body)))
                                            (catch Exception e
                                              (log-error (format "Error processing remote message '%s', error msg %s" msg (.getMessage e))))))})]

    ;; see https://github.com/TooTallNate/Java-WebSocket/wiki/Enable-SO_REUSEADDR
    ;; if we don't have this we get Address already in use when starting twice in a row
    (.setReuseAddr ws-server true)
    (.start ws-server)

    ;; wait for the websocket to be ready before finishing this subsystem start
    ;; just to avoid weird race conditions
    @ready

    {:ws-server ws-server
     :pending-commands-callbacks (atom {})
     :remote-connection remote-connection}))
