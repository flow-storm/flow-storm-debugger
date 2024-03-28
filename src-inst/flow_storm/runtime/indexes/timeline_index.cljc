(ns flow-storm.runtime.indexes.timeline-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils :refer [make-mutable-stack ms-peek ms-push ms-pop ms-count
                                                                      make-mutable-list ml-get ml-add ml-count]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]
            [flow-storm.runtime.types.bind-trace :as bind-trace]
            [flow-storm.utils :as utils]
            #?(:clj [clojure.core.protocols :as cp])))


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

(defn- fn-call-exprs [timeline fn-call-idx]
  (locking timeline
    (let [tl-cnt (count timeline)
          fn-call (get timeline fn-call-idx)
          ret-idx (or (index-protos/get-ret-idx fn-call) tl-cnt)]
      (loop [idx (inc fn-call-idx)
             collected (transient [])]
        (if (= idx ret-idx)
          
          ;; we reached the end
          (persistent! collected)

          ;; keep collecting
          (let [tle (get timeline idx)]
            (if (expr-trace/expr-trace? tle)
              
              ;; if expr collect it
              (recur (inc idx) (conj! collected tle))

              ;; else if fn-call, jump over
              (if (fn-call-trace/fn-call-trace? tle)
                (recur (if-let [ret-idx (index-protos/get-ret-idx tle)]
                         (inc ret-idx)
                         ;; if we don't have a ret-idx it means this function didn't return yet
                         ;; so we just recur with the end which will finish the loop
                         tl-cnt)
                       collected)
                (recur (inc idx) collected)))))))))

(defn- get-fn-call-idx-path

  "Return a path of timeline indexes from (root ... frame)"
  
  [timeline fn-call-idx]
  (locking timeline
    (loop [curr-fn-call-idx fn-call-idx
           fn-call-idx-path (transient [])]
      (if (nil? curr-fn-call-idx)
        (persistent! fn-call-idx-path)
        (recur (index-protos/get-parent-idx (get timeline curr-fn-call-idx))
               (conj! fn-call-idx-path curr-fn-call-idx))))))

(defn- timeline-next-out-idx

  "Given `idx` return the next index after the current call frame for the `timeline`."

  [timeline idx]
  (locking timeline
    (let [last-idx (dec (count timeline))
          curr-fn-call (get timeline (index-protos/fn-call-idx (get timeline idx)))
          curr-fn-call-ret-idx (index-protos/get-ret-idx curr-fn-call)]
      (min last-idx
           (if curr-fn-call-ret-idx
             (inc curr-fn-call-ret-idx)
             last-idx)))))

(defn- timeline-next-over-idx [timeline idx]
  (locking timeline
    (let [last-idx (dec (count timeline))
          init-entry (get timeline idx)
          init-fn-call-idx (index-protos/fn-call-idx init-entry)]
      (if (fn-return-trace/fn-end-trace? init-entry)
        ;; if we are on a return just move next
        (inc idx)
        
        (loop [i (inc idx)]
          (if (>= i last-idx)
            idx
            (let [tl-entry (get timeline i)]
              (if (= (index-protos/fn-call-idx tl-entry) init-fn-call-idx)            
                i
                (if (fn-call-trace/fn-call-trace? tl-entry)
                  ;; this is an imporatant optimization for big timelines,
                  ;; when moving forward, if we see a fn-call jump directly past the return
                  (recur (if-let [ret-idx (index-protos/get-ret-idx tl-entry)]
                           (inc ret-idx)
                           last-idx))
                  (recur (inc i)))))))))))

(defn- timeline-prev-over-idx [timeline idx]  
  (locking timeline
    (let [init-entry (get timeline idx)
         init-fn-call-idx (index-protos/fn-call-idx init-entry)]
     (if (fn-call-trace/fn-call-trace? init-entry)
       
       ;; if we are on a fn-call just move prev
       (dec idx)
       
       (loop [i (dec idx)]
         (if-not (pos? i)
           idx
           (let [tl-entry (get timeline i)]
             (if (= (index-protos/fn-call-idx tl-entry) init-fn-call-idx)            
               i
               ;; this is an important optimization for big timelines
               ;; when moving back sikip over entire functions instead
               ;; of just searching backwards one entry at a time until
               ;; we find our original frame
               (recur (dec (index-protos/fn-call-idx tl-entry)))))))))))

