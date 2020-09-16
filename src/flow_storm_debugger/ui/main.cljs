(ns flow-storm-debugger.ui.main
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [taoensso.sente  :as sente]
            [clojure.core.async :refer [go-loop] :as async]
            [flow-storm-debugger.ui.views :as views]
            [flow-storm-debugger.ui.events :as events]))

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
  (dispatch-sync [::events/init])
  (let [?csrf-token (when-let [el (.getElementById js/document "sente-csrf-token")]
                      (.getAttribute el "data-csrf-token"))
        {:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
         "/chsk" ; Note the same path as before
         ?csrf-token
         {:type :auto
          :client-id "browser"
          :host "localhost"
          :port 7722})]
    (go-loop []
      (try
        (handle-ws-message (async/<! ch-recv))
        (catch js/Error e
          (js/console.error "Error handling ws message")))
      (recur))))

(comment
  (dispatch [::events/init-trace
             {:flow-id 3493,
              :form-id -194436103,
              :form "(try (let [{body :Body} (js->clj message :keywordize-keys true)
                   {:video/keys [url id tags], :as video} (transit/read r body)
                   [before key] (string/split url \"amazonaws.com/\")
                   bucket (-> (string/split before \".s3\") first (string/split \"https://\") second)]
               (log/info \"Transcoding & video\" video)
               (<!p (read-file {:id id, :S3 S3, :bucket bucket, :key key}))
               (<!p (start-transcoding id))
               (let [video-file (.readFileSync fs (str id \"-transcoded.mp4\")) placeholder-file (.readFileSync fs (str \"placeholder/\" id \".png\")) gif-file (.readFileSync fs (str id \".gif\")) transcoded-key (str \"1080/\" key) first-transcoding? (not (s3-api/get-object S3 {:bucket bucket, :key transcoded-key})) new-url (.-Location (<!p (s3-api/upload S3 {:bucket (get-in config [:aws :bucket-name]), :key transcoded-key, :body video-file}))) placeholder-url (.-Location (<!p (s3-api/upload S3 {:bucket (get-in config [:aws :bucket-name]), :key (clojure.string/replace transcoded-key \".mp4\" \".png\"), :body placeholder-file}))) gif-url (.-Location (<!p (s3-api/upload S3 {:bucket (get-in config [:aws :bucket-name]), :key (clojure.string/replace transcoded-key \".mp4\" \".gif\"), :body gif-file}))) new-video (merge video {:video/url new-url, :video/gif-url gif-url, :video/placeholder-url placeholder-url}) {:video/keys [id], :as db-response} (<! (models.videos/insert-video! new-video db))]
                 (clean-up video) (if (= (:video/id video) id) (do (when first-transcoding? (when (seq tags) (<! (models.videos/insert-video-tags! id (take 2 tags) db))) (sqs-api/send-message SQS {:queue-url (get-in config [:aws :notification-queue-url]), :body (assoc new-video :type :video-uploaded)})) (log/info \"Successfully transcoded video\" new-video) (resolve new-video)) (let [message \"Error when persisting video\"] (log/error message (assoc video :error db-response)) (reject (js/Error. message))))))
             (catch :default e (log/error \"Unexpected transcoding service error\" {:error e}) (reject e)))"
              }])
)
