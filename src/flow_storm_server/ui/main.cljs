(ns flow-storm-server.ui.main
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch]]
            [taoensso.sente  :as sente]
            [clojure.core.async :refer [go-loop] :as async]
            [flow-storm-server.ui.views :as views]
            [flow-storm-server.ui.events :as events]))

(defn ^:dev/after-load mount-component []
  (r/render [views/main-screen] (.getElementById js/document "app")))

(defn handle-ws-message [{:keys [event]}]
  (let [[_ evt] event]
    (let [[e-key e-data-map] evt]
      (case e-key
        :flow-storm/add-trace  (dispatch [::events/add-trace e-data-map])
        :flow-storm/init-trace (dispatch [::events/init-trace e-data-map]))
      (println "Got event " evt))))

(defn init []
  (mount-component)
  (let [?csrf-token (when-let [el (.getElementById js/document "sente-csrf-token")]
                      (.getAttribute el "data-csrf-token"))
        {:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
         "/chsk" ; Note the same path as before
         ?csrf-token
         {:type :auto
          :client-id "browser"
          :host "localhost"
          :port 8080})]
    (go-loop []
      (try
        (handle-ws-message (async/<! ch-recv))
        (catch js/Error e
          (js/console.error "Error handling ws message")))
      (recur))))
