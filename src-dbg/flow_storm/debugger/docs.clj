(ns flow-storm.debugger.docs
  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(declare start)
(declare stop)
(declare fn-docs)

(def docs-file-name "flow-docs.edn")
(def deprecated-docs-file-name "samples.edn")

(defn- classpath-uris [file-name]
  (let [cl (.. Thread currentThread getContextClassLoader)]
    (->> (enumeration-seq (.getResources cl file-name))
         (map #(.toURI %)))))

(defn- read-deprecated-docs [files]
  (let [fns-data (reduce (fn [r file]
                           (merge r (-> file
                                        slurp
                                        edn/read-string)))
                         {}
                         files)]
    {:functions/data fns-data}))

(defn- read-docs [files]

  (when (seq files)
    (utils/log (format "Loading docs using %s files" (pr-str files))))

  (reduce (fn [r file]
            (merge-with merge  r (-> file
                                     slurp
                                     edn/read-string)))
          {}
          files))

(defn read-classpath-docs []
  (let [dev-docs-file (io/file docs-file-name) ;; just to make dev easier
        docs-files (cond-> (classpath-uris docs-file-name)
                     (.exists dev-docs-file) (conj dev-docs-file))
        docs-maps (read-docs docs-files)
        deprecated-docs-maps (read-deprecated-docs (classpath-uris deprecated-docs-file-name))]

    (merge-with merge docs-maps deprecated-docs-maps)))

(defstate fn-docs
  :start (fn [_] (start))
  :stop (fn [] (stop)))

(defn start []
  (-> (read-classpath-docs)
      :functions/data))

(defn stop []
  nil)
