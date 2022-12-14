(ns flow-storm.fn-sampler.core

  "Instrument entire namespaces for sampling fn args and return values.

  The main entry point is flow-storm.fn-sampler.core/sample

  When instrumented namespaces code runs, the sampler will collect fns
  into a map {fully-qualified-fn-symbol-1 {:args-types #{...}
                                           :return-types #{...}
                                           :call-examples #{...}
                                           :var-meta {...}}
              fully-qualified-fn-symbol-2 {...}
              ...}

  end save it into a edn file then wrapped into a jar.

  :args-type is a set of vectors with sampled types descriptions.

  :return-types is a set of sampled types descriptions.

  :call-examples is a set of {:args [sv] :ret sv} where sv is the value serialization of the sampled value.
                 See `serialize-val` to see how values get serialized.

  :var-meta is meta as returned by Clojure (meta (var fully-qualified-fn-symbol))"

  (:require [hansel.api :as hansel]
            [flow-storm.utils :as utils]
            [clojure.tools.build.api :as tools.build]
            [clojure.string :as str]
            [clojure.set :as set]
            [flow-storm.runtime.indexes.utils :as index-utils]
            [clojure.pprint :as pp])
  (:import [java.io File]))

(def max-samples-per-fn 3)
(def max-map-keys 20)

(def ^:dynamic *tracing* false)

(def context nil)

(defn type-name [o]
  (when o
    (.getName (class o))))

(defn- map-desc [m]
  (let [mdesc {:type/name (type-name m)
               :type/type :map}]
    (cond

      (keyword? (first (keys m)))
      (assoc mdesc
             :map/domain (->> (keys m)
                              (take 100) ;; should be enough for entities
                              (reduce (fn [r k]
                                        (assoc r k (type-name (get m k))))
                                      {}))
             :map/kind :entity)

      (and (some->> (keys m) (map class) (apply =))
           (some->> (vals m) (map class) (apply =)))
      (assoc mdesc
             :map/domain {(type-name (first (keys m))) (type-name (first (vals m)))}
             :map/kind :regular)

      :else mdesc)))

(defn- seqable-desc [xs]
  (let [first-elem-type (when (seq xs)
                          (let [first-elem (first xs)]
                            (if (utils/hash-map? first-elem)
                              (map-desc first-elem)
                              (type-name first-elem))))]
    (cond-> {:type/name (type-name xs)
             :type/type :seqable}
      first-elem-type (assoc :seq/first-elem-type first-elem-type))))

(defn type-desc

  "If `o` is non nil, returns a string description for the type of `o`"

  [o]

  (when o
    (cond
      (fn? o)
      {:type/type :fn}

      (utils/hash-map? o)
      (map-desc o)

      (and (seqable? o)
           (not (string? o))
           (counted? o))
      (seqable-desc o)

      :else
      {:type/name (type-name o)})))

