(ns flow-storm.runtime.indexes.timelines-diffs
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.api :as index-api]
            [flow-storm.utils :as utils]
            [hansel.utils :as hansel-utils])
  #?(:clj
     (:import [com.github.difflib DiffUtils]
              [com.github.difflib.algorithm.myers MeyersDiff]
              [com.github.difflib.patch Chunk AbstractDelta ChangeDelta DeleteDelta EqualDelta InsertDelta])))

(defn- entry-summary-str [timeline entry]
  (binding [*print-level* 3
            *print-length* 3]
    (let [{:keys [idx] :as imm-entry} (index-protos/as-immutable entry)
          entry-form-id (if (= :fn-call (:type imm-entry))
                          (:form-id imm-entry)
                          (index-protos/get-form-id (get timeline (:fn-call-idx imm-entry))))
          entry-form (:form/form (index-api/get-form entry-form-id))
          e-details (case (:type imm-entry)                      
                      :fn-call   {:form-preview (utils/format "(%s/%s %s)" (:fn-ns imm-entry) (:fn-name imm-entry) (pr-str (:fn-args imm-entry)))}
                      :expr      {:form-preview (pr-str (hansel-utils/get-form-at-coord entry-form (:coord imm-entry)))
                                  :val-preview (pr-str (:result imm-entry))}
                      :fn-return {:form-preview (pr-str (hansel-utils/get-form-at-coord entry-form (:coord imm-entry)))
                                  :val-preview (pr-str (:result imm-entry))}
                      :fn-unwind {:form-preview (pr-str (hansel-utils/get-form-at-coord entry-form (:coord imm-entry)))
                                  :val-preview (ex-message (:throwable imm-entry))})]
      (assoc e-details
             :type (:type imm-entry)
             :idx idx))))

#?(:clj
   (defn diff [src-timeline target-timeline]
     (let [patch (-> (DiffUtils/diff (seq src-timeline) (seq target-timeline) ^MeyersDiff (MeyersDiff.))
                     .getDeltas)]
       (->> patch
            (mapv (fn [^AbstractDelta delta]
                    (let [^Chunk src-chunk (.getSource delta)
                          ^Chunk target-chunk (.getTarget delta)
                          src-entries (.getLines src-chunk)
                          target-entries (.getLines target-chunk)]
                      {:delta/type (cond
                                     (instance? ChangeDelta delta) :change-delta
                                     (instance? DeleteDelta delta) :delete-delta
                                     (instance? InsertDelta delta) :insert-delta
                                     (instance? EqualDelta  delta) :equal-delta)
                       :delta/src-chunk    (mapv #(entry-summary-str src-timeline %)    src-entries)
                       :delta/target-chunk (mapv #(entry-summary-str target-timeline %) target-entries)}))))))

   :cljs (defn diff [_ _]
           (entry-summary-str nil nil) ;; just for the linter
           (throw (ex-info "Not implemented yet" {}))))

(comment
  (dev-tester/boo [1 "hello" 4])
  (dev-tester/boo [1 "world" 4])

  (diff (flow-storm.runtime.indexes.api/get-timeline 0 33)
        (flow-storm.runtime.indexes.api/get-timeline 1 33))
  )
