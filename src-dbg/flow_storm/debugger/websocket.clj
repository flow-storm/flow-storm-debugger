(ns flow-storm.debugger.websocket
  (:require [flow-storm.utils :refer [log log-error]]
            [flow-storm.json-serializer :as serializer]
            [mount.core :as mount :refer [defstate]])
  (:import [org.java_websocket.server WebSocketServer]
           [org.java_websocket.handshake ClientHandshake]
           [org.java_websocket WebSocket]
           [java.net InetSocketAddress]
           [java.util UUID]))

(declare start-websocket-server)
(declare stop-websocket-server)
(declare websocket-server)

(defstate websocket-server
  :start (start-websocket-server (mount/args))
  :stop  (stop-websocket-server))

;;;;;;;;;;;;;;;;;;;;;;;
;; WebSocket Packets ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                  ;;
;; INST -> DBG                                      ;;
;;                                                  ;;
;; [:trace (FlowInitTrace | FormInitTrace | ...)]   ;;
;; [:cmd-ret [comm-id val]]                         ;;
;;                                                  ;;
;;--------------------------------------------------;;
;;                                                  ;;
;; DBG -> INST                                      ;;
;;                                                  ;;
;; [comm-id :rpc-method {args-map}]                 ;;
;;                                                  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn async-command-request [method args callback]
  (let [conn @(:remote-connection websocket-server)]
    (if (nil? conn)
     (log-error "No process connected.")
     (try
       (let [comm-id (UUID/randomUUID)
             packet-str (serializer/serialize [comm-id method args])]
         (.send conn packet-str)
         (swap! (:pending-commands-callbacks websocket-server) assoc comm-id callback))
       (catch Exception e
         (log-error "Error sending async command" e))))))

(defn process-command-response [[comm-id resp]]
  (let [callback (get @(:pending-commands-callbacks websocket-server) comm-id)]
    (callback resp)))

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

    (onMessage [conn ^String message]
      (on-message conn message))

    (onClose [conn code reason remote?]
      (log (format "Connection with debugger closed. code=%s reson=%s remote?=%s"
                   code reason remote?)))

    (onError [conn ^Exception e]
      (log-error "WebSocket error" e))))

(defn stop-websocket-server []
  (when-let [wss (:ws-server websocket-server)]
    (.stop wss)))

(defn start-websocket-server [{:keys [event-dispatcher trace-dispatcher show-error on-connection-open]}]
  (let [remote-connection (atom nil)
        ws-server (create-ws-server
                   {:port 7722
                    :on-connection-open (fn [conn]
                                          (reset! remote-connection conn)
                                          (on-connection-open conn))
                    :on-message (fn [conn msg]
                                  (try
                                    (let [[msg-kind msg-body] (serializer/deserialize msg)]
                                      (case msg-kind
                                        :event (event-dispatcher msg-body)
                                        :trace (trace-dispatcher conn msg-body)
                                        :cmd-ret (process-command-response msg-body)
                                        :cmd-err (show-error msg-body)))
                                    (catch Exception e
                                      (log-error (format "Error processing a remote trace for message '%s', error msg %s" msg (.getMessage e))))))})]

    (.start ws-server)
    {:ws-server ws-server
     :pending-commands-callbacks (atom {})
     :remote-connection remote-connection}))
