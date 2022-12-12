(ns flow-storm.debugger.docs
  (:require [mount.core :as mount :refer [defstate]]
            [clojure.edn :as edn]
            [flow-storm.utils :refer [log]]
            [clojure.java.io :as io]))

(declare start)
(declare stop)
(declare fn-docs)

(defstate fn-docs
  :start (start)
  :stop (stop))

(defn start []
  (log "[Starting docs subsystem]")
  (let [resources-uris (let [cl (.. Thread currentThread getContextClassLoader)]
                         (->> (enumeration-seq (.getResources cl "samples.edn"))
                              (map #(.toURI %))))
        dev-sample-file (io/file "samples.edn")
        samples-files (cond-> resources-uris
                        (.exists dev-sample-file) (conj dev-sample-file))]
    (log (format "Docs using %s samples files" (pr-str samples-files)))
    (reduce (fn [r file]
              (merge r (-> file
                           slurp
                           edn/read-string)))
            {}
            samples-files)))

(defn stop []
  (log "[Stopping docs subsystem]")
  nil)
