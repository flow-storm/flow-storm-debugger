(ns flow-storm.runtime.indexes.thread-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]))

(deftype ThreadRegistry [*registry *callbacks]

  index-protos/ThreadRegistryP

  (all-threads [_]
    (keys @*registry))

  (flow-threads-info [_ flow-id]
    (->> (get @*registry flow-id)
         (map (fn [[tid tinfo]]
                {:thread/id tid
                 :thread/name (:thread/name tinfo)}))))

  (flow-exists? [_ flow-id]
    (some (fn [[fid _]] (= fid flow-id)) (keys @*registry)))

  (get-thread-indexes [_ flow-id thread-id]
    (get @*registry [flow-id thread-id :thread/indexes]))

  (register-thread-indexes [_ flow-id thread-id thread-name form-id indexes]
    (swap! *registry
           assoc [flow-id thread-id] {:thread/name thread-name
                                      :thread/indexes indexes})
    (when-let [otc (:on-thread-created @*callbacks)]
      (otc {:flow-id flow-id
            :thread-id thread-id
            :thread-name thread-name
            :form-id form-id})))

  (discard-threads [_ flow-threads-ids]
    (swap! *registry
           (fn [flows-threads]
             (apply dissoc flows-threads flow-threads-ids))))

  (start-thread-registry [thread-reg callbacks]
    (reset! *callbacks callbacks)
    thread-reg)

  (stop-thread-registry [_]))

(defn make-thread-registry []
  (->ThreadRegistry (atom {}) (atom {})))
