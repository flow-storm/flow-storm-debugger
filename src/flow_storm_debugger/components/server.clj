(ns flow-storm-debugger.components.server
  (:require [com.stuartsierra.component :as sierra.component]
            [org.httpkit.server :as http-server]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [taoensso.sente :as sente]
            [compojure.core :as compojure :refer [GET POST]]
            [compojure.route :refer [resources]]
            [flow-storm-debugger.components.ui :as ui]
            [flow-storm-debugger.ui.events.traces :as events.traces]
            [clojure.core.async :refer [go-loop] :as async]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [taoensso.timbre :as log]
            [cljfx.api :as fx]

            [cognitect.transit :as transit]
            [taoensso.sente.packers.transit :as sente-transit]))

(defn build-websocket []
  ;; TODO: move all this stuff to sierra components or something like that
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter)
                                    {:csrf-token-fn nil
                                     :packer (sente-transit/->TransitPacker :json {} {})
                                     :user-id-fn (fn [req] (:client-id req))})]

    {:ws-routes (compojure/routes (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
                                  (POST "/chsk" req (ajax-post-fn                req)))
     :ws-send-fn send-fn
     :ch-recv ch-recv
     :connected-uids-atom connected-uids}))

(defn trace-event? [[e-key]]
  (#{:flow-storm/add-trace :flow-storm/init-trace :flow-storm/add-bind-trace
     :flow-storm/ref-trace :flow-storm/ref-init-trace
     :flow-storm/tap-trace} e-key))

(defn update-state [state event]
  (let [[e-key e-data-map] event]

    (cond-> (case e-key
              :flow-storm/add-trace      (events.traces/add-trace state e-data-map)
              :flow-storm/init-trace     (events.traces/init-trace state e-data-map)
              :flow-storm/add-bind-trace (events.traces/add-bind-trace state e-data-map)
              :flow-storm/ref-init-trace (events.traces/add-ref-init-trace state e-data-map)
              :flow-storm/ref-trace      (events.traces/add-ref-trace state e-data-map)
              :flow-storm/tap-trace      (events.traces/add-tap-trace state e-data-map)

              (do
                (log/warn "Don't know how to handle" {:event event})
                state))

      (trace-event? event) (update-in [:stats :received-traces-count] (fnil inc 0)))))

(defn make-msg-process-thread-fn [ui-state-ref ch-recv]
  (let [update-connected-clients (fn [s clients-count] (assoc-in s [:stats :connected-clients] clients-count))
        increment-traces-count (fn [s] (update-in s [:stats :received-traces-count] (fnil inc 0)))]
    (fn []
      (loop []
        (try
          (let [ws-msg (async/<!! ch-recv)
                [e-key e-data :as event] (:event ws-msg)]

            (cond

              ;; if it is sente telling us a connection has been open or closed
              ;; update the ui connected clients
              (#{:chsk/uidport-open :chsk/uidport-close} e-key)
              (let [clients-count (-> ws-msg :connected-uids deref :any count)]
                (ui/swap-state! ui-state-ref #(update-connected-clients % clients-count)))

              ;; if it is a batch event
              (= :flow-storm/batch e-key)
              (let [event-batch e-data
                    temp-state (ui/deref-state ui-state-ref)
                    new-state (reduce update-state
                                      temp-state
                                      event-batch)]
                (swap! ui-state-ref fx/reset-context new-state))))

          (catch Exception e
            (log/error "Something went wrong inside make-msg-process-thread-fn, skipping message" e)))
        (recur)))))

(defrecord HttpServer [port
                       socket-send-fn ch-recv connected-uids-atom ws-routes
                       msg-process-thread server ui]

  sierra.component/Lifecycle
  (start [this]
    (log/info "Starting HttpServer...")
    (let [msg-proc-thread (Thread. (make-msg-process-thread-fn (:state ui) ch-recv))
          server (http-server/run-server (-> (compojure/routes ws-routes)
                                             wrap-keyword-params
                                             wrap-params)
                                         {:port port
                                          :max-ws (* 50 1024 1024) ;; 50 Mb
                                          })]
      ;; start the message process thread loop
      (.start msg-proc-thread)

      (log/info "HttpServer started.")
      (assoc this
             :msg-process-thread msg-proc-thread
             :server server)))

  (stop [this]
    (log/info "Stopping HttpServer...")
    ((:server this))
    (.stop (:msg-process-thread this))
    (log/info "HttpServer stopped.")
    (dissoc this :server :msg-process-thread)))

(defn http-server [{:keys [host port]}]
  (let [{:keys [ws-routes ws-send-fn ch-recv connected-uids-atom]} (build-websocket)]
    (map->HttpServer {:port port
                      :ws-send-fn ws-send-fn
                      :ch-recv ch-recv
                      :connected-uids-atom connected-uids-atom
                      :ws-routes ws-routes})))
