(ns flow-storm.runtime.indexes.thread-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [int-map]]            
            [flow-storm.runtime.indexes.total-order-timeline :as total-order-timeline]
            [clojure.string :as str])
  #?(:clj (:import [clojure.data.int_map PersistentIntMap])))


(defrecord ThreadRegistry [;; atom of int-map with flow-id -> thread-id -> indexes
                           registry

                           ;; atom of int-map with flow-id -> TotalOrderTimeline
                           total-order-timelines

                           ;; atom with threads events callbacks, like thread creation
                           callbacks]

  index-protos/ThreadRegistryP

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

  (get-thread-indexes [_ flow-id thread-id]
    #?(:clj
       (some-> ^PersistentIntMap                @registry
               ^PersistentIntMap                (.get flow-id)
               ^clojure.lang.PersistentArrayMap (.get thread-id)
               (.get :thread/indexes))
       :cljs
       (some-> @registry
               (get flow-id)
               (get thread-id)
               (get :thread/indexes))))

  (register-thread-indexes [this flow-id thread-id thread-name form-id indexes]
    (when-not (index-protos/flow-exists? this flow-id)
      (swap! total-order-timelines assoc flow-id (total-order-timeline/make-total-order-timeline)))
    
    (swap! registry update flow-id
           (fn [threads]               
             (assoc (or threads (int-map)) thread-id {:thread/id thread-id
                                                      :thread/name (if (str/blank? thread-name)
                                                                     (str "Thread-" thread-id)
                                                                     thread-name)
                                                      :thread/indexes indexes
                                                      :thread/blocked nil})))
    
    (when-let [otc (:on-thread-created @callbacks)]
      (otc {:flow-id flow-id
            :thread-id thread-id
            :thread-name thread-name
            :form-id form-id})))

  (set-thread-blocked [this flow-id thread-id breakpoint]
    (when (index-protos/get-thread-indexes this flow-id thread-id)
      (swap! registry assoc-in [flow-id thread-id :thread/blocked]  breakpoint)))

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

  (start-thread-registry [thread-reg cbs]    
    (reset! callbacks cbs)
    thread-reg)

  (stop-thread-registry [_] nil)

  (record-total-order-entry [_ flow-id th-timeline entry]
    (-> (get @total-order-timelines flow-id)
        (index-protos/tot-add-entry th-timeline entry)))

  (total-order-timeline [_ flow-id]
    (get @total-order-timelines flow-id)))

(defn make-thread-registry []
  (map->ThreadRegistry {:registry (atom (int-map)) 
                        :total-order-timelines (atom (int-map))
                        :callbacks (atom {})}))
