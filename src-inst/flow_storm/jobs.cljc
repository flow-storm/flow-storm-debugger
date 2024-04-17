(ns flow-storm.jobs
  (:require [flow-storm.runtime.events :as rt-events]
            [flow-storm.utils :as utils :refer [log]])
  #?(:clj (:import [java.util.concurrent Executors TimeUnit])))

(def mem-reporter-interval 1000)
(def updates-reporter-interval 1000)
(defonce cancel-jobs-fn (atom nil))


#?(:clj (defonce scheduled-thread-pool (Executors/newScheduledThreadPool 1)))

#?(:clj
   (defn schedule-repeating-fn [f millis]
     (let [sched-feature (.scheduleAtFixedRate scheduled-thread-pool
                                               f
                                               millis
                                               millis
                                               TimeUnit/MILLISECONDS)]
       (log (str f " function scheduled every " millis " millis"))
       (fn []
         (.cancel sched-feature false)
         (log (str f " scheduled function cancelled.")))))

   :cljs
   (defn schedule-repeating-fn [f millis]
     (let [interval-id (js/setInterval f millis)]
       (log (str f " function scheduled every " millis " millis"))
       (fn []
         (js/clearInterval interval-id)
         (log (str f " scheduled function cancelled."))))))

(defn run-jobs []
  (let [mem-job-cancel (schedule-repeating-fn                   
                        (fn mem-reporter []
                          (let [heap-info (utils/get-memory-info)
                                ev (rt-events/make-heap-info-update-event heap-info)]                            
                            (rt-events/publish-event! ev)))
                        mem-reporter-interval)
        updates-job-cancel (schedule-repeating-fn                   
                            (fn timelines-updates []
                              )
                            updates-reporter-interval)]
    (reset! cancel-jobs-fn (fn []
                             (mem-job-cancel)
                             (updates-job-cancel)))))

(defn stop-jobs []  
  (when-let [stop-fn @cancel-jobs-fn]
    (stop-fn)))