(defn- timeline-prev-idx [timeline idx]  
  (locking timeline
    (if-not (pos? idx)
     0
     (let [prev-tl-entry (get timeline (- idx 1))]
       (if (fn-call-trace/fn-call-trace? prev-tl-entry)
         (if (and (>= (- idx 2) 0) (get timeline (- idx 2)))
           ;; if there is a call right before a call then return the fn-call index,
           ;; so we don't miss the fn-call
           (- idx 1)

           ;; else just skip the fn-call and go directly to the prev expr or return
           (max 0 (- idx 2)))
         (- idx 1))))))

(defn- timeline-next-idx [timeline idx]  
  (locking timeline
    (let [last-idx (dec (count timeline))]
     (if (>= idx last-idx)
       last-idx
       (let [next-tl-entry (get timeline (+ 1 idx))]
         (if (fn-call-trace/fn-call-trace? next-tl-entry)
           (if (get timeline (+ 2 idx))
             ;; if there is a call right after a call then return the fn-call index,
             ;; so we don't miss the fn-call
             (+ 1 idx)

             ;; else just skip the fn-call and go directly to the next expr or return
             (+ 2 idx))
           (+ 1 idx)))))))

(defn timeline-entry [timeline idx drift]
    (locking timeline
      (when (pos? (count timeline))
        (let [drift (or drift :at)
              last-idx (dec (count timeline))
              idx (-> idx (max 0) (min last-idx)) ;; clamp the idx
              target-idx (case drift
                           :next-out  (timeline-next-out-idx timeline idx)
                           :next-over (timeline-next-over-idx timeline idx)
                           :prev-over (timeline-prev-over-idx timeline idx)
                           :next      (timeline-next-idx timeline idx)
                           :prev      (timeline-prev-idx timeline idx)
                           :at   idx)
              tl-entry (get timeline target-idx)]
          (index-protos/as-immutable tl-entry)))))
  
(defn tree-frame-data [timeline fn-call-idx {:keys [include-path? include-exprs? include-binds?]}]
  (if (= fn-call-idx tree-root-idx)
    {:root? true}
    (locking timeline
      (when (pos? (count timeline))
        (let [fn-call (get timeline fn-call-idx)
              _ (assert (fn-call-trace/fn-call-trace? fn-call) "Frame data should be called with a idx that correspond to a fn-call")
              fn-ret-idx (index-protos/get-ret-idx fn-call)
              fn-return (when fn-ret-idx (get timeline fn-ret-idx))
              fr-data {:fn-ns (index-protos/get-fn-ns fn-call)
                       :fn-name (index-protos/get-fn-name fn-call)
                       :args-vec (index-protos/get-fn-args fn-call)
                       :form-id (index-protos/get-form-id fn-call)
                       :fn-call-idx fn-call-idx
                       :parent-fn-call-idx (index-protos/get-parent-idx fn-call)}
              fr-data (cond-> fr-data
                        (nil? fn-return)                             (assoc :return/kind :waiting)
                        (fn-return-trace/fn-unwind-trace? fn-return) (assoc :return/kind :unwind
                                                                            :throwable (index-protos/get-throwable fn-return))
                        (fn-return-trace/fn-return-trace? fn-return) (assoc :return/kind :return
                                                                            :ret (index-protos/get-expr-val fn-return)))
              fr-data (if include-path?
                        (assoc fr-data :fn-call-idx-path (get-fn-call-idx-path timeline fn-call-idx))
                        fr-data)
              fr-data (if include-exprs?
                        (let [expressions (fn-call-exprs timeline fn-call-idx)]
                          ;; expr-executions will contain also the fn-return at the end
                          (assoc fr-data :expr-executions (cond-> (mapv index-protos/as-immutable expressions)
                                                            fn-return (conj (index-protos/as-immutable fn-return)))))
                        fr-data)
              fr-data (if include-binds?
                        (assoc fr-data :bindings (map index-protos/as-immutable (index-protos/bindings fn-call)))
                        fr-data)]
          fr-data)))))
