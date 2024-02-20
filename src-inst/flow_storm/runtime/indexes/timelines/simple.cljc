(ns flow-storm.runtime.indexes.timelines.simple
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils :refer [make-mutable-stack ms-peek ms-push ms-pop ms-count
                                                                      make-mutable-list ml-get ml-add ml-count]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]
            [flow-storm.runtime.types.bind-trace :as bind-trace]
            [flow-storm.utils :as utils]
            #?(:clj [clojure.core.protocols :as cp])))

#?(:clj (set! *warn-on-reflection* true))

(def fn-expr-limit
  #?(:cljs 9007199254740992 ;; MAX safe integer
     :clj 10000))

(def tree-root-idx -1)

(defn- print-it [timeline]
  (utils/format "#flow-storm/timeline [count: %d]" (count timeline)))

(deftype ExecutionTimelineTree [;; an array of FnCall, Expr, FnRet, FnUnwind
                                  timeline

                                  ;; a stack of pointers to prev FnCall
                                  build-stack
                                  ]

  index-protos/RecorderP

  (add-fn-call [this fn-ns fn-name form-id args]
    (locking this
      (let [tl-idx (ml-count timeline)
            curr-fn-call (ms-peek build-stack)
            parent-idx (when curr-fn-call (index-protos/entry-idx curr-fn-call))
            fn-call (fn-call-trace/make-fn-call-trace fn-ns
                                                      fn-name
                                                      form-id
                                                      args
                                                      tl-idx
                                                      parent-idx)]

        (ms-push build-stack fn-call)
        (ml-add timeline fn-call)
        tl-idx)))

  (add-fn-return [this coord ret-val]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              tl-idx (ml-count timeline)
              fn-ret (fn-return-trace/make-fn-return-trace coord ret-val tl-idx (index-protos/entry-idx curr-fn-call))]
          (index-protos/set-ret-idx curr-fn-call tl-idx)
          (ml-add timeline fn-ret)
          (ms-pop build-stack)
          tl-idx))))

  (add-fn-unwind [this coord throwable]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              tl-idx (ml-count timeline)
              fn-unwind (fn-return-trace/make-fn-unwind-trace coord
                                                              throwable
                                                              tl-idx
                                                              (index-protos/entry-idx curr-fn-call))]
          (index-protos/set-ret-idx curr-fn-call tl-idx)
          (ml-add timeline fn-unwind)
          (ms-pop build-stack)
          tl-idx))))

  (add-expr-exec [this coord expr-val]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [tl-idx (ml-count timeline)
              curr-fn-call (ms-peek build-stack)
              expr-exec (expr-trace/make-expr-trace coord expr-val tl-idx (index-protos/entry-idx curr-fn-call))]
          (ml-add timeline expr-exec)
          tl-idx))))

  (add-bind [this coord symb-name symb-val]
    ;; discard all expressions when no FnCall has been made yet
    (locking this
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              last-entry-idx (ml-count timeline)
              bind-trace (bind-trace/make-bind-trace symb-name symb-val coord last-entry-idx)]
          (index-protos/add-binding curr-fn-call bind-trace)))))

  index-protos/TreeBuilderP

  (reset-build-stack [this]
    (locking this
      (loop [stack build-stack]
        (when (pos? (ms-count stack))
          (ms-pop stack)
          (recur stack)))))

  index-protos/TreeP

  (tree-root-index [_]
    tree-root-idx)

  (tree-childs-indexes [this fn-call-idx]
    (locking this
      (let [tl-cnt (count this)]
        (when (pos? tl-cnt)
          (let [start-idx (if (= fn-call-idx tree-root-idx)
                            0
                            (inc fn-call-idx))
                end-idx (if (= fn-call-idx tree-root-idx)
                          tl-cnt
                          (let [fn-call (ml-get timeline fn-call-idx)]
                            (or (index-protos/get-ret-idx fn-call) tl-cnt)))]
            (loop [i start-idx
                   ch-indexes (transient [])]
              (if (= i end-idx)
                (persistent! ch-indexes)

                (let [tle (ml-get timeline i)]
                  (if (fn-call-trace/fn-call-trace? tle)
                    (recur (if-let [ret-idx (index-protos/get-ret-idx tle)]
                             (inc ret-idx)
                             ;; if we don't have a ret-idx it means this function didn't return yet
                             ;; so we just recur with the end which will finish the loop
                             tl-cnt)
                           (conj! ch-indexes i))

                    (recur (inc i) ch-indexes))))))))))

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
       (-nth [_ n not-found] (or (ml-get timeline n) not-found))

       IPrintWithWriter
       (-pr-writer [this writer _]
                   (write-all writer (print-it this)))]))

#?(:clj
   (defmethod print-method ExecutionTimelineTree [timeline ^java.io.Writer w]
      (.write w ^String (print-it timeline))))

(defn make-index []
  (let [build-stack (make-mutable-stack)
        timeline (make-mutable-list)]
    (->ExecutionTimelineTree timeline build-stack)))
