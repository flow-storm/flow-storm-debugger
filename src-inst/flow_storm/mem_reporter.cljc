(ns flow-storm.mem-reporter
  (:require #?(:clj [flow-storm.runtime.events :as rt-events])
            [flow-storm.utils :as utils]))

(def reporter-interval 1000)
(def reporter-stop-fn (atom nil))

#?(:clj
   (defn run-mem-reporter []  
     (let [thread (Thread.
                   (fn []
                     (loop []
                       (if (.isInterrupted (Thread/currentThread))

                         (utils/log "Runtime stopping mem reporting subsystem")
                         
                         (let [heap-info (utils/get-memory-info)
                               ev (rt-events/make-heap-info-update-event heap-info)]
                           (rt-events/publish-event! ev)
                           (Thread/sleep reporter-interval)
                           (recur)))))
                   "FlowStorm Memory Reporter")
           interrupt-fn (fn [] (.interrupt thread))]

       (reset! reporter-stop-fn interrupt-fn)
       (.start thread)))

   :cljs (defn run-mem-reporter []
           (utils/log "Mem reporter not starting. Not implemented for ClojureScript yet.")))

(defn stop-mem-reporter []  
  (when-let [stop-fn @reporter-stop-fn]
    (stop-fn)))

