(ns flow-storm.runtime.indexes.total-order-timeline
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [make-mutable-list ml-add ml-clear ml-count ml-get]]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace :refer [fn-end-trace?]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace :refer [fn-call-trace?]]
            [flow-storm.runtime.types.expr-trace :as expr-trace :refer [expr-trace?]]
            [hansel.utils :as hansel-utils]
            #?(:clj [clojure.core.protocols :as cp])))

(def ^:dynamic *printing-expr-val* false)

(defprotocol TotalOrderTimelineP
  (add-entry [_ flow-id thread-id entry])
  (clear-all [_]))

(deftype TotalOrderTimelineEntry [flow-id thread-id entry]

  index-protos/ImmutableP
  (as-immutable [_]
    (merge {:flow-id flow-id
            :thread-id thread-id}
           (index-protos/as-immutable entry)))
  
  index-protos/TotalOrderTimelineEntryP
  
  (tote-flow-id [_] flow-id)
  (tote-thread-id [_] thread-id)  
  (tote-entry [_] entry))

(deftype TotalOrderTimeline [timeline]

  TotalOrderTimelineP

  (add-entry [this flow-id thread-id entry]
    ;; HACKY: `build-detailed-timeline` will print expr-vals which because of laziness can fire
    ;; instrumented code that will try to add to the timeline under the same thread, which will end
    ;; in a java.util.ConcurrentModificationException
    ;; The *printing-expr-val* flag is to prevent this
    (when-not *printing-expr-val*      
      (locking this
        (ml-add timeline (TotalOrderTimelineEntry. flow-id thread-id entry)))))
  
  (clear-all [_]
    (locking timeline
      (ml-clear timeline)))
  
  #?@(:clj
      [clojure.lang.Counted       
       (count
        [this]
        (locking this
          (ml-count timeline)))

       clojure.lang.Seqable
       (seq
        [this]
        (locking this
          (doall (seq timeline))))       

       cp/CollReduce
       (coll-reduce
        [this f]        
        (locking this
          (cp/coll-reduce timeline f)))
       
       (coll-reduce
        [this f v]        
        (locking this
          (cp/coll-reduce timeline f v)))

       clojure.lang.ILookup
       (valAt [this k] (locking this (ml-get timeline k)))
       (valAt [this k not-found] (locking this (or (ml-get timeline k) not-found)))

       clojure.lang.Indexed
       (nth [this k] (locking this (ml-get timeline k)))
       (nth [this k not-found] (locking this (or (ml-get timeline k) not-found)))]

      :cljs
      [ICounted
       (-count [_] (ml-count timeline))

       ISeqable
       (-seq [_] (seq timeline))
       
       IReduce
       (-reduce [_ f]
                (reduce f timeline))
       (-reduce [_ f start]
                (reduce f start timeline))

       ILookup
       (-lookup [_ k] (ml-get timeline k))
       (-lookup [_ k not-found] (or (ml-get timeline k) not-found))

       IIndexed
       (-nth [_ n] (ml-get timeline n))
       (-nth [_ n not-found] (or (ml-get timeline n) not-found))]))

(defn make-total-order-timeline []
  (TotalOrderTimeline. (make-mutable-list)))

(defn detailed-timeline-transd [forms-registry]
  (let [threads-stacks (atom {})]
    (map (fn [tote]
           (let [entry (index-protos/tote-entry tote)
                 fid   (index-protos/tote-flow-id tote)
                 tid   (index-protos/tote-thread-id tote)
                 tidx  (index-protos/entry-idx entry)]
             (cond
               (fn-call-trace? entry)
               (do
                 (swap! threads-stacks update tid conj entry)
                 {:type                :fn-call
                  :flow-id             fid
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
                  :flow-id             fid
                  :thread-id           tid
                  :thread-timeline-idx tidx})
               
               (expr-trace? entry)
               (let [[curr-fn-call] (get @threads-stacks tid)
                     form-id (index-protos/get-form-id curr-fn-call)
                     form-data (index-protos/get-form forms-registry form-id)
                     coord (index-protos/get-coord-vec entry)
                     expr-val (index-protos/get-expr-val entry)]
                 
                 {:type                :expr-exec
                  :flow-id             fid
                  :thread-id           tid
                  :thread-timeline-idx tidx
                  :expr-str            (binding [*print-length* 5
                                                 *print-level*  3]
                                         (pr-str
                                          (hansel-utils/get-form-at-coord (:form/form form-data)
                                                                          coord)))
                  :expr-type (pr-str (type expr-val))
                  :expr-val-str  (binding [*print-length* 3
                                           *print-level*  2
                                           *printing-expr-val* true]
                                   (pr-str expr-val))})))))))
