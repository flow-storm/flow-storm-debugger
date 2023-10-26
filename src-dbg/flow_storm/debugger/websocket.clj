(ns flow-storm.debugger.websocket

  "Component that manages the websocket server started by the debugger.

  It will :

  - automatically dispatch all events received from the runtime through its configured `on-ws-event`
  - provide `sync-remote-api-request` and `async-remote-api-request` to call the runtime."

  (:require [flow-storm.utils :refer [log log-error]]
            [flow-storm.json-serializer :as serializer]
            [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.events-queue])
  (:import [org.java_websocket.server WebSocketServer]
           [org.java_websocket.handshake ClientHandshake]
           [org.java_websocket WebSocket]
           [org.java_websocket.exceptions WebsocketNotConnectedException]
           [org.java_websocket.framing CloseFrame]
           [java.net InetSocketAddress]
           [java.util UUID]))

(declare start-websocket-server)
(declare stop-websocket-server)
(declare websocket-server)

(defstate websocket-server
  :start (fn [config] (start-websocket-server config))
  :stop  (fn [] (stop-websocket-server)))

(defn async-remote-api-request

  "Call a runtime `method` asynchronously through the websocket with the
  provided `args`.

  `callback` will be called on response with the result object.

  `method` is a keyword with the method id as defined in `flow-storm.runtime.debuggers-api/api-fn`
  "

  [method args callback]

  (let [conn @(:remote-connection websocket-server)
        {:keys [ws-ready?]} (dbg-state/connection-status)]

    (if (or (not ws-ready?)
            (nil? conn))
      (do
        (log-error "Skipping api-request because ws connection isn't ready.")
        (callback nil))
      (try
        (let [request-id (str (UUID/randomUUID))
              packet-str (serializer/serialize [:api-request request-id method args])]
          (.send ^WebSocket conn ^String packet-str)
          (swap! (:pending-commands-callbacks websocket-server) assoc request-id callback))
        (catch WebsocketNotConnectedException wnce
          (log-error "Can't execute async api request because websocket isn't connected" wnce))
        (catch Exception e
          (log-error "Error sending async command" e)
          nil)))))

(defn sync-remote-api-request

  "Call a runtime `method` through the websocket with the
  provided `args`. Will block until we have the response.

  `method` is a keyword with the method id as defined in `flow-storm.runtime.debuggers-api/api-fn`

  `timeout` can be provided so we don't block indefinetly.
  "

  ([method args] (sync-remote-api-request method args 10000))
  ([method args timeout]
   (let [p (promise)]

     (async-remote-api-request method args (fn [resp] (deliver p resp)))

     (let [v (deref p timeout :flow-storm/timeout)]
       (if (= v :flow-storm/timeout)
         (do
           (log-error (str "Timeout waiting for sync-remote-api-request response to " method " with " args))
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

(defn- create-ws-server [{:keys [port on-message on-open on-close on-start]}]
  (let [server (proxy
                   [WebSocketServer]
                   [(InetSocketAddress. port)]

                 (onStart []
                   (log (format "WebSocket server started, listening on %s" port))
                   (on-start))

                 (onOpen [^WebSocket conn ^ClientHandshake handshake-data]
                   (when on-open
                     (on-open conn))
                   (log (format "Got a connection %s" conn)))

                 (onMessage [conn message]
                   (on-message conn message))

                 (onClose [conn code reason remote?]
                   (log (format "Connection with debugger closed. conn=%s code=%s reson=%s remote?=%s"
                                conn code reason remote?))
                   (on-close code reason remote?))

                 (onError [conn ^Exception e]
                   (log-error "WebSocket error" e)))]

    server))

(defn stop-websocket-server []
  (when-let [wss (:ws-server websocket-server)]
    (.stop wss))
  (when-let [conn @(:remote-connection websocket-server)]
    (.close conn))
  nil)

(defn start-websocket-server [{:keys [on-ws-event on-ws-up on-ws-down]}]
  (let [remote-connection (atom nil)
        ws-ready (promise)
        ws-server (create-ws-server
                   {:port 7722
                    :on-start (fn [] (deliver ws-ready true))
                    :on-open (fn [conn]
                               (reset! remote-connection conn)
                               (when on-ws-up
                                 (on-ws-up conn)))
                    :on-message (fn [_ msg]
                                  (try
                                    (let [[msg-kind msg-body] (serializer/deserialize msg)]
                                      (case msg-kind
                                        :event (on-ws-event msg-body)
                                        :api-response (process-remote-api-response msg-body)))
                                    (catch Exception e
                                      (log-error (format "Error processing remote message '%s', error msg %s" msg (.getMessage e))))))

                    :on-close (fn [code _ _]
                                (log-error (format "Connection closed with code %s" code))
                                (cond

                                  (or (= code CloseFrame/GOING_AWAY)
                                      (= code CloseFrame/ABNORMAL_CLOSE))
                                  (when on-ws-down
                                    (on-ws-down))

                                  :else nil)

                                )})]

    ;; see https://github.com/TooTallNate/Java-WebSocket/wiki/Enable-SO_REUSEADDR
    ;; if we don't have this we get Address already in use when starting twice in a row
    (.setReuseAddr ws-server true)
    (.start ws-server)

    ;; wait for the websocket to be ready before finishing this subsystem start
    ;; just to avoid weird race conditions
    @ws-ready

    {:ws-server ws-server
     :pending-commands-callbacks (atom {})
     :remote-connection remote-connection}))
