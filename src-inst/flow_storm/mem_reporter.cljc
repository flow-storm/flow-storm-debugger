(ns flow-storm.mem-reporter
  (:require [clojure.core.async :as async]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.utils :as utils]))

(def reporter-interval 1000)
(def reporter (atom nil))

(defn run-mem-reporter []  
  (let [stop-ch (async/chan)]

    (reset! reporter stop-ch)
    
    (utils/log "[Starting mem reporting subsystem]")
    (async/go-loop []
      (let [[_ ch] (async/alts! [(async/timeout reporter-interval)
                                 stop-ch])]
        (when-not (= ch stop-ch)
          (let [{:keys [max-bytes free-bytes]} (utils/get-memory-info)
                ev (rt-events/make-heap-info-update-event max-bytes free-bytes)]
            (rt-events/publish-event! ev)
            (recur)))))
    (utils/log "[Stopping mem reporting subsystem]")))

(defn stop-mem-reporter []
  (async/put! @reporter :stop))

