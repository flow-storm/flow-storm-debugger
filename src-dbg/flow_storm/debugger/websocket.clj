(ns flow-storm.debugger.websocket
  (:require [flow-storm.utils :refer [log log-error]]
            [flow-storm.json-serializer :as serializer])
  (:import [org.java_websocket.server WebSocketServer]
           [org.java_websocket.handshake ClientHandshake]
           [org.java_websocket WebSocket]
           [java.net InetSocketAddress]
           [java.util UUID]))

(def websocket-server nil)

(def *pending-commands-callbacks (atom {}))

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

(defn async-command-request [conn method args callback]
  (if (nil? conn)
    (log-error "No process connected.")
    (try
      (let [comm-id (UUID/randomUUID)
            packet-str (serializer/serialize [comm-id method args])]
        (.send conn packet-str)
        (swap! *pending-commands-callbacks assoc comm-id callback))
      (catch Exception e
        (log-error "Error sending async command" e)))))

(defn process-command-response [[comm-id resp]]
  (let [callback (get @*pending-commands-callbacks comm-id)]
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

(defn stop-websocket-servers []
  (when websocket-server
    (.stop websocket-server)))

(defn start-websocket-server [{:keys [trace-dispatcher show-error on-connection-open]}]
  (let [ws-server (create-ws-server
                   {:port 7722
                    :on-connection-open on-connection-open
                    :on-message (fn [conn msg]
                                  (try
                                    (let [[msg-kind msg-body] (serializer/deserialize msg)]
                                      (case msg-kind
                                        :trace (trace-dispatcher conn msg-body)
                                        :cmd-ret (process-command-response msg-body)
                                        :cmd-err (show-error msg-body)))
                                    (catch Exception e
                                      (log-error (format "Error processing a remote trace for message '%s', error msg %s" msg (.getMessage e))))))})]
    (alter-var-root #'websocket-server (constantly ws-server))

    (.start ws-server)))
