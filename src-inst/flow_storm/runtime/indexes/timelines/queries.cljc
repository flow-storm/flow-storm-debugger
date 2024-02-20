(ns flow-storm.runtime.indexes.timelines.queries
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace]
            [flow-storm.runtime.types.expr-trace :as expr-trace]))

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

(defn timeline-find-entry [timeline from-idx backward? pred]
  (locking timeline
    (let [last-idx (if backward?
                     0
                     (dec (count timeline)))
          next-idx (if backward? dec inc)]
      (loop [i from-idx]
        (when (not= i last-idx)
          (let [tl-entry (get timeline i)
                fn-call (if (fn-call-trace/fn-call-trace? tl-entry)
                          tl-entry
                          (get timeline (index-protos/fn-call-idx tl-entry)))
                form-id (index-protos/get-form-id fn-call)]
            (if (pred form-id tl-entry)
              (index-protos/as-immutable tl-entry)
              (recur (next-idx i)))))))))

(defn tree-frame-data [timeline fn-call-idx {:keys [include-path? include-exprs? include-binds?]}]
  (if (= fn-call-idx (index-protos/tree-root-index timeline))
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
