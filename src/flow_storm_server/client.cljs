(ns flow-storm-server.client
  (:require [reagent.core :as r]
            [clojure.core.async :refer [go-loop] :as async]
            [taoensso.sente  :as sente]
            [flow-storm-server.highlighter :refer [highlight-expr]]
            [zprint.core :as zp]
            [cljs.tools.reader :as tools-reader]
            #_[flow-storm.tracer :as tracer]
            )
  #_(:require-macros [flow-storm.instrument :refer [t]]))

(defonce state (r/atom {:form-id nil
                        :form nil
                        :trace []
                        :trace-idx 0}))

(defn main-screen []
  (let [{:keys [form trace trace-idx]} @state
        coor (:coor (get trace trace-idx))
        form-str (zp/zprint-str form)
        result  (try
                  (-> (:result (get trace trace-idx))
                      (tools-reader/read-string)
                      zp/zprint-str)
                  (catch js/Error e
                    (:result (get trace trace-idx))))
        hl-expr (highlight-expr form-str coor "<b class=\"hl\">" "</b>")]
    [:div.screen
     [:div.controls.panel
      [:button {:on-click #(swap! state update :trace-idx dec)
                :disabled (zero? trace-idx)}"<"]
      [:button {:on-click #(swap! state update :trace-idx inc)
                :disabled (>= trace-idx (count trace))}">"]
      [:span (str trace-idx "/" (count trace))]]

     [:div.code-result-cont
      [:div.code.panel
       [:pre {:dangerouslySetInnerHTML {:__html hl-expr}}]]

      [:div.result.panel
       [:pre {:dangerouslySetInnerHTML {:__html result}}]]]

     #_[:div.debug.panel
      [:div (str "Current coor: " coor)]
      [:div (str "Trace: " trace)]]]))

(defn ^:dev/after-load mount-component []
  (r/render [main-screen] (.getElementById js/document "app")))

(defn handle-ws-message [{:keys [event]}]
  (let [[_ evt] event]
    (let [[e-key e-data-map] evt]
      (case e-key
        :flow-storm/add-trace  (swap! state update :trace (fn [t] (conj t (select-keys e-data-map [:coor :result]))))
        :flow-storm/init-trace (swap! state assoc
                                  :form-id (:traced-form-id e-data-map)
                                  :form (:form e-data-map)
                                  :trace []
                                  :trace-idx 0))
     (println "Got event " evt))
    ))

(defn  main []
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
