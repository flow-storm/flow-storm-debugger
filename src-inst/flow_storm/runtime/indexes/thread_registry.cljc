(ns flow-storm.runtime.indexes.thread-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [int-map]]            
            [flow-storm.runtime.indexes.total-order-timeline :as total-order-timeline]
            [clojure.string :as str])
  #?(:clj (:import [clojure.data.int_map PersistentIntMap])))

(defn flow-id-key [fid]
  (or fid 10))

(defn flow-key-id [fk]
  (when-not (= fk 10)
    fk))

(defrecord ThreadRegistry [registry
                           total-order-timeline
                           *callbacks]

  index-protos/ThreadRegistryP

  (all-threads [_]
    (reduce-kv (fn [all-ths fk threads]
                 (into all-ths (mapv
                                (fn [tid] [(flow-key-id fk) tid])
                                (keys threads))))
               #{}
     @registry))

  (flow-threads-info [_ flow-id]
    (->> (get @registry (flow-id-key flow-id))
         vals
         (mapv (fn [tinfo]
                 {:flow/id flow-id
                  :thread/id (:thread/id tinfo)
                  :thread/name (:thread/name tinfo)
                  :thread/blocked (:thread/blocked tinfo)}))))

  (flow-exists? [_ flow-id]
    (let [flow-int-key (flow-id-key flow-id)]
      (contains? @registry flow-int-key)))

  (get-thread-indexes [_ flow-id thread-id]
    (let [flow-int-key (flow-id-key flow-id)]      
      #?(:clj
         (some-> ^PersistentIntMap                @registry
                 ^PersistentIntMap                (.get flow-int-key)
                 ^clojure.lang.PersistentArrayMap (.get thread-id)
                                                  (.get :thread/indexes))
         :cljs
         (some-> @registry
                 (get flow-int-key)
                 (get thread-id)
                 (get :thread/indexes)))))

  (register-thread-indexes [_ flow-id thread-id thread-name form-id indexes]
    (let [flow-int-key (flow-id-key flow-id)]
      (swap! registry update flow-int-key
             (fn [threads]               
               (assoc (or threads (int-map)) thread-id {:thread/id thread-id
                                                        :thread/name (if (str/blank? thread-name)
                                                                       (str "Thread-" thread-id)
                                                                       thread-name)
                                                        :thread/indexes indexes
                                                        :thread/blocked nil}))))
    
    (when-let [otc (:on-thread-created @*callbacks)]
      (otc {:flow-id flow-id
            :thread-id thread-id
            :thread-name thread-name
            :form-id form-id})))

  (set-thread-blocked [this flow-id thread-id breakpoint]
    (when (index-protos/get-thread-indexes this flow-id thread-id)
      (let [flow-int-key (flow-id-key flow-id)]
        (swap! registry assoc-in [flow-int-key thread-id :thread/blocked]  breakpoint))))

  (discard-threads [_ flow-threads-ids]
    (doseq [[fid tid] flow-threads-ids]
      (let [fk (flow-id-key fid)]
        (swap! registry update fk dissoc tid)))
    
    ;; remove empty flows from the registry since flow-exist? uses it
    ;; kind of HACKY...
    (let [empty-flow-keys (reduce-kv (fn [efks fk threads-map]
                                       (if (empty? threads-map)
                                         (conj efks fk)
                                         efks))
                                     #{}
                                     @registry)]      
      (swap! registry (fn [flows-map] (apply dissoc flows-map empty-flow-keys))))

    ;; discard the entire total-order-timeline list if we
    ;; discard any threads
    (index-protos/tot-clear-all total-order-timeline))

  (start-thread-registry [thread-reg callbacks]    
    (reset! *callbacks callbacks)
    thread-reg)

  (stop-thread-registry [_])

  (record-total-order-entry [_ th-timeline entry]    
    (index-protos/tot-add-entry total-order-timeline th-timeline entry))

  (total-order-timeline [_]
    total-order-timeline))

(defn make-thread-registry []
  (map->ThreadRegistry {:registry (atom (int-map))
                        :total-order-timeline (total-order-timeline/make-total-order-timeline)
                        :*callbacks (atom {})}))
