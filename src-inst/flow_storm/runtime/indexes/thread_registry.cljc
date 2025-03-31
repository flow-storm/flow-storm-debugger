(ns flow-storm.runtime.indexes.thread-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [int-map]]            
            [flow-storm.runtime.indexes.total-order-timeline :as total-order-timeline]
            [clojure.string :as str])
  #?(:clj (:import [clojure.data.int_map PersistentIntMap])))


(defrecord FlowsThreadsRegistry [;; atom of int-map with flow-id -> thread-id -> thread-info
                                 registry

                                 ;; atom of int-map with flow-id -> TotalOrderTimeline
                                 total-order-timelines]

  index-protos/FlowsThreadsRegistryP

  (all-threads [_]
    (reduce-kv (fn [all-ths fid threads]
                 (into all-ths (mapv
                                (fn [tid] [fid tid])
                                (keys threads))))
               #{}
               @registry))

  (flow-threads-info [_ flow-id]
    (->> (get @registry flow-id)
         vals
         (mapv (fn [tinfo]
                 {:flow/id flow-id
                  :thread/id (:thread/id tinfo)
                  :thread/name (:thread/name tinfo)
                  :thread/blocked (:thread/blocked tinfo)}))))

  (flow-exists? [_ flow-id]
    (contains? @registry flow-id))

  (get-thread-tracker [_ flow-id thread-id]
    #?(:clj
       (some-> ^PersistentIntMap                @registry
               ^PersistentIntMap                (.get flow-id)
               ^clojure.lang.PersistentArrayMap (.get thread-id))
       :cljs
       (some-> @registry
               (get flow-id)
               (get thread-id))))

  (register-thread [this flow-id thread-id thread-name timeline init-fn-call-limits]
    (let [thread-tracker {:thread/id thread-id
                          :thread/name (if (str/blank? thread-name)
                                         (str "Thread-" thread-id)
                                         thread-name)
                          :thread/timeline timeline
                          :thread/*fn-call-limits (atom init-fn-call-limits)
                          :thread/*thread-limited (atom nil)
                          :thread/blocked nil}]
      (when-not (index-protos/flow-exists? this flow-id)
        (swap! total-order-timelines assoc flow-id (total-order-timeline/make-total-order-timeline flow-id)))
      
      (swap! registry update flow-id
             (fn [threads]               
               (assoc (or threads (int-map)) thread-id thread-tracker)))
      thread-tracker))

  (set-thread-blocked [_ flow-id thread-id breakpoint]
    (swap! registry assoc-in [flow-id thread-id :thread/blocked]  breakpoint))

  (discard-threads [this flow-threads-ids]
    (doseq [[fid tid] flow-threads-ids]
      (swap! registry update fid dissoc tid))
    
    ;; remove empty flows from the registry since flow-exist? uses it
    ;; kind of HACKY...
    (let [empty-flow-ids (reduce-kv (fn [efids fid threads-map]
                                      (if (empty? threads-map)
                                        (conj efids fid)
                                        efids))
                                    #{}
                                    @registry)]      
      (swap! registry (fn [flows-map] (apply dissoc flows-map empty-flow-ids))))

    (doseq [[fid] flow-threads-ids]
      (index-protos/tot-clear-all (index-protos/total-order-timeline this fid))))

  (record-total-order-entry [_ flow-id th-timeline th-idx]
    (-> (get @total-order-timelines flow-id)
        (index-protos/tot-add-entry th-timeline th-idx)))

  (total-order-timeline [_ flow-id]
    (get @total-order-timelines flow-id)))

(defn make-flows-threads-registry []
  (map->FlowsThreadsRegistry {:registry (atom (int-map)) 
                              :total-order-timelines (atom (int-map))}))
