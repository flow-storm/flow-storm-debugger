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
            [ring.util.request :refer [body-string]]))

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

(defn save-flow [{:keys [params] :as req}]
  (spit (str "./" (:file-name params)) (body-string req)))

(defn build-routes [opts]
  (compojure/routes
   (GET "/" [] (resource-response "index.html" {:root "public"}))
   (POST "/save-flow" req (do (save-flow req) {:status 200}) )
   (resources "/")))

(defn handle-ws-message [send-fn {:keys [event client-id user-id] :as msg}]
  (if (= client-id "browser")
    ;; message comming from the browser
    nil #_(println "browser -> tracer" event)

    ;; if client-id is not "browser" then it is one of the tracers
    ;; if we get a message from a tracer, just forward it to the browser
    (let [[evk] event]
      (if (#{:chsk/uidport-open :chsk/uidport-close} evk )
        ;; dec by one the count so we don't count the browser as another tracer
        (send-fn "browser" [:flow-storm/connected-clients-update {:count (dec (count (:any @(:connected-uids msg))))}])

        (when (#{:flow-storm/init-trace :flow-storm/add-trace :flow-storm/add-bind-trace} evk)
          (send-fn "browser" event))))))

(defn -main [& args]
  (let [{:keys [ws-routes ws-send-fn ch-recv connected-uids-atom]} (build-websocket)
        port 7722]

    (go-loop []
      (try
        (handle-ws-message ws-send-fn (async/<! ch-recv))
        (catch Exception e
          (println "ERROR handling ws message")))
      (recur))

    (reset! server (http-server/run-server (-> (compojure/routes ws-routes (build-routes {:port port}))
                                              (wrap-cors :access-control-allow-origin [#"http://localhost:9500"]
                                                         :access-control-allow-methods [:get :put :post :delete])
                                              wrap-keyword-params
                                              wrap-params)
                                           {:port port}))
    (println "HTTP server running on 7722")))

(comment
  (require '[flow-storm.api :as fsa])
  (-main)
  (fsa/connect)

  )
