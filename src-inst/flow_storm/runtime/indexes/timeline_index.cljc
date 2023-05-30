(ns flow-storm.runtime.indexes.timeline-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils :refer [make-mutable-stack ms-peek ms-push ms-pop ms-count
                                                                      make-mutable-list ml-get ml-add ml-count ml-sub-list]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]
            [flow-storm.runtime.types.bind-trace :as bind-trace]))

(def fn-expr-limit
  #?(:cljs 9007199254740992 ;; MAX safe integer     
     :clj 10000))

(def tree-root-idx -1)

(defn fn-call-exprs [timeline fn-call-idx]
  (let [tl-cnt (ml-count timeline)
        fn-call (ml-get timeline fn-call-idx)
        ret-idx (or (fn-call-trace/get-ret-idx fn-call) tl-cnt)]
    (loop [idx (inc fn-call-idx)
           collected []]
      (if (= idx ret-idx)
        
        ;; we reached the end
        collected

        ;; keep collecting
        (let [tle (ml-get timeline idx)]
          (if (expr-trace/expr-trace? tle)
            
            ;; if expr collect it
            (recur (inc idx) (conj collected tle))

            ;; else if fn-call, jump over
            (if (fn-call-trace/fn-call-trace? tle)
              (recur (if-let [ret-idx (fn-call-trace/get-ret-idx tle)]
                       (inc ret-idx)
                       ;; if we don't have a ret-idx it means this function didn't return yet
                       ;; so we just recur with the end which will finish the loop
                       tl-cnt)
                     collected)
              (recur (inc idx) collected))))))))

(defn- get-fn-call-idx-path

  "Return a path of timeline indexes from (root ... frame)"
  
  [timeline fn-call-idx]  
  (loop [curr-fn-call-idx fn-call-idx
         fn-call-idx-path []]
    (if (nil? curr-fn-call-idx)
      fn-call-idx-path
      (recur (fn-call-trace/get-parent-idx (ml-get timeline curr-fn-call-idx))
             (conj fn-call-idx-path curr-fn-call-idx)))))

(defn- timeline-next-out-idx

  "Given `idx` return the next index after the current call frame for the `timeline`."

  [timeline idx]
  (let [last-idx (dec (ml-count timeline))
        curr-fn-call (ml-get timeline (index-protos/fn-call-idx (ml-get timeline idx)))
        curr-fn-call-ret-idx (fn-call-trace/get-ret-idx curr-fn-call)]
    (min last-idx
         (if curr-fn-call-ret-idx
           (inc curr-fn-call-ret-idx)
           last-idx))))

(defn- timeline-next-over-idx [timeline idx]
  (let [last-idx (dec (ml-count timeline))
        init-fn-call-idx (index-protos/fn-call-idx (ml-get timeline idx))]
    (loop [i (inc idx)]
      (if (>= i last-idx)
        idx
        (let [tl-entry (ml-get timeline i)]
          (if (= (index-protos/fn-call-idx tl-entry) init-fn-call-idx)            
            i
            (if (fn-call-trace/fn-call-trace? tl-entry)
              ;; this is an imporatant optimization for big timelines,
              ;; when moving forward, if we see a fn-call jump directly past the return
              (recur (if-let [ret-idx (fn-call-trace/get-ret-idx tl-entry)]
                       (inc ret-idx)
                       last-idx))
              (recur (inc i)))))))))

(defn- timeline-prev-over-idx [timeline idx]
  (let [init-fn-call-idx (index-protos/fn-call-idx (ml-get timeline idx))]
    (loop [i (dec idx)]
      (if-not (pos? i)
        idx
        (let [tl-entry (ml-get timeline i)]
          (if (= (index-protos/fn-call-idx tl-entry) init-fn-call-idx)            
            i
            ;; this is an important optimization for big timelines
            ;; when moving back sikip over entire functions instead
            ;; of just searching backwards one entry at a time until
            ;; we find our original frame
            (recur (dec (index-protos/fn-call-idx tl-entry)))))))))

