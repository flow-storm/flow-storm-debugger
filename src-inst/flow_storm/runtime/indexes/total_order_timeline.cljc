(ns flow-storm.runtime.indexes.total-order-timeline
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [make-mutable-list ml-add ml-clear ml-count ml-get]]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace :refer [fn-end-trace?]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace :refer [fn-call-trace?]]
            [flow-storm.runtime.types.expr-trace :as expr-trace :refer [expr-trace?]]
            [hansel.utils :as hansel-utils]
            #?(:clj [clojure.core.protocols :as cp])))

(deftype TotalOrderTimelineEntry [th-timeline entry]

  index-protos/ImmutableP
  (as-immutable [_]
    (merge {:thread-id (index-protos/thread-id th-timeline)}
           (index-protos/as-immutable entry)))
  
  index-protos/TotalOrderTimelineEntryP
  
  (tote-thread-timeline [_] th-timeline)
  (tote-thread-timeline-entry [_] entry)

  )

(deftype TotalOrderTimeline [mt-timeline]

  index-protos/TimelineP
  
  (thread-id [_] :multi-thread)
  
  index-protos/TotalOrderTimelineP

  (tot-add-entry [this th-timeline entry]    
    (locking this
      (ml-add mt-timeline (TotalOrderTimelineEntry. th-timeline entry))))
  
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

(defn make-total-order-timeline []
  (TotalOrderTimeline. (make-mutable-list)))

(defn detailed-timeline-transd [forms-registry]
  (let [threads-stacks (atom {})]
    (map (fn [tote]
           (let [entry (index-protos/tote-thread-timeline-entry tote)
                 tidx  (index-protos/entry-idx entry)
                 tid   (-> tote
                           index-protos/tote-thread-timeline
                           index-protos/thread-id)]
             (cond
               (fn-call-trace? entry)
               (do
                 (swap! threads-stacks update tid conj entry)
                 {:type                :fn-call
                  :thread-id           tid
                  :thread-timeline-idx tidx
                  :fn-ns               (index-protos/get-fn-ns entry)
                  :fn-name             (index-protos/get-fn-name entry)})
               
               (fn-end-trace? entry)
               (do
                 (swap! threads-stacks update tid pop)
                 {:type                (if (fn-return-trace/fn-return-trace? entry)
                                         :fn-return
                                         :fn-unwind)
                  :thread-id           tid
                  :thread-timeline-idx tidx})
               
               (expr-trace? entry)
               (let [[curr-fn-call] (get @threads-stacks tid)
                     form-id (index-protos/get-form-id curr-fn-call)
                     form-data (index-protos/get-form forms-registry form-id)
                     coord (index-protos/get-coord-vec entry)
                     expr-val (index-protos/get-expr-val entry)]
                 
                 {:type                :expr-exec
                  :thread-id           tid
                  :thread-timeline-idx tidx
                  :expr-str            (binding [*print-length* 5
                                                 *print-level*  3]
                                         (pr-str
                                          (hansel-utils/get-form-at-coord (:form/form form-data)
                                                                          coord)))
                  :expr-type (pr-str (type expr-val))
                  :expr-val-str  (binding [*print-length* 3
                                           *print-level*  2]
                                   (pr-str expr-val))})))))))
