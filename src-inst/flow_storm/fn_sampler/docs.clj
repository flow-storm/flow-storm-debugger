(ns flow-storm.fn-sampler.docs
  (:require [clojure.tools.build.api :as tools.build]
            [flow-storm.utils :as utils]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.io File]))

(def docs-file-name "flow-docs.edn")
(def deprecated-docs-file-name "samples.edn")

(defn- make-docs [fns-map]
  {:functions/data fns-map})

(defn write-docs

  "Save the resulting sampled FNS-MAP into a file and wrap it in a jar with RESULT-NAME"

  [fns-map {:keys [result-name]}]
  (utils/log "Saving result ...")

  (let [tmp-dir (.getAbsolutePath (utils/mk-tmp-dir!))
        result-file-str (-> (make-docs fns-map)
                            pr-str)
        result-file-path (str tmp-dir File/separator docs-file-name)]

    (utils/log (format "Saving results in %s" result-file-path))
    (spit result-file-path result-file-str)

    (utils/log (str "Wrote " result-file-path " creating jar file."))
    (tools.build/jar {:class-dir tmp-dir
                      :jar-file (str result-name ".jar")})
    (utils/log "Jar file created.")))

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

  (utils/log (format "Loading docs using %s files" (pr-str files)))

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
