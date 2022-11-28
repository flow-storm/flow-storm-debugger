(ns flow-storm.fn-sampler.core
  (:require [hansel.api :as hansel]
            [flow-storm.utils :as utils]
            [clojure.tools.build.api :as tools.build]
            [clojure.string :as str]
            [flow-storm.runtime.indexes.utils :as index-utils]
            [clojure.java.io :as io])
  (:import [java.io File]))

(def max-samples-per-fn 3)
(def max-map-keys 20)

(def ^:dynamic *context*)

(defn- type-desc

  "If `o` is non nil, returns a string description for the type of `o`"

  ([o] (type-desc o 1))
  ([o lvl]

   (when o
     (let [fmt-coll (fn [fmt-str o]
                     (format fmt-str (if (pos? lvl)
                                       (type-desc (first o) (dec lvl))
                                       "")))]
      (cond
        (map? o)    (format "{ %s }" (let [ks (keys o)]
                                       (if (> (count ks) max-map-keys)
                                         (str (str/join " " (take max-map-keys (keys o))) " ...")
                                         (str/join " " (keys o)))))
        (vector? o) (fmt-coll "[ %s ]" o)
        (seq? o)    (fmt-coll "( %s )" o)
        (set? o)    (fmt-coll "#{ %s }" o)
        :else (str/replace (str (type o)) "class " ""))))))

