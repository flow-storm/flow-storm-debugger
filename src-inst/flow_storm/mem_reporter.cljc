(ns flow-storm.mem-reporter
  (:require [clojure.core.async :as async]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.utils :as utils]))

(def reporter-interval 1000)
(def reporter (atom nil))

(defn run-mem-reporter []  
  (let [stop-ch (async/chan)]

    (reset! reporter stop-ch)
    
    (utils/log "Runtime starting mem reporting subsystem")
    (async/go-loop []
      (let [[_ ch] (async/alts! [(async/timeout reporter-interval)
                                 stop-ch])]
        (if (= ch stop-ch)

          (utils/log "Runtime stopping mem reporting subsystem")
          
          (let [heap-info (utils/get-memory-info)
                ev (rt-events/make-heap-info-update-event heap-info)]
            (rt-events/publish-event! ev)
            (recur)))))))

(defn stop-mem-reporter []  
  (async/put! @reporter :stop))

