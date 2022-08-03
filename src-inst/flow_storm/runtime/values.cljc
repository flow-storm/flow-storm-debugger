(ns flow-storm.runtime.values
  (:require [flow-storm.utils :as utils]
            [flow-storm.value-types :refer [->LocalImmValue ->RemoteImmValue]]
            [clojure.pprint :as pp]
            [clojure.datafy :as datafy]))

(defn snapshot-reference [x]  
  (if #?(:clj  (instance? clojure.lang.IDeref x)
         :cljs (instance? cljs.core.IDeref x))
    {:ref/snapshot (deref x)
     :ref/type (type x)}
    x))

(def *values-references (atom {}))

(defn get-reference-value [vid]
  (get @*values-references vid))

(defn reference-value! [v]
  (let [vid (utils/rnd-uuid)]
    (swap! *values-references assoc vid v)
    vid))

(defn make-local-immutable-value [v]
  (->LocalImmValue (snapshot-reference v)))

(defn make-remote-immutable-value [v]
  (->RemoteImmValue (snapshot-reference v)))

(defn wrap-trace-values! [{:keys [trace/type] :as trace} remote?]
  (let [wrap-val (if remote?
                   (comp make-remote-immutable-value reference-value!)
                   make-local-immutable-value)]
    (case type
      :flow-init trace
      :form-init (update trace :mm-dispatch-val wrap-val)
      :expr-exec (update trace :result wrap-val)
      :fn-call   (update trace :args-vec wrap-val)
      :bind      (update trace :value wrap-val))))

(defn val-pprint [val {:keys [print-length print-level print-meta? pprint? nth-elems]}]
  (let [print-fn #?(:clj (if pprint? pp/pprint print) 
                    :cljs (if (and pprint? (not print-meta?)) pp/pprint print))] ;; ClojureScript pprint doesn't support *print-meta*
    (with-out-str
      (binding [*print-level* print-level
                *print-length* print-length
                *print-meta* print-meta?]

        (if nth-elems

          (let [max-idx (dec (count val))
                nth-valid-elems (filter #(<= % max-idx) nth-elems)]
            (doseq [n nth-valid-elems]
              (print-fn (nth val n))
              (print " ")))

          (print-fn val))))))

(defn- make-value [x remote?]
  (if remote?
    (make-remote-immutable-value (reference-value! x))
    (make-local-immutable-value x)))

(defn- maybe-ref! [x remote?]
  (if (or (string? x)
          (number? x)
          (keyword? x)
          (symbol? x))
    x

    (make-value x remote?)))

(defn- build-shallow-map [data remote?]
  (let [entries (->> (into {} data)
                     (mapv (fn [[k v]]
                             [(maybe-ref! k remote?) (maybe-ref! v remote?)])))]
    {:val/kind :map
     :val/map-entries entries}))

(defn- build-shallow-seq [data remote?]  
  (let [page-size 50
        cnt (when (counted? data) (count data))
        shallow-page (->> data
                          (map #(maybe-ref! % remote?))
                          (take page-size)
                          doall)
        shallow-page-cnt (count shallow-page)
        more-elems (drop shallow-page-cnt data)]
    (cond-> {:val/kind :seq
             :val/page shallow-page             
             :total-count cnt}
      (seq more-elems) (assoc :val/more (maybe-ref! more-elems remote?)))))

#?(:clj (defn map-like? [x] (instance? java.util.Map x)))
#?(:cljs (defn map-like? [_] false))

#?(:clj (defn seq-like? [x] (instance? java.util.List x)))
#?(:cljs (defn seq-like? [_] false))

(defn shallow-val [v remote?]
  (let [data (datafy/datafy v)
        type-name (pr-str (type v))
        shallow-data (cond
                       (map-like? data)
                       (build-shallow-map data remote?)

                       (or (coll? data) (seq-like? data))
                       (build-shallow-seq data remote?)

                       :else {:val/kind :simple
                              :val/str (pr-str v)})]
    (assoc shallow-data
           :val/type type-name
           :val/full (make-value v remote?))))
