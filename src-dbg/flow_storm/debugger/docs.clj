(ns flow-storm.debugger.docs
  (:require [mount.core :as mount :refer [defstate]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [flow-storm.utils :refer [log]]))

(declare start)
(declare stop)
(declare fn-docs)

(defstate fn-docs
  :start (start)
  :stop (stop))

(defn start []
  (log "[Starting docs subsystem]")
  (let [samples-uris (let [cl (.. Thread currentThread getContextClassLoader)]
                       (->> (enumeration-seq (.getResources cl "samples.edn"))
                            (map #(.toURI %))))]
    (log (format "Docs using %s samples files" (pr-str samples-uris)))
    (reduce (fn [r file]
              (merge r (-> file
                           slurp
                           edn/read-string)))
            {}
            samples-uris)))

(defn stop []
  (log "[Stopping docs subsystem]")
  nil)