(defn- serialize-val [{:keys [examples-print-fn examples-print-length examples-print-level]} v]
  (binding [*print-length* examples-print-length
            *print-level* examples-print-level
            *print-readably* true]
    (try
      (str/replace (with-out-str (examples-print-fn v)) "..." ":...")
      (catch Exception _
        (utils/log-error "Couldn't serialize val")
        "ERROR-SERIALIZING"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Keep track of fns call stacks per thread  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;
;; Stats ;;
;;;;;;;;;;;

(defn empty-stats []
  {:inst-fns nil
   :sampled-fns #{}
   :last-sampled-fns-report nil})

(defn set-stats-inst-fns [{:keys [stats]} {:keys [inst-fns]}]
  (utils/log (str "Initializing stats. inst-fns count " (count inst-fns)))
  (swap! stats assoc :inst-fns (into #{} (map first inst-fns))))

(defn report-stats [{:keys [inst-fns sampled-fns]}]
  (when inst-fns
    (let [total-sampled (count sampled-fns)
          total-inst-fns (count inst-fns)
          cover-perc (float (* 100 (/ total-sampled total-inst-fns)))]

      (binding [*out* *err*]
        (println (utils/colored-string
                  (format "Sampled %d of %d instrumented with a coverage of %.2f %%"
                          total-sampled
                          total-inst-fns
                          cover-perc)
                  :yellow))))))

(defn update-stats [{:keys [stats]} {:keys [ns fn-name]}]
  (swap! stats (fn [s]
                 (-> s
                     (update :sampled-fns conj (symbol ns fn-name)))))

  (let [{:keys [last-sampled-fns-report sampled-fns] :as sts} @stats]
    (when (not= last-sampled-fns-report sampled-fns)
      (report-stats sts)
      (swap! stats assoc :last-sampled-fns-report sampled-fns))))

;;;;;;;;;;;;;;;;;;;;;;
;; Hansel callbacks ;;
;;;;;;;;;;;;;;;;;;;;;;

(defn trace-fn-call [fn-call-data]
  (let [curr-thread-id (utils/get-current-thread-id)]
    (push-thread-frame context curr-thread-id fn-call-data)))

(defn collect-fn-call [{:keys [collected-fns]} fn-call]
  (let [add-fn (fn add-fn [fns-map {:keys [ns fn-name fn-args return]}]
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
                     call-example (update-in [fq-fn-symb :call-examples] (fnil conj #{}) call-example))))]
    (swap! collected-fns add-fn fn-call)))

(defn trace-fn-return [{:keys [return]}]
  (let [curr-thread-id (utils/get-current-thread-id)
        {:keys [unnamed-fn?] :as frame-data} (peek-thread-frame context curr-thread-id)]

    (when (and (not unnamed-fn?)
               (not *tracing*))
      (binding [*tracing* true]
        (collect-fn-call context (assoc frame-data :return return))))

    (update-stats context frame-data)

    (pop-thread-frame context curr-thread-id))
  return)

;; -----------------------------------------------------------

(defn- symb-var-meta [vsymb]
  (when vsymb
    (let [v (find-var vsymb)]
      (-> (meta v)
          (select-keys [:added :ns :name :file :static :column :line :arglists :doc])
          (update :ns (fn [ns] (when ns (ns-name ns))))
          (update :arglists str)))))

(defn add-vars-meta [fns-map]
  (let [total-cnt (count fns-map)]

    (utils/log (format "Adding vars meta for a fns-map of size %d" total-cnt))

    (reduce-kv (fn [r fsymb fdata]
                 (let [data (assoc fdata :var-meta (symb-var-meta fsymb))]
                   (assoc r fsymb data)))
               {}
               fns-map)))

(defn serialize-call-examples [{:keys [examples-pprint? examples-print-length examples-print-level]} collected-fns]
  (let [total-cnt (count collected-fns)
        ser-cfg {:examples-print-fn (if examples-pprint? pp/pprint print)
                 :examples-print-length (or examples-print-length 1)
                 :examples-print-level (or examples-print-level 2)}]

    (utils/log (format "Processing call examples for %d collected fns. Serializing values ..." total-cnt))

    (utils/update-values
     collected-fns
     (fn [data]
       (update data :call-examples
               (fn [ce]
                 (->> ce
                      (map (fn [ex]
                             (-> ex
                                 (update :args #(mapv (partial serialize-val ser-cfg) %))
                                 (update :ret #(serialize-val ser-cfg %))))))))))))

(defn save-result

  "Save the resulting sampled FNS-MAP into a samples.edn file and wrap it in a jar with RESULT-NAME"

  [fns-map {:keys [result-name]}]
  (utils/log "Saving result ...")

  (let [tmp-dir (.getAbsolutePath (utils/mk-tmp-dir!))
        result-file-str (pr-str fns-map)
        result-file-path (str tmp-dir File/separator "samples.edn")]

    (utils/log (format "Saving results in %s" result-file-path))
    (spit result-file-path result-file-str)

    (utils/log (str "Wrote " result-file-path " creating jar file."))
    (tools.build/jar {:class-dir tmp-dir
                      :jar-file (str result-name ".jar")})
    (utils/log "Jar file created.")))

(defmacro sample

  "Expands into code for sampling the executions of FORMS. "

  [{:keys [uninstrument? inst-ns-prefixes print-unsampled?]
    :as config} & forms]

  `(let [stats# (atom (empty-stats))
         ctx# {:collected-fns (atom {})
               :threads-stacks (index-utils/make-mutable-hashmap)
               :stats stats#}]

     (utils/log "Instrumentation done. Running forms ...")

     (alter-var-root #'context (constantly ctx#))

     (let [inst-result# (hansel/instrument-namespaces-clj
                         ~inst-ns-prefixes
                         (merge
                          ~config
                          {:trace-fn-call trace-fn-call
                           :trace-fn-return trace-fn-return
                           :disable #{:anonymous-fn}}))]

       (set-stats-inst-fns context inst-result#)

       ~@forms

       (let [fns-map# (serialize-call-examples ~config @(:collected-fns ctx#))
             unsampled-fns# (set/difference (:inst-fns @stats#) (:sampled-fns @stats#))]
         (report-stats @stats#)

         (save-result (add-vars-meta fns-map#) ~config)

         (utils/log "Results saved.")

         (when ~print-unsampled?

           (utils/log "Unsampled functions :")

           (doseq [uf# (sort unsampled-fns#)]
             (utils/log uf#)))

         (when ~uninstrument?
           (utils/log "Uninstrumenting...")
           (hansel/uninstrument-namespaces-clj ~inst-ns-prefixes))

         (utils/log "All done!")

         {:unsampled-fns unsampled-fns#}))))
