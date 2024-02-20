(ns flow-storm.runtime.indexes.timelines.soft-batched-store
  (:require [clojure.test :refer [deftest is]]
            [clojure.math :as math]
            [clojure.core.protocols :as core.protocols]
            [clojure.datafy :as d])
  (:import [java.lang.ref SoftReference]
           [java.util ArrayList]))

(set! *warn-on-reflection* true)

(defn- log2 ^long [^long n]
  (long
   (quot (math/log n)
         (math/log 2))))

(defprotocol SoftBatchedStoreP
  (append-obj [_ o] "Append a object and return the key"))

(deftype SoftBatchedStore
    [^ArrayList                          soft-batches    ;; ArrayList<SoftReference> to batches (ArrayList)
     ^:unsynchronized-mutable ^objects   building-batch  ;; Object[]
     ^:unsynchronized-mutable ^long      batch-sub-idx
     ^long                               batch-bits-cnt
     ^long                               batch-sub-idx-mask
     ^long                               batch-size
     ]

  ;; 63 bits total storage
  ;; Idx 63 - batch-size-bits
  ;; S = batch Sub index
  ;; B = Batch index
  ;;
  ;; For batch size 255 (8 bits)
  ;;
  ;; BBB BBBB BBBB BBBB BBBB BBBB BBBB BBBB BBBB BBBB BBBB BBBB BBBB BBBB SSSS SSSS
  ;; ^__________________________________________________________________^ ^_______^
  ;;                              55 bits                                   8 bits  = 63 bits

  SoftBatchedStoreP

  (append-obj [this o]
    (locking this

      ;; when the building-batch is full, add a new batch and move
      ;; the building to soft-batches
      (when (> (inc batch-sub-idx) batch-size)
        (.add soft-batches (SoftReference. building-batch))
        (set! building-batch (object-array batch-size))
        (set! batch-sub-idx 0))

      ;; add the o to the building-batch
      (aset building-batch batch-sub-idx o)
      (set! batch-sub-idx (inc batch-sub-idx))
      this))

  clojure.lang.ILookup

  (valAt [this idx]
    (locking this
      (let [req-batch-sub-idx (bit-and idx batch-sub-idx-mask)
            req-batch (bit-shift-right idx batch-bits-cnt)]

        ;; if the requested idx is on the building-batch
        (if (= (.size soft-batches) req-batch)
          (aget building-batch req-batch-sub-idx)

          ;; else let's look it in the soft-batches
          (let [^SoftReference sb-ref (.get soft-batches req-batch)]
            (if-let [^objects soft-batch-arr (.get sb-ref)]
              (aget soft-batch-arr req-batch-sub-idx)
              ::cleared))))))

  (valAt [this idx not-found]
    (let [o (.valAt this idx)]
      (when (= ::cleared o)
        not-found)))

  clojure.lang.Counted
  (count
    [this]
    (locking this
      (+ (* batch-size (.size soft-batches))
         batch-sub-idx)))

  core.protocols/Datafiable

  (datafy [_]
    {:soft-batches (mapv (fn [^SoftReference sb-ref]
                           (if-let [soft-batch (.get sb-ref)]
                             (into [] soft-batch)
                             ::cleared) )
                         soft-batches)
     :building-batch (into [] building-batch)
     :batch-sub-idx batch-sub-idx}))

(defn make-soft-batched-store

  "Provided `batch-size` should be a power of 2"

  [batch-size]

  (let [batch-bits-cnt (log2 batch-size)]
    (SoftBatchedStore. (ArrayList.)
                       (object-array batch-size)
                       0
                       batch-bits-cnt
                       (dec (bit-shift-left 1 batch-bits-cnt))
                       batch-size)))


(deftest building-batch-test
  (let [^SoftBatchedStore sbs (make-soft-batched-store 256)]
    (-> sbs
        (append-obj :obj0)
        (append-obj :obj1)
        (append-obj :obj2))
    (is (= :obj0 (get sbs 0)))
    (is (= :obj1 (get sbs 1)))
    (is (= :obj2 (get sbs 2)))
    (is (= 3 (count sbs)))))

(deftest soft-batches-test
  (let [^SoftBatchedStore sbs (make-soft-batched-store 256)
        _ (doseq [i (range 260)]
            (append-obj sbs (keyword (str "obj" i))))]
    (is (= 260 (count sbs)))
    (is (= :obj255 (get sbs 255)))
    (is (= :obj256 (get sbs 256)))

    (let [{:keys [soft-batches batch-sub-idx]} (d/datafy sbs)]
      (is (= 1   (count soft-batches)))
      (is (= 256 (count (first soft-batches))))
      (is (= 4   batch-sub-idx)))))

(comment
  (float (/ (* 24 (math/pow 2 20)) 1024 1024)) ;; ~ 380 Mb batches

  (def sbs (make-soft-batched-store (math/pow 2 20)))

  (def iters
    (let [gen-size-mb 4096
          ent-size-bs 24]
      (int (/ (* gen-size-mb 1024 1024) ent-size-bs))))

  (loop [i 0]
    (when (< i iters)
      (append-obj sbs i)
      (recur (inc i))))

  (loop [i 0
         cleared 0
         recorded 0]
    (if (< i iters)
      (let [o (get sbs i)]
        (cond
          (= o ::cleared) (recur (inc i) (inc cleared) recorded)
          (number? o)     (recur (inc i) cleared (inc recorded))
          :else (throw (ex-info "We shouldn't be here" {}))))

      {:cleared [cleared (float (/ cleared iters))]
       :recorded [recorded (float (/ recorded iters))]}))




  )
