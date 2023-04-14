(ns flow-storm.runtime.values
  (:require [clojure.pprint :as pp]
            [clojure.datafy :as datafy]
            [flow-storm.utils :as utils]
            #?(:cljs [goog.object :as gobj])))

(def values-references (atom nil))

(defprotocol PValueRef
  (ref-value [_ v])
  (get-value [_ vid])
  (get-value-id [_ v]))

(defn maybe-wrapped [o]
  (cond-> o
    (not (counted? o)) utils/wrap))

;; This is tricky.
;; All this stuff in ValuesReferences is so we can assign unique stable ids to objects
;; First I tried directly with utils/object-id but on the JVM it is using System/identityHashCode which isn't
;; reliable since it is pretty easy to find collisions.
;; The current approach is to keep two maps vid->v, v->vid and an incrementing id value.
;; Also if the value we are about to store isn't counted? we need to wrap it, since we can only have hashable values
;; as keys of a map, which infinite sequences are not.

(defrecord ValuesReferences [vid->v v->vid max-vid]
  PValueRef

  (ref-value [this v]
    (let [v (maybe-wrapped v)]
      (if (contains? v->vid v)
        this
        (let [next-vid (inc max-vid)]
          (-> this
              (assoc :max-vid next-vid)
              (update :vid->v assoc next-vid v)
              (update :v->vid assoc v next-vid))))))
  
  (get-value [_ vid]
    (let [maybe-wrapped-v (get vid->v vid)]
      (if (utils/wrapped? maybe-wrapped-v)
        (utils/unwrap maybe-wrapped-v)
        maybe-wrapped-v)))

  (get-value-id [_ v]
    (let [v (maybe-wrapped v)]
      (get v->vid v))))

(defn get-reference-value [vid]  
  (get-value @values-references vid))

(defn reference-value! [v]
  (try
    (swap! values-references ref-value v)
    (get-value-id @values-references v)
    (catch Exception e
      ;; if for whatever reason we can't reference the value
      ;; let's be explicit so at least the user knows that 
      ;; something went wrong and the value can't be trusted.
      ;; I have seen a issues of hashing failing for a lazy sequence
      (reference-value! :flow-storm/error-referencing-value))))

(defn clear-values-references []
  (reset! values-references (map->ValuesReferences {:vid->v {} :v->vid {} :max-vid 0})))

(defmulti snapshot-value type)

(defmethod snapshot-value :default [v] v)

(defn snapshot-reference [x]  
  (cond

    (and (utils/blocking-derefable? x)
         (utils/pending? x))
    (merge
     {:ref/type (type x)}
     (if (realized? x)
       {:ref/snapshot (deref x)}
       {:ref/timeout x}))

    (utils/derefable? x)
    {:ref/snapshot (deref x)
     :ref/type (type x)}

    :else (snapshot-value x)))

(defn val-pprint [val {:keys [print-length print-level print-meta? pprint? nth-elems]}]  
  (let [print-fn #?(:clj (if pprint? pp/pprint print) 
                    :cljs (if (and pprint? (not print-meta?)) pp/pprint print))] ;; ClojureScript pprint doesn't support *print-meta*

    (try
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

           (print-fn val))))
      (catch Exception e
        ;; return somthing so the user knows the value can't be trusted
        "Flow-storm error, value couldn't be pprinted"))))

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
  (let [{:keys [page/offset] :or {offset 0}} (meta data)
        page-size 50
        cnt (when (counted? data) (count data))
        shallow-page (->> data
                          (map #(maybe-dig-node! %))
                          (take page-size)
                          doall)
        shallow-page-cnt (count shallow-page)
        more-elems (drop shallow-page-cnt data)]
    (cond-> {:val/kind :seq
             :val/page shallow-page
             :page/offset offset
             :total-count cnt}
      (seq more-elems) (assoc :val/more (reference-value! (with-meta more-elems {:page/offset (+ offset shallow-page-cnt)}))))))

(defn shallow-val [v]
  (let [v-meta (meta v)
        data (cond-> (datafy/datafy v) ;; forward meta since we are storing meta like :page/offset in references
               v-meta (with-meta v-meta))
        type-name (pr-str (type v))
        shallow-data (cond
                       (utils/map-like? data)
                       (build-shallow-map data)

                       (or (coll? data) (utils/seq-like? data))
                       (build-shallow-seq data)

                       :else {:val/kind :simple
                              :val/str (pr-str v)})]
    (assoc shallow-data
           :val/type type-name
           :val/full (reference-value! v))))

(defn tap-value [vref]
  (let [v (get-reference-value vref)]
    (tap> v)))

#?(:clj
   (defn def-value [val-name vref]
     (intern 'user (symbol val-name) (get-reference-value vref)))

   :cljs
   (defn def-value [val-name vref]
     (gobj/set (if (= *target* "nodejs") js/global js/window)
               val-name
               (get-reference-value vref))))
