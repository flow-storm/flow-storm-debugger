(ns flow-storm.runtime.indexes.thread-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [make-mutable-concurrent-hashmap mch->immutable-map mch-put mch-remove mch-keys mch-get]]))

(defrecord ThreadRegistry [registry *callbacks]

  index-protos/ThreadRegistryP

  (all-threads [_]
    (mch-keys registry))

  (flow-threads-info [_ flow-id]
    (->> (mch->immutable-map registry)
         (keep (fn [[[fid tid] tinfo]]
             (when (= fid flow-id)
               {:flow/id fid
                :thread/id tid
                :thread/name (:thread/name tinfo)})))))

  (flow-exists? [_ flow-id]
    (some (fn [[fid _]] (= fid flow-id)) (mch-keys registry)))

  (get-thread-indexes [_ flow-id thread-id]
    (some-> registry
            (mch-get [flow-id thread-id])
            (get :thread/indexes)))

  (register-thread-indexes [_ flow-id thread-id thread-name form-id indexes]
    (mch-put registry [flow-id thread-id] {:thread/name thread-name
                                           :thread/indexes indexes})
    (when-let [otc (:on-thread-created @*callbacks)]
      (otc {:flow-id flow-id
            :thread-id thread-id
            :thread-name thread-name
            :form-id form-id})))

  (discard-threads [_ flow-threads-ids]
    (doseq [ftid flow-threads-ids]
      (mch-remove registry ftid)))

  (start-thread-registry [thread-reg callbacks]    
    (reset! *callbacks callbacks)
    thread-reg)

  (stop-thread-registry [_]))

(defn make-thread-registry []
  (->ThreadRegistry (make-mutable-concurrent-hashmap) (atom {})))
