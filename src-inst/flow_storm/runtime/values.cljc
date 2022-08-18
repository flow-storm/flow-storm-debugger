(ns flow-storm.runtime.values
  (:require [flow-storm.utils :as utils]            
            [clojure.pprint :as pp]
            [clojure.datafy :as datafy]
            #?(:cljs [goog.object :as gobj])))

(defn snapshot-reference [x]  
  (if #?(:clj  (instance? clojure.lang.IDeref x)
         :cljs (instance? cljs.core.IDeref x))
    {:ref/snapshot (deref x)
     :ref/type (type x)}
    x))

(def *values-references

  "Reified pointers. A map from val-id -> val "
  
  (atom {}))

(defn get-reference-value [vid]
  (get @*values-references vid))

(defn reference-value! [v]  
  (when v
    (let [vid (str (utils/rnd-uuid))]
      (swap! *values-references assoc vid v)
      vid)))

(defn clear-values-references []
  (reset! *values-references {}))

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

(defn- maybe-dig-node! [x]
  (if (or (string? x)
          (number? x)
          (keyword? x)
          (symbol? x))
    
    x

    [:val/dig-node (reference-value! x)]))

(defn- build-shallow-map [data]
  (let [entries (->> (into {} data)
                     (mapv (fn [[k v]]
                             [(maybe-dig-node! k) (maybe-dig-node! v)])))]
    {:val/kind :map
     :val/map-entries entries}))

(defn- build-shallow-seq [data]  
  (let [page-size 50
        cnt (when (counted? data) (count data))
        shallow-page (->> data
                          (map #(maybe-dig-node! %))
                          (take page-size)
                          doall)
        shallow-page-cnt (count shallow-page)
        more-elems (drop shallow-page-cnt data)]
    (cond-> {:val/kind :seq
             :val/page shallow-page             
             :total-count cnt}
      (seq more-elems) (assoc :val/more (maybe-dig-node! more-elems)))))

#?(:clj (defn map-like? [x] (instance? java.util.Map x)))
#?(:cljs (defn map-like? [x] (map? x)))

#?(:clj (defn seq-like? [x] (instance? java.util.List x)))
#?(:cljs (defn seq-like? [_] false))

(defn shallow-val [v]
  (let [data (datafy/datafy v)
        type-name (pr-str (type v))
        shallow-data (cond
                       (map-like? data)
                       (build-shallow-map data)

                       (or (coll? data) (seq-like? data))
                       (build-shallow-seq data)

                       :else {:val/kind :simple
                              :val/str (pr-str v)})]
    (assoc shallow-data
           :val/type type-name
           :val/full (reference-value! v))))

#?(:clj
   (defn def-val-reference [val-name vref]
     (intern 'user (symbol val-name) (get-reference-value vref)))

   :cljs
   (defn def-val-reference [val-name vref]
     (gobj/set (if (= *target* "nodejs") js/global js/window)
               val-name
               (get-reference-value vref))))
