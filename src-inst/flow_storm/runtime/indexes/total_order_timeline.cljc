(ns flow-storm.runtime.indexes.total-order-timeline
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.timeline-index :refer [ensure-indexes]]
            [flow-storm.runtime.indexes.utils :refer [make-mutable-list ml-add ml-clear ml-count ml-get]]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace :refer [fn-end-trace?]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace :refer [fn-call-trace?]]
            [flow-storm.runtime.types.expr-trace :as expr-trace :refer [expr-trace?]]
            [hansel.utils :as hansel-utils]
            [flow-storm.utils :as utils]
            #?(:clj [clojure.core.protocols :as cp])))

(defn- print-tote [tote]
  (let [th-tl (index-protos/tote-thread-timeline tote)
        th-idx (index-protos/tote-thread-timeline-idx tote)
        th-id (index-protos/thread-id th-tl th-idx)]
    (utils/format "#flow-storm/total-order-timeline-entry [ThreadId: %d, Idx: %d]"
                  th-id th-idx)))

(deftype TotalOrderTimelineEntry [th-timeline th-idx]

  index-protos/ImmutableP
  (as-immutable [_]
    (-> (get th-timeline th-idx)
        index-protos/as-immutable
        (ensure-indexes th-idx)
        (assoc :thread-id (index-protos/thread-id th-timeline 0))))
  
  index-protos/TotalOrderTimelineEntryP
  
  (tote-thread-timeline [_] th-timeline)
  (tote-thread-timeline-idx [_] th-idx)

  #?@(:cljs
      [IPrintWithWriter
       (-pr-writer [this writer _]
                   (write-all writer (print-tote this)))]))

(defn total-order-timeline-entry? [x]
  (instance? TotalOrderTimelineEntry x))

#?(:clj
   (defmethod print-method TotalOrderTimelineEntry [tote ^java.io.Writer w]
     (.write w ^String (print-tote tote))))

(deftype TotalOrderTimeline [flow-id mt-timeline]

  index-protos/TimelineP

  (flow-id [_]
    flow-id)
  
  (thread-id [this idx]
    (locking this
      (let [tote (ml-get mt-timeline idx)
            th-tl (index-protos/tote-thread-timeline tote)]
        (index-protos/thread-id th-tl 0))))
  
  index-protos/TotalOrderTimelineP

  (tot-add-entry [this th-timeline th-idx]    
    (locking this
      (ml-add mt-timeline (TotalOrderTimelineEntry. th-timeline th-idx))))
  
  (tot-clear-all [this]
    (locking this
      (ml-clear mt-timeline)))
  
  #?@(:clj
      [clojure.lang.Counted       
       (count
        [this]
        (locking this
          (ml-count mt-timeline)))

       clojure.lang.Seqable
       (seq
        [this]
        (locking this
          (doall (seq mt-timeline))))       

       cp/CollReduce
       (coll-reduce
        [this f]        
        (locking this
          (cp/coll-reduce mt-timeline f)))
       
       (coll-reduce
        [this f v]        
        (locking this
          (cp/coll-reduce mt-timeline f v)))

       clojure.lang.ILookup
       (valAt [this k] (locking this (ml-get mt-timeline k)))
       (valAt [this k not-found] (locking this (or (ml-get mt-timeline k) not-found)))

       clojure.lang.Indexed
       (nth [this k] (locking this (ml-get mt-timeline k)))
       (nth [this k not-found] (locking this (or (ml-get mt-timeline k) not-found)))]

      :cljs
      [ICounted
       (-count [_] (ml-count mt-timeline))

       ISeqable
       (-seq [_] (seq mt-timeline))
       
       IReduce
       (-reduce [_ f]
                (reduce f mt-timeline))
       (-reduce [_ f start]
                (reduce f start mt-timeline))

       ILookup
       (-lookup [_ k] (ml-get mt-timeline k))
       (-lookup [_ k not-found] (or (ml-get mt-timeline k) not-found))

       IIndexed
       (-nth [_ n] (ml-get mt-timeline n))
       (-nth [_ n not-found] (or (ml-get mt-timeline n) not-found))]))

(defn make-total-order-timeline [flow-id]
  (TotalOrderTimeline. flow-id (make-mutable-list)))

(defn make-detailed-timeline-mapper [forms-registry]
  (let [threads-stacks (atom {})]
    (fn [thread-id te-idx entry]
      (cond
        (fn-call-trace? entry)
        (do
          (swap! threads-stacks update thread-id conj entry)
          {:type                :fn-call
           :thread-id           thread-id
           :thread-timeline-idx te-idx
           :fn-ns               (index-protos/get-fn-ns entry)
           :fn-name             (index-protos/get-fn-name entry)})
        
        (fn-end-trace? entry)
        (do
          (swap! threads-stacks update thread-id pop)
          {:type                (if (fn-return-trace/fn-return-trace? entry)
                                  :fn-return
                                  :fn-unwind)
           :thread-id           thread-id
           :thread-timeline-idx te-idx})
        
        (expr-trace? entry)
        (let [[curr-fn-call] (get @threads-stacks thread-id)
              form-id (index-protos/get-form-id curr-fn-call)
              form-data (index-protos/get-form forms-registry form-id)
              coord (index-protos/get-coord-vec entry)
              expr-val (index-protos/get-expr-val entry)]
          
          {:type                :expr-exec
           :thread-id           thread-id
           :thread-timeline-idx te-idx
           :expr-str            (binding [*print-length* 5
                                          *print-level*  3]
                                  (pr-str
                                   (hansel-utils/get-form-at-coord (:form/form form-data)
                                                                   coord)))
           :expr-type (pr-str (type expr-val))
           :expr-val-str  (binding [*print-length* 3
                                    *print-level*  2]
                            (pr-str expr-val))})))))
