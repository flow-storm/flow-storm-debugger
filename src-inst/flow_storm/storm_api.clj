(ns flow-storm.storm-api
  (:require [flow-storm.api :as fs-api]
            [flow-storm.runtime.debuggers-api :as debuggers-api]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.indexes.frame-index :as frame-index]))

(defn start-recorder []
  (when-let [fn-expr-limit-prop (System/getProperty "flowstorm.fnExpressionsLimit")]
    (alter-var-root #'frame-index/fn-expr-limit (constantly (Integer/parseInt fn-expr-limit-prop))))

  (indexes-api/start))

(defn start-debugger []
  (let [theme-prop (System/getProperty "flowstorm.theme")
        styles-prop (System/getProperty "flowstorm.styles")
        theme-key (case theme-prop
                    "light" :light
                    "dark"  :dark
                    :auto)
        config {:local? true
                :styles styles-prop
                :skip-index-start? true
                :theme theme-key}]

    (fs-api/local-connect config)))

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
