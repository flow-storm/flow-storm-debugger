(ns flow-storm.runtime.indexes.timelines.soft
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils :refer [make-mutable-stack ms-peek ms-push ms-pop ms-count
                                                                      make-mutable-list ml-get ml-add ml-count ml-trim
                                                                      make-mutable-hashmap mh-put mh-get]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]
            [flow-storm.runtime.types.bind-trace :as bind-trace]
            [flow-storm.utils :as utils]
            [flow-storm.runtime.indexes.timelines.soft-batched-store :as sbs]
            [clojure.math :as math]
            [clojure.core.protocols :as cp]))

(set! *warn-on-reflection* true)

(def tree-root-idx -1)

(def hole-entry (expr-trace/make-expr-trace "" :flow-storm/value-cleared 0 0))

(defn- print-it [timeline]
  (utils/format "#flow-storm/soft-timeline [count: %d]" (count timeline)))

(defn deref-entry [expr-and-binds-store entry-or-idx]
  (if (number? entry-or-idx)
    (let [soft-entry (get expr-and-binds-store entry-or-idx)]
      (if (= soft-entry :sbs/cleared)
        hole-entry
        soft-entry))
    entry-or-idx))

(deftype SoftTimelineTree [;; an array of FnCall, FnRet, FnUnwind and soft-batched-store Integer indexes
                           timeline

                           ;; for expr-exec and binds
                           expr-and-binds-store

                           ;; a mutable hashmap of fn-call-idx -> mutable list<BindTrace>
                           frames-bindings

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
                                                      parent-idx)
            this-frame-bindings (make-mutable-list)]

        (ms-push build-stack fn-call)
        (ml-add timeline fn-call)

        ;; create empty bindings list and add it to our soft store
        (sbs/append-obj expr-and-binds-store this-frame-bindings)
        (let [binds-list-soft-k (dec (count expr-and-binds-store))]
          (mh-put frames-bindings (int tl-idx) binds-list-soft-k))

        tl-idx)))

  (add-fn-return [this coord ret-val]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              tl-idx (ml-count timeline)
              fn-call-idx (index-protos/entry-idx curr-fn-call)
              fn-ret (fn-return-trace/make-fn-return-trace coord ret-val tl-idx fn-call-idx)
              this-frame-bindings (get expr-and-binds-store (mh-get frames-bindings fn-call-idx))]
          (index-protos/set-ret-idx curr-fn-call tl-idx)
          (ml-add timeline fn-ret)
          (ms-pop build-stack)
          (when (not= this-frame-bindings ::sbs/cleared)
            (ml-trim this-frame-bindings))
          tl-idx))))

  (add-fn-unwind [this coord throwable]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              tl-idx (ml-count timeline)
              fn-call-idx (index-protos/entry-idx curr-fn-call)
              fn-unwind (fn-return-trace/make-fn-unwind-trace coord
                                                              throwable
                                                              tl-idx
                                                              fn-call-idx)
              this-frame-bindings (get expr-and-binds-store (mh-get frames-bindings fn-call-idx))]
          (index-protos/set-ret-idx curr-fn-call tl-idx)
          (ml-add timeline fn-unwind)
          (ms-pop build-stack)
          (when (not= this-frame-bindings ::sbs/cleared)
            (ml-trim this-frame-bindings))
          tl-idx))))

  (add-expr-exec [this coord expr-val]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [tl-idx (ml-count timeline)
              curr-fn-call (ms-peek build-stack)
              expr-exec (expr-trace/make-expr-trace coord expr-val tl-idx (index-protos/entry-idx curr-fn-call))]
          (sbs/append-obj expr-and-binds-store expr-exec)
          (let [expr-sbs-idx (dec (count expr-and-binds-store))]
            (ml-add timeline expr-sbs-idx))
          tl-idx))))

  (add-bind [this coord symb-name symb-val]
    ;; discard all expressions when no FnCall has been made yet
    (locking this
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              fn-call-idx (index-protos/entry-idx curr-fn-call)
              last-entry-idx (ml-count timeline)
              bind-trace (bind-trace/make-bind-trace symb-name symb-val coord last-entry-idx)
              this-frame-bindings (get expr-and-binds-store (mh-get frames-bindings fn-call-idx))]

          (when (not= this-frame-bindings ::sbs/cleared)
            (ml-add this-frame-bindings bind-trace))))))

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

  index-protos/BindQueryP

  (bindings [this fn-call-idx]
    (locking this
      (let [this-frame-bindings (get expr-and-binds-store (mh-get frames-bindings fn-call-idx))]
        (when (not= this-frame-bindings ::sbs/cleared)
          this-frame-bindings))))

  clojure.lang.Counted
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
      (cp/coll-reduce
       timeline
       (fn [r e] (f r (deref-entry expr-and-binds-store e))))))

  (coll-reduce
    [this f v]
    (locking this
      (cp/coll-reduce
       timeline
       (fn [r e] (f r (deref-entry expr-and-binds-store e)))
       v)))

  clojure.lang.ILookup

  (valAt [this k] (locking this
                    (let [entry-or-idx (ml-get timeline k)]
                      (deref-entry expr-and-binds-store entry-or-idx))))

  (valAt [this k not-found]
    (or (.valAt this k) not-found))

  clojure.lang.Indexed

  (nth [this k] (.valAt this k))
  (nth [this k not-found] (.valAt this k not-found)))

(defmethod print-method SoftTimelineTree [timeline ^java.io.Writer w]
   (.write w ^String (print-it timeline)))

(defn make-timeline []
  (let [build-stack (make-mutable-stack)
        timeline (make-mutable-list)]
    (->SoftTimelineTree timeline
                        (sbs/make-soft-batched-store (math/pow 2 20))
                        (make-mutable-hashmap)
                        build-stack)))
