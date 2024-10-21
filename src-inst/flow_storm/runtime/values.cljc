(ns flow-storm.runtime.values
  (:require [clojure.pprint :as pp]
            [flow-storm.utils :as utils]
            [flow-storm.types :as types]
            [clojure.datafy :refer [datafy nav]]
            #?(:clj [clojure.string :as str])))

(defprotocol PWrapped
  (unwrap [_]))

(deftype HashableObjWrap [obj]
  #?@(:clj
      [clojure.lang.IHashEq
       (hasheq [_] (utils/object-id obj))
       (hashCode [_] (utils/object-id obj))
       (equals [_ that]
               (if (instance? HashableObjWrap that)
                 (identical? obj (unwrap that))
                 false))]

      :cljs
      [IEquiv
       (-equiv [_ that]
               (if (instance? HashableObjWrap that)
                 (identical? obj (unwrap that))
                 false))

       IHash
       (-hash [_]
              (utils/object-id obj))])
  
  PWrapped
  (unwrap [_]
    obj))

(defn hashable-obj-wrap [o]
  (->HashableObjWrap o))

(defn hashable-obj-wrapped? [o]
  (instance? HashableObjWrap o))

(defprotocol PValueRefRegistry
  (add-val-ref [_ v])
  (get-value [_ vref])
  (get-value-ref [_ v]))

;; Fast way of going from
;;    value-ref -> value 
;;    value     -> value-ref
;;
;; every object gets wrapped into a HashableObjWrap that will have
;; hashCode based on unique object-id, calculated by `utils/object-id`
;; This is so ^:a [] and ^:b [] get a different value-ref, which won't get unless
;; we wrap them, since both will have the same hashCode because meta isn't used for hashCode calculation
;; Wrapping is also useful for infinite sequences, since you can't put a val as a key of a hash-map if it
;; is a infinite seq
(defrecord ValueRefRegistry [vref->wv wv->vref max-vid]
  PValueRefRegistry

  (add-val-ref [this v]
    (let [wv (hashable-obj-wrap v)]
      (if (contains? wv->vref wv)
        this
        (let [next-vid (inc max-vid)
              vref (types/make-value-ref next-vid)]
          (-> this
              (assoc :max-vid next-vid)
              (update :vref->wv assoc vref wv)
              (update :wv->vref assoc wv vref))))))
  
  (get-value [_ vref]
    (unwrap (get vref->wv vref)))

  (get-value-ref [_ v]
    (let [wv (hashable-obj-wrap v)]
      (get wv->vref wv))))

(def init-ref-registry (map->ValueRefRegistry {:vref->wv {} :wv->vref {} :max-vid 0}))

(defonce values-ref-registry (atom init-ref-registry))

(defn deref-value [vref]
  (if (types/value-ref? vref)
    (get-value @values-ref-registry vref)

    ;; if vref is not a ref, assume it is a value and just return it
    vref))

(defn deref-val-id [vid]
  (deref-value (types/make-value-ref vid)))

(defn reference-value! [v]
  (try
    
    (swap! values-ref-registry add-val-ref v)
    (-> (get-value-ref @values-ref-registry v)
        (types/add-val-preview v))
    
    ;; if for whatever reason we can't reference the value
    ;; let's be explicit so at least the user knows that 
    ;; something went wrong and the value can't be trusted.
    ;; I have seen a issues of hashing failing for a lazy sequence
    #?(:clj (catch Exception e
              (utils/log-error "Error referencing value" e)    
              (reference-value! :flow-storm/error-referencing-value))
       :cljs (catch js/Error e
               (utils/log-error "Error referencing value" e)    
               (reference-value! :flow-storm/error-referencing-value)))))

(defn clear-vals-ref-registry []
  (reset! values-ref-registry init-ref-registry))

(defprotocol SnapshotP
  (snapshot-value [_]))

(extend-protocol SnapshotP
  #?(:clj Object :cljs default)
  (snapshot-value [v] v))

