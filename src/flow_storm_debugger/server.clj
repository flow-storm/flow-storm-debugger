(ns flow-storm-debugger.server
  (:require [org.httpkit.server :as http-server]
            [compojure.core :as compojure :refer [GET POST]]
            [compojure.route :refer [resources]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.util.response :refer [resource-response]]
            [clojure.core.async :refer [go-loop] :as async]
            [ring.util.request :refer [body-string]]
            [flow-storm-debugger.ui.events :as ui.events]
            [flow-storm-debugger.ui.events.traces :as events.traces]
            [flow-storm-debugger.ui.db :as ui.db]
            [flow-storm-debugger.ui.main-screen :as ui.main-screen]
            [cljfx.api :as fx])
  (:import [javafx.application Platform])
  (:gen-class))

(def server (atom nil))

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

(defn dispatch-ws-event [event]
  (let [[e-key e-data-map] event]
    
    (case e-key
      :flow-storm/add-trace                (swap! ui.db/*state fx/swap-context events.traces/add-trace e-data-map) 
      :flow-storm/init-trace               (swap! ui.db/*state fx/swap-context events.traces/init-trace e-data-map)        
      :flow-storm/add-bind-trace           (swap! ui.db/*state fx/swap-context events.traces/add-bind-trace e-data-map) 
      (println "Don't know how to handle" event))
        
    (when (#{:flow-storm/add-trace :flow-storm/init-trace :flow-storm/add-bind-trace} e-key)
      (swap! ui.db/*state fx/swap-context update-in [:stats :received-traces-count] inc))
    
    (println "Got event " event)))

(defn -main [& args]
  (let [{:keys [ws-routes ws-send-fn ch-recv connected-uids-atom]} (build-websocket)
        port 7722]

    (Platform/setImplicitExit true)


    (go-loop []
      (try
        (let [msg (async/<! ch-recv)
              [e-key e-data-map :as event]  (:event msg)]
          
          (if (#{:chsk/uidport-open :chsk/uidport-close} e-key)
            (let [clients-count (-> msg :connected-uids deref :any count)]
              (swap! ui.db/*state fx/swap-context assoc-in [:stats :connected-clients] clients-count))
            
            (dispatch-ws-event event)))
        
        (catch Exception e
          (println "ERROR handling ws message" e)))
      (recur))

    (fx/mount-renderer ui.db/*state ui.main-screen/renderer)
    (reset! server (http-server/run-server (-> (compojure/routes ws-routes)
                                              (wrap-cors :access-control-allow-origin [#"http://localhost:9500"]
                                                         :access-control-allow-methods [:get :put :post :delete])
                                              wrap-keyword-params
                                              wrap-params)
                                           {:port port}))
    #_(println "HTTP server running on 7722")))

(comment
  (def some-events [[:flow-storm/connected-clients-update {:count 1}]
                  [:flow-storm/init-trace {:flow-id 4094, :form-id 1195953040, :form-flow-id 94111, :form "(->> (range 3) (map inc))"}]
                  [:flow-storm/add-trace {:flow-id 4094, :form-id 1195953040, :form-flow-id 94111, :coor [2 1], :result "#function[clojure.core/inc]"}]
                  [:flow-storm/add-trace {:flow-id 4094, :form-id 1195953040, :form-flow-id 94111, :coor [1], :result "(0 1 2)"}]
                  [:flow-storm/add-trace {:flow-id 4094, :form-id 1195953040, :form-flow-id 94111, :coor [], :result "(1 2 3)"}]
                    [:flow-storm/add-trace {:flow-id 4094, :form-id 1195953040, :form-flow-id 94111, :coor [], :result "(1 2 3)", :outer-form? true}]])

  (def some-events-2 [[:flow-storm/connected-clients-update {:count 1}]
                      [:flow-storm/init-trace {:flow-id 333, :form-id 444, :form-flow-id 555, :form "(->> (range 4) (map inc))"}]
                      [:flow-storm/add-trace {:flow-id 333, :form-id 444, :form-flow-id 555, :coor [2 1], :result "#function[clojure.core/inc]"}]
                      [:flow-storm/add-trace {:flow-id 333, :form-id 444, :form-flow-id 555, :coor [1], :result "(0 1 2)"}]
                      [:flow-storm/add-trace {:flow-id 333, :form-id 444, :form-flow-id 555, :coor [], :result "(1 2 3)"}]
                      [:flow-storm/add-trace {:flow-id 333, :form-id 444, :form-flow-id 555, :coor [], :result "(1 2 3)", :outer-form? true}]])

  (doseq [e some-events-2]
    (dispatch-local-event nil [nil e]))
  
  (require '[flow-storm.api :as fsa])
  (def m-thread (Thread. (fn [] (-main))))
  (.start m-thread)
  (.stop m-thread)
  (fsa/connect)
  )
