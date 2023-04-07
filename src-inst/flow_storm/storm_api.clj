(ns flow-storm.storm-api
  (:require [flow-storm.api :as fs-api]
            [flow-storm.runtime.debuggers-api :as debuggers-api]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.taps :as rt-taps]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.mem-reporter :as mem-reporter]
            [flow-storm.runtime.indexes.frame-index :as frame-index]))

(defn start-recorder []
  (when-let [fn-expr-limit-prop (System/getProperty "flowstorm.fnExpressionsLimit")]
    (alter-var-root #'frame-index/fn-expr-limit (constantly (Integer/parseInt fn-expr-limit-prop))))

  (indexes-api/start))

(defn start-ui []
  (let [theme-prop (System/getProperty "flowstorm.theme")
        theme-key (case theme-prop
                    "light" :light
                    "dark"  :dark
                    :auto)
        config {:local? true
                :theme theme-key}
         start-debugger (requiring-resolve (symbol (name fs-api/debugger-main-ns) "start-debugger"))
         enqueue-event! (requiring-resolve 'flow-storm.debugger.events-queue/enqueue-event!)]

     ;; start the debugger UI
     (start-debugger config)

     (rt-events/subscribe! enqueue-event!)

     (rt-values/clear-values-references)

     (rt-taps/setup-tap!)

     (mem-reporter/run-mem-reporter)

     ;; TODO: change it for something better
     (rt-events/publish-event! (rt-events/make-flow-created-event nil nil 0 nil))))

(defn jump-to-last-exception []
  (let [last-ex-loc (indexes-api/get-last-exception-location)]
    (if last-ex-loc
      (debuggers-api/goto-location nil last-ex-loc)
      (println "No exception recorded"))))

(defn jump-to-last-expression []
  (let [thread-id (.getId (Thread/currentThread))
        last-ex-loc (let [cnt (indexes-api/timeline-count nil thread-id)]
                      {:thread/id thread-id
                       :thread/name (.getName (Thread/currentThread))
                       :thread/idx (dec cnt)})]
    (if last-ex-loc
      (debuggers-api/goto-location nil last-ex-loc)
      (println "No recordings for this thread yet"))))

(defn reset-all-threads-trees-build-stack []
  (indexes-api/reset-all-threads-trees-build-stack nil))

(defn get-fn-expr-limit []
  frame-index/fn-expr-limit)
