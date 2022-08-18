(ns flow-storm.debugger.websocket
  (:require [flow-storm.utils :refer [log log-error]]
            [flow-storm.json-serializer :as serializer]
            [mount.core :as mount :refer [defstate]]
            [clojure.core.async :as async])
  (:import [org.java_websocket.server WebSocketServer]
           [org.java_websocket.handshake ClientHandshake]
           [org.java_websocket WebSocket]
           [java.net InetSocketAddress BindException]
           [java.util UUID]))

(declare start-websocket-server)
(declare stop-websocket-server)
(declare websocket-server)

(defstate websocket-server
  :start (start-websocket-server (mount/args))
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
       (catch Exception e
         (log-error "Error sending async command" e))))))

(defn sync-remote-api-request [method args]
  (let [p (promise)]

    (async-remote-api-request method args (fn [resp] (deliver p resp)))

    (deref p 5000 nil)))

(defn process-remote-api-response [[request-id err-msg resp :as packet]]
  (let [callback (get @(:pending-commands-callbacks websocket-server) request-id)]
    (if err-msg

      (do
       (log-error (format "Error on process-remote-api-response : %s" packet))
       ;; TODO: we should report errors to callers
       (callback nil))

     (callback resp))))


(defn- create-ws-server [{:keys [port on-message on-connection-open]}]
  (proxy
      [WebSocketServer]
      [(InetSocketAddress. port)]

    (onStart []
      (log (format "WebSocket server started, listening on %s" port)))

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
      (log-error "WebSocket error" e))))

(defn stop-websocket-server []
  (when-let [wss (:ws-server websocket-server)]
    (.stop wss)
    (log "WebSocket server stopped"))
  (when-let [events-thread (:events-thread websocket-server)]
    (.interrupt events-thread))
  nil)

(defn build-events-dispatcher-thread [dispatch-event events-chan]
  (Thread.
   (fn []

     (try
       (loop [ev (async/<!! events-chan)]
        (when-not (.isInterrupted (Thread/currentThread))
          (dispatch-event ev)
          (recur (async/<!! events-chan))))
       (catch java.lang.InterruptedException ie
         (log "Events thread interrupted"))))))

(defn start-websocket-server [{:keys [event-dispatcher show-error on-connection-open]}]
  (stop-websocket-server)
  (let [remote-connection (atom nil)
        events-chan (async/chan 100)
        ws-server (create-ws-server
                   {:port 7722
                    :on-connection-open (fn [conn]
                                          (reset! remote-connection conn)
                                          (when on-connection-open
                                            (on-connection-open conn)))
                    :on-message (fn [_ msg]
                                  (try
                                    (let [[msg-kind msg-body] (serializer/deserialize msg)]
                                      (case msg-kind
                                        :event (async/>!! events-chan msg-body)
                                        :api-response (process-remote-api-response msg-body)))
                                    (catch Exception e
                                      (log-error (format "Error processing a remote trace for message '%s', error msg %s" msg (.getMessage e))))))})
        events-thread (build-events-dispatcher-thread event-dispatcher events-chan)]

    (.start events-thread)

    ;; see https://github.com/TooTallNate/Java-WebSocket/wiki/Enable-SO_REUSEADDR
    ;; if we don't have this we get Address already in use when starting twice in a row
    (.setReuseAddr ws-server true)
    (.start ws-server)



    {:ws-server ws-server
     :events-thread events-thread
     :pending-commands-callbacks (atom {})
     :remote-connection remote-connection}))
