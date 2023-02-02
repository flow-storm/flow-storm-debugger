(ns flow-storm.runtime.indexes.thread-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]))

(deftype ThreadRegistry [*registry]

  index-protos/ThreadRegistryP
  (all-threads [_]
    (keys @*registry))

  (flow-exists? [_ flow-id]
    (some (fn [[fid _]] (= fid flow-id)) (keys @*registry)))

  (get-thread-indexes [_ flow-id thread-id]
    (get @*registry [flow-id thread-id]))

  (register-thread-indexes [_ flow-id thread-id indexes]
    (swap! *registry
           assoc [flow-id thread-id] indexes))

  (discard-threads [_ flow-threads-ids]
    (swap! *registry
           (fn [flows-threads]
             (apply dissoc flows-threads flow-threads-ids)))))

(defn make-thread-registry []
  (->ThreadRegistry (atom {})))
