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
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]))

(defn build-websocket []
  ;; TODO: move all this stuff to sierra components or something like that
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter)
                                    {:csrf-token-fn nil
                                     :user-id-fn (fn [req] (:client-id req))})]

    {:ws-routes (compojure/routes (GET  "/chsk" req (ajax-get-or-ws-handshake-fn req))
                                  (POST "/chsk" req (ajax-post-fn                req)))
     :ws-send-fn send-fn
     :ch-recv ch-recv
     :connected-uids-atom connected-uids}))

(defn dispatch-ws-event [event ui]
  (let [[e-key e-data-map] event]
    
    (case e-key
      :flow-storm/add-trace      (ui/swap-state! ui (fn [s] (events.traces/add-trace s e-data-map))) 
      :flow-storm/init-trace     (ui/swap-state! ui (fn [s] (events.traces/init-trace s e-data-map)))        
      :flow-storm/add-bind-trace (ui/swap-state! ui (fn [s] (events.traces/add-bind-trace s e-data-map)))
      :flow-storm/ref-init-trace (ui/swap-state! ui (fn [s] (events.traces/add-ref-init-trace s e-data-map)))
      :flow-storm/ref-trace      (ui/swap-state! ui (fn [s] (events.traces/add-ref-trace s e-data-map)))
      :flow-storm/tap-trace      (ui/swap-state! ui (fn [s] (events.traces/add-tap-trace s e-data-map)))
      (println "Don't know how to handle" event))
        
    (when (#{:flow-storm/add-trace :flow-storm/init-trace :flow-storm/add-bind-trace
             :flow-storm/ref-trace :flow-storm/ref-init-trace
             :flow-storm/tap-trace} e-key)
      (ui/swap-state! ui (fn [s] (update-in s [:stats :received-traces-count] inc))))))

(defrecord HttpServer [port
                       socket-send-fn ch-recv connected-uids-atom ws-routes
                       msg-process-thread server ui]
  
  sierra.component/Lifecycle
  (start [this]
    (println "Starting HttpServer...")
    (let [msg-proc-thread (Thread.
                           (fn []
                             (loop []
                               (try
                                 (let [msg (async/<!! ch-recv)
                                       [e-key e-data-map :as event]  (:event msg)]
                                   
                                   (if (#{:chsk/uidport-open :chsk/uidport-close} e-key)
                                     (let [clients-count (-> msg :connected-uids deref :any count)]
                                       (ui/swap-state! ui (fn [s] (assoc-in s [:stats :connected-clients] clients-count))))
                                     
                                     (dispatch-ws-event event ui)))
                                 
                                 (catch Exception e
                                   (println "ERROR handling ws message" e)))
                               (recur))))
          server (http-server/run-server (-> (compojure/routes ws-routes)
                                             wrap-keyword-params
                                             wrap-params)
                                         {:port port})]
      ;; start the message process thread loop
      (.start msg-proc-thread)
      
      (println "HttpServer started.")
      (assoc this
             :msg-process-thread msg-proc-thread
             :server server)))

  (stop [this]
    (println "Stopping HttpServer...")
    ((:server this))
    (.stop (:msg-process-thread this))
    (println "HttpServer stopped.")
    (dissoc this :server :msg-process-thread)))

(defn http-server [{:keys [host port]}]
  (let [{:keys [ws-routes ws-send-fn ch-recv connected-uids-atom]} (build-websocket)]
    (map->HttpServer {:port port
                      :ws-send-fn ws-send-fn
                      :ch-recv ch-recv
                      :connected-uids-atom connected-uids-atom
                      :ws-routes ws-routes})))