(extend-protocol SnapshotP
  nil
  (snapshot-value [_] nil))

(extend-protocol SnapshotP
  #?(:clj clojure.lang.Atom :cljs cljs.core/Atom)
  (snapshot-value [a]
    {:ref/snapshot (deref a) :ref/type (type a)}))

#?(:clj
   (extend-protocol SnapshotP
     clojure.lang.Agent
     (snapshot-value [a]
       {:ref/snapshot (deref a) :ref/type (type a)})))

#?(:clj
   (extend-protocol SnapshotP
     clojure.lang.Ref
     (snapshot-value [r]
       {:ref/snapshot (deref r) :ref/type (type r)})))

#?(:clj
   (extend-protocol SnapshotP
     clojure.lang.Var
     (snapshot-value [v]
       {:ref/snapshot (deref v) :ref/type (type v)})))

(defn snapshot-reference [x]
  (if (and (utils/derefable? x) (utils/pending? x) (realized? x))
    ;; If the value is already realized it should be safe to call deref.
    ;; If it is a non realized pending derefable we don't mess with it
    ;; because we don't want to interfere with the program behavior, since
    ;; people can do side effectful things on deref, like in the case of delay
    ;; This will cover basically realized promises, futures and delays
    {:ref/snapshot (deref x) :ref/type (type x)}

    (snapshot-value x)))

(defn value-type [v]
  (if (and (map? v)
           (try ;; the try/catch is for things like sorted-map of symbol keys
             (contains? v :ref/type)
             (catch  #?(:clj Exception :cljs js/Error) _e nil)))
    (pr-str (:ref/type v))
    (pr-str (type v))))

(defn val-pprint [val {:keys [print-length print-level print-meta? pprint? nth-elems]}]  
  (let [val-type (value-type val)
        print-fn #?(:clj (if pprint? pp/pprint print) 
                    :cljs (if (and pprint? (not print-meta?)) pp/pprint print)) ;; ClojureScript pprint doesn't support *print-meta*
        val-str (try
                  
                  (if (and (utils/blocking-derefable? val)
                           (utils/pending? val))

                    "FlowStorm : Unrealized value"

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

                          (print-fn val)))))

                  ;; return somthing so the user knows the value can't be trusted
                  #?(:clj (catch Exception e
                            (utils/log-error "Error pprinting value" e)
                            "Flow-storm error, value couldn't be pprinted")
                     :cljs (catch js/Error e
                             (utils/log-error "Error pprinting value" e)
                             "Flow-storm error, value couldn't be pprinted")))]
    {:val-str val-str
     :val-type val-type}))

(defn val-pprint-ref [vref opts]  
  (let [val (deref-value vref)]
    (val-pprint val opts)))

(defn tap-value [vref]
  (let [v (deref-value vref)]
    (tap> v)))

#?(:clj
   (defn def-value [var-ns var-name x]     
     (intern (symbol var-ns)
             (symbol var-name)
             (deref-value x))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce values-datafiers-registry (atom {}))

(defn register-data-aspect-extractor [{:keys [id] :as extractor}]
  (swap! values-datafiers-registry assoc id extractor))

(defn unregister-data-aspect-extractor [id]
  (swap! values-datafiers-registry dissoc id))

(defn short-preview [v]
  (:val-str (val-pprint v {:pprint? false :print-length 3 :print-level 3 :print-meta? false})))

(defn interesting-nav-reference [coll k]
  (let [v (get coll k)
        n (nav coll k v)]    
    (when (or (not= n v)
               (not= (meta n) (meta v)))
      (reference-value! n))))