(defn- timeline-prev-idx [timeline idx]
  (if-not (pos? idx)
    0
    (let [prev-tl-entry (ml-get timeline (- idx 1))]
      (if (fn-call-trace/fn-call-trace? prev-tl-entry)
        (if (and (>= (- idx 2) 0) (ml-get timeline (- idx 2)))
          ;; if there is a call right before a call then return the fn-call index,
          ;; so we don't miss the fn-call
          (- idx 1)

          ;; else just skip the fn-call and go directly to the prev expr or return
          (max 0 (- idx 2)))
        (- idx 1)))))

(defn- timeline-next-idx [timeline idx]
  (let [last-idx (dec (ml-count timeline))]
    (if (>= idx last-idx)
      last-idx
      (let [next-tl-entry (ml-get timeline (+ 1 idx))]
        (if (fn-call-trace/fn-call-trace? next-tl-entry)
          (if (ml-get timeline (+ 2 idx))
            ;; if there is a call right after a call then return the fn-call index,
            ;; so we don't miss the fn-call
            (+ 1 idx)

            ;; else just skip the fn-call and go directly to the next expr or return
            (+ 2 idx))
          (+ 1 idx))))))

(defrecord ExecutionTimelineTree [;; an array of FnCall, Expr, FnRet
                                  timeline 

                                  ;; a stack of pointers to prev FnCall
                                  build-stack                                                                                                               
                                  ]

  index-protos/BuildIndexP

  (add-form-init [_ _]) ; don't do anything for form-init 
  
  (add-fn-call [this fn-call]
    (locking this
      (let [tl-idx (ml-count timeline)
            curr-fn-call (ms-peek build-stack)]
        (when curr-fn-call
          (fn-call-trace/set-parent-idx fn-call (index-protos/entry-idx curr-fn-call)))
        (fn-call-trace/set-idx fn-call tl-idx)
        (ms-push build-stack fn-call)        
        (ml-add timeline fn-call))))

  (add-fn-return [this fn-ret]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              tl-idx (ml-count timeline)]
          (fn-return-trace/set-idx fn-ret tl-idx)
          (fn-return-trace/set-fn-call-idx fn-ret (index-protos/entry-idx curr-fn-call))
          (fn-call-trace/set-ret-idx curr-fn-call tl-idx)          
          (ml-add timeline fn-ret)
          (ms-pop build-stack)))))
  
  (add-expr-exec [this expr]
    (locking this
      ;; discard all expressions when no FnCall has been made yet
      (when (pos? (ms-count build-stack))
        (let [tl-idx (ml-count timeline)
              curr-fn-call (ms-peek build-stack)]
          (expr-trace/set-idx expr tl-idx)
          (expr-trace/set-fn-call-idx expr (index-protos/entry-idx curr-fn-call))
          (ml-add timeline expr)))))
  
  (add-bind [this bind]
    ;; discard all expressions when no FnCall has been made yet
    (locking this
      (when (pos? (ms-count build-stack))
        (let [curr-fn-call (ms-peek build-stack)
              last-entry-idx (ml-count timeline)]
          (bind-trace/set-visible-after bind last-entry-idx)
          (fn-call-trace/add-binding curr-fn-call bind)))))

  (reset-build-stack [this]
    (locking this
      (loop [stack build-stack]
        (when (pos? (ms-count stack))
          (ms-pop stack)
          (recur stack)))))

  index-protos/TimelineP
  
  (timeline-count [this]
    (locking this
      (ml-count timeline)))
  
  (timeline-entry [this idx drift]
    (locking this      
      (let [drift (or drift :at)
            last-idx (dec (ml-count timeline))
            idx (-> idx (max 0) (min last-idx)) ;; clamp the idx
            target-idx (case drift
                            :next-out  (timeline-next-out-idx timeline idx)
                            :next-over (timeline-next-over-idx timeline idx)
                            :prev-over (timeline-prev-over-idx timeline idx)
                            :next      (timeline-next-idx timeline idx)
                            :prev      (timeline-prev-idx timeline idx)
                            :at   idx)            
            tl-entry (ml-get timeline target-idx)]
        (index-protos/as-immutable tl-entry))))
  
  (timeline-frames [this from-idx to-idx pred]
    (locking this
      (let [from (or from-idx 0)
            to (or to-idx (dec (ml-count timeline)))
            sub-timeline (ml-sub-list timeline from to)]
        (into []
              (keep (fn [tl-entry]
                      (when (and (fn-call-trace/fn-call-trace? tl-entry)
                                 (pred (fn-call-trace/get-fn-ns tl-entry)
                                       (fn-call-trace/get-fn-name tl-entry)
                                       (fn-call-trace/get-form-id tl-entry)
                                       (fn-call-trace/get-fn-args tl-entry)))
                        (index-protos/tree-frame-data this
                                                      (index-protos/entry-idx tl-entry)
                                                      {}))))
              sub-timeline))))

  (timeline-find-entry [this from-idx backwards? pred]
    (locking this
      (let [last-idx (if backwards?
                       0
                       (dec (ml-count timeline)))
            next-idx (if backwards? dec inc)]
        (loop [i from-idx]
          (when (not= i last-idx)
            (let [tl-entry (ml-get timeline i)]
              (if (pred tl-entry)
                (index-protos/as-immutable tl-entry)
                (recur (next-idx i )))))))))

  (timeline-raw-entries [this from-idx to-idx]
    (locking this
      (let [from (or from-idx 0)
            to (or to-idx (dec (ml-count timeline)))
            sub-timeline (ml-sub-list timeline from to)]
        (into [] sub-timeline))))

  index-protos/TreeP

  (tree-root-index [_]
    tree-root-idx)
  
  (tree-childs-indexes [this fn-call-idx]
    (locking this
      (let [tl-cnt (index-protos/timeline-count this)
            start-idx (if (= fn-call-idx tree-root-idx)
                        0
                        (inc fn-call-idx))
            end-idx (if (= fn-call-idx tree-root-idx)
                      tl-cnt
                      (let [fn-call (ml-get timeline fn-call-idx)]
                        (or (fn-call-trace/get-ret-idx fn-call) tl-cnt)))]
        (loop [i start-idx
               ch-indexes []]
          (if (= i end-idx)
            ch-indexes

            (let [tle (ml-get timeline i)]
              (if (fn-call-trace/fn-call-trace? tle)
                (recur (if-let [ret-idx (fn-call-trace/get-ret-idx tle)]
                         (inc ret-idx)
                         ;; if we don't have a ret-idx it means this function didn't return yet
                         ;; so we just recur with the end which will finish the loop
                         tl-cnt)
                       (conj ch-indexes i))

                (recur (inc i) ch-indexes))))))))
  
  (tree-frame-data [this fn-call-idx {:keys [include-path? include-exprs? include-binds?]}]
    (locking this
      (if (= fn-call-idx tree-root-idx)
        {:root? true}
        (locking this
          (let [fn-call (ml-get timeline fn-call-idx)
                _ (assert (fn-call-trace/fn-call-trace? fn-call) "Frame data should be called with a idx that correspond to a fn-call")
                fn-ret-idx (fn-call-trace/get-ret-idx fn-call)
                fr-data {:fn-ns (fn-call-trace/get-fn-ns fn-call)
                         :fn-name (fn-call-trace/get-fn-name fn-call)
                         :args-vec (fn-call-trace/get-fn-args fn-call)                 
                         :ret (when fn-ret-idx
                                (fn-return-trace/get-ret-val (ml-get timeline fn-ret-idx)))
                         :form-id (fn-call-trace/get-form-id fn-call)
                         :fn-call-idx fn-call-idx
                         :parent-fn-call-idx (fn-call-trace/get-parent-idx fn-call)}
                fr-data (if include-path?
                          (assoc fr-data :fn-call-idx-path (get-fn-call-idx-path timeline fn-call-idx))
                          fr-data)
                fr-data (if include-exprs?
                          (let [expressions (fn-call-exprs timeline fn-call-idx)]
                            (assoc fr-data :expr-executions (map index-protos/as-immutable expressions)))
                          fr-data)
                fr-data (if include-binds?
                          (assoc fr-data :bindings (map index-protos/as-immutable (fn-call-trace/bindings fn-call)))
                          fr-data)]        
            fr-data))))))

(defn make-index []
  (let [build-stack (make-mutable-stack)
        timeline (make-mutable-list)]    
    (->ExecutionTimelineTree timeline build-stack)))