(defn- serialize-val [v]
  (binding [*print-length* 1
            *print-level* 2
            *print-readably* true]

    (try
      (str/replace (with-out-str (print v) #_(pp/pprint v)) "..." ":...")
      (catch Exception _
        (utils/log-error "Couldn't serialize val")
        "ERROR-SERIALIZING"))))

(defn add-fn [fns-map {:keys [ns fn-name fn-args return]}]
  (let [fq-fn-symb (symbol ns fn-name)
        args-types-cnt (count (get-in fns-map [fq-fn-symb :args-types]))
        returns-type-cnt (count (get-in fns-map [fq-fn-symb :return-types]))
        call-examples-cnt (count (get-in fns-map [fq-fn-symb :call-examples]))
        args-types (when (< args-types-cnt max-samples-per-fn)
                     (mapv type-desc fn-args))
        return-type (when (< returns-type-cnt max-samples-per-fn)
                      (type-desc return))
        call-example (when (< call-examples-cnt max-samples-per-fn)
                       {:args fn-args
                        :ret return})]
    (cond-> fns-map
      args-types   (update-in [fq-fn-symb :args-types] (fnil conj #{}) args-types)
      return-type  (update-in [fq-fn-symb :return-types] (fnil conj #{}) return-type)
      call-example (update-in [fq-fn-symb :call-examples] (fnil conj #{}) call-example))))

(defn collect-fn-call [{:keys [collected-fns]} fn-call]
  (swap! collected-fns add-fn fn-call))

(defn push-thread-frame [{:keys [threads-stacks]} thread-id frame]
  (locking threads-stacks

    (when-not (index-utils/mh-contains? threads-stacks thread-id)
      (index-utils/mh-put threads-stacks thread-id (index-utils/make-mutable-stack)))

    (let [th-stack (index-utils/mh-get threads-stacks thread-id)]
      (index-utils/ms-push th-stack frame))))

(defn pop-thread-frame [{:keys [threads-stacks]} thread-id]
  (locking threads-stacks
    (let [th-stack (index-utils/mh-get threads-stacks thread-id)]
      (index-utils/ms-pop th-stack))))

(defn peek-thread-frame [{:keys [threads-stacks]} thread-id]
  (locking threads-stacks
    (let [th-stack (index-utils/mh-get threads-stacks thread-id)]
      (index-utils/ms-peek th-stack))))

(defn init-stats [{:keys [stats]} {:keys [inst-fns]}]
  (reset! stats {:total-inst-fns (count inst-fns)
                 :rem-fns inst-fns
                 :last-rem-fns-report nil}))

(defn report-stats [{:keys [total-inst-fns rem-fns]}]
  (let [total-sampled (- total-inst-fns (count rem-fns))
        cover-perc (float (* 100 (/ total-sampled total-inst-fns)))]

    (binding [*out* *err*]
      (println (format "Sampled %d of %d instrumented with a coverage of %.2f %%"
                       total-sampled
                       total-inst-fns
                       cover-perc)))))

(defn update-stats [{:keys [stats]} {:keys [ns fn-name fn-args]}]
  (let [arity-vec [(symbol ns fn-name) (count fn-args)]]
    (swap! stats (fn [s]
                   (-> s
                       (update :rem-fns disj arity-vec))))

    (let [{:keys [last-rem-fns-report rem-fns] :as sts} @stats]
      (when (not= last-rem-fns-report rem-fns)
        (report-stats sts)
        (swap! stats assoc :last-rem-fns-report rem-fns)))))

(defn trace-fn-call [fn-call-data]
  (let [curr-thread-id (utils/get-current-thread-id)]
    (push-thread-frame *context* curr-thread-id fn-call-data)))

(defn trace-fn-return [{:keys [return]}]
  (let [curr-thread-id (utils/get-current-thread-id)
        {:keys [unnamed-fn?] :as frame-data} (peek-thread-frame *context* curr-thread-id)]

    (when-not unnamed-fn?
      (collect-fn-call *context* (assoc frame-data :return return)))

    (update-stats *context* frame-data)

    (pop-thread-frame *context* curr-thread-id))
  return)

(defn- symb-var-meta [vsymb]
  (when vsymb
    (let [v (find-var vsymb)]
      (-> (meta v)
          (select-keys [:added :ns :name :file :static :column :line :arglists :doc])
          (update :ns (fn [ns] (when ns (ns-name ns))))
          (update :arglists str)))))

(defn make-result [fns-map]
  (let [total-cnt (count fns-map)]

    (utils/log (format "Building results for a fns-map of size %d" total-cnt))

    (reduce-kv (fn [r fsymb fdata]
                 (let [data (assoc fdata :var-meta (symb-var-meta fsymb))]
                   (assoc r fsymb data)))
               {}
               fns-map)))

(defn save-result [result {:keys [result-name]}]
  (utils/log "Saving result ...")

  (let [tmp-dir (.getAbsolutePath (utils/mk-tmp-dir!))
        result-file-str (pr-str result)
        result-file-path (str tmp-dir File/separator "samples.edn")]

    (utils/log (format "Saving results in %s" result-file-path))
    (spit result-file-path result-file-str)

    ;; for dev purposes
    (io/copy (io/file result-file-path) (io/file "/home/jmonetta/my-projects/flow-storm-debugger/samples.edn"))

    (utils/log (str "Wrote " result-file-path " creating jar file."))
    (tools.build/jar {:class-dir tmp-dir
                      :jar-file (str result-name ".jar")})
    (utils/log "Jar file created.")))

(defn process-collected-fns [collected-fns]

  (let [total-cnt (count collected-fns)]

    (utils/log (format "Processing %d collected fns call-examples. Serializing values ..." total-cnt))

    (utils/update-vals
     collected-fns
     (fn [data]
       (update data :call-examples
               (fn [ce]
                 (->> ce
                      (map (fn [ex]
                             (-> ex
                                 (update :args #(mapv serialize-val %))
                                 (update :ret #(serialize-val %))))))))))))

(defmacro sample [{:keys [uninstrument? inst-ns-prefixes print-unsampled?]
                                :as config} & forms]
  `(let [stats# (atom {})
         ctx# {:collected-fns (atom {})
               :threads-stacks (index-utils/make-mutable-hashmap)
               :stats stats#}]

     (utils/log "Instrumentation done. Running forms ...")

     (alter-var-root #'*context* (constantly ctx#))

     ;; HACKY, this is because calling hansel/instrument-namespaces-clj will
     ;; already fire trace-fn-call and trace-fn-return because of macroexpansion
     (init-stats *context* #{})

     (let [inst-result# (hansel/instrument-namespaces-clj
                         ~inst-ns-prefixes
                         (merge
                          ~config
                          {:trace-fn-call trace-fn-call
                           :trace-fn-return trace-fn-return}))]

       (init-stats *context* inst-result#)

       ~@forms

       (let [fns-map# (process-collected-fns @(:collected-fns ctx#))]
         (report-stats @stats#)

         (save-result (make-result fns-map#) ~config)

         (utils/log "Results saved.")

         (when ~print-unsampled?

           (utils/log "Unsampled functions :")

           (let [unsample-fns# (->> (:rem-fns @stats#)
                                    (map first)
                                    (into #{}))]
             (doseq [uf# (sort unsample-fns#)]
               (utils/log uf#))))))

     (when ~uninstrument?
       (utils/log "Uninstrumenting...")
       (hansel/uninstrument-namespaces-clj ~inst-ns-prefixes))

     (utils/log "All done!")))