(defn extract-data-aspects [o]
  (let [dat-o (datafy o)
        o-meta (meta o)]
    (reduce (fn [aspects {:keys [id pred extractor]}]
              (if (pred dat-o)
                (let [ext (extractor dat-o)]
                  (-> ext
                      (merge aspects)
                      (update ::kinds conj id)))
                aspects))
            (cond-> {::kinds #{}
                     ::type (pr-str (type o))
                     ::val-ref (reference-value! o)}
              o-meta (assoc ::meta-ref (reference-value! o-meta)
                            ::meta-preview (short-preview o-meta)))
            (vals @values-datafiers-registry))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data aspect extractors ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(register-data-aspect-extractor
 {:id :number
  :pred number?
  :extractor (fn [n]               
               {:number/val n})})

(register-data-aspect-extractor
 {:id :int
  :pred int?
  :extractor (fn [n]               
               {:int/decimal n
                :int/binary (utils/format-int n 2)
                :int/octal  (utils/format-int n 8)
                :int/hex    (utils/format-int n 16)})})

  
(register-data-aspect-extractor
 {:id :previewable
  :pred any?
  :extractor (fn [o]
               {:preview/pprint
                (-> o
                    (val-pprint {:pprint? true
                                 :print-length 10
                                 :print-level 10
                                 :print-meta? false})
                    :val-str)})})

(register-data-aspect-extractor
 {:id :shallow-map
  :pred map?
  :extractor (fn [m]
               (let [m-keys (keys m)
                     m-vals (vals m)]
                 {:shallow-map/keys-previews (mapv short-preview m-keys)
                  :shallow-map/keys-refs     (mapv reference-value! m-keys)
                  :shallow-map/navs-refs     (mapv (partial interesting-nav-reference m) m-keys)
                  :shallow-map/vals-previews (mapv short-preview m-vals)
                  :shallow-map/vals-refs     (mapv reference-value! m-vals)}))})

(register-data-aspect-extractor
 {:id :paged-shallow-seqable
  :pred seqable?
  :extractor (fn [s]
               (let [xs (seq s)
                     page-size 100
                     last-page? (< (bounded-count page-size xs) page-size)
                     page (take page-size xs)]
                 (cond-> {:paged-seq/count (when (counted? s) (count s))
                          :paged-seq/page-size page-size
                          :paged-seq/page-previews (mapv short-preview page)
                          :paged-seq/page-refs (mapv reference-value! page)}
                   (not last-page?) (assoc :paged-seq/next-ref (reference-value! (drop page-size xs))))))})

(register-data-aspect-extractor
 {:id :shallow-indexed
  :pred indexed?
  :extractor (fn [idx-coll]
               {:shallow-idx-coll/count (count idx-coll)
                :shallow-idx-coll/vals-previews (mapv short-preview idx-coll)
                :shallow-idx-coll/vals-refs (mapv reference-value! idx-coll)
                :shallow-idx-coll/navs-refs (mapv (partial interesting-nav-reference idx-coll) (range (count idx-coll)))})})


#?(:clj
   (register-data-aspect-extractor
    {:id :byte-array
     :pred bytes?
     :extractor (fn [bs]
                  (let [max-cnt 1000
                        
                        format-and-pad (fn [b radix]
                                         (let [s (-> ^byte b
                                                     Byte/toUnsignedInt
                                                     (utils/format-int radix))
                                               s-padded (cond 
                                                          (= radix 2)  (format "%8s" s)
                                                          (= radix 16) (format "%2s" s))]
                                           
                                           (str/replace s-padded " " "0")))]
                    (if (<= (count bs) max-cnt)
                      {:bytes/hex    (mapv #(format-and-pad % 16) bs)
                       :bytes/binary (mapv #(format-and-pad % 2) bs)
                       :bytes/full? true}

                      (let [head (take (/ max-cnt 2) bs)
                            tail (drop (- (count bs) (/ max-cnt 2)) bs)]
                        {:bytes/head-hex    (mapv #(format-and-pad % 16) head)
                         :bytes/head-binary (mapv #(format-and-pad % 2)  head)
                         :bytes/tail-hex    (mapv #(format-and-pad % 16) tail)
                         :bytes/tail-binary (mapv #(format-and-pad % 2)  tail)}))))}))

(comment
    
  (extract-data-aspects 120)
  (extract-data-aspects {:a 20 :b 40})
  )
