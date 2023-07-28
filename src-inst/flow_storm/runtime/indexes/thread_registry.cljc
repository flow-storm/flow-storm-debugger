(ns flow-storm.runtime.indexes.thread-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [int-map make-mutable-list ml-add ml-clear]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace :refer [fn-call-trace?]]
            [flow-storm.runtime.types.fn-return-trace :as fn-return-trace :refer [fn-return-trace?]]
            [flow-storm.runtime.types.expr-trace :as expr-trace :refer [expr-trace?]]
            [clojure.string :as str]
            [hansel.utils :as hansel-utils])
  #?(:clj (:import [clojure.data.int_map PersistentIntMap])))

(defn flow-id-key [fid]
  (or fid 10))

(defn flow-key-id [fk]
  (when-not (= fk 10)
    fk))

(defprotocol TotalOrderTimelineEntryP
  (tote-flow-id [_])
  (tote-thread-id [_])
  (tote-thread-tl-idx [_])
  (tote-entry [_]))

(deftype TotalOrderTimelineEntry [flow-id thread-id thread-tl-idx entry]
  TotalOrderTimelineEntryP
  (tote-flow-id [_] flow-id)
  (tote-thread-id [_] thread-id)
  (tote-thread-tl-idx [_] thread-tl-idx)
  (tote-entry [_] entry))

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

  (set-thread-blocked [_ flow-id thread-id breakpoint]    
    (let [flow-int-key (flow-id-key flow-id)]
      (swap! registry assoc-in [flow-int-key thread-id :thread/blocked]  breakpoint)))

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
    (locking total-order-timeline
      (ml-clear total-order-timeline)))

  (start-thread-registry [thread-reg callbacks]    
    (reset! *callbacks callbacks)
    thread-reg)

  (stop-thread-registry [_])

  (record-total-order-entry [_ flow-id thread-id thread-tl-idx entry]
    (locking total-order-timeline
      (ml-add total-order-timeline (TotalOrderTimelineEntry. flow-id thread-id thread-tl-idx entry))))

  (total-order-timeline [_ forms-registry]
    (locking total-order-timeline
      (loop [[tote & r] total-order-timeline
             threads-stacks {}
             timeline-ret (transient [])]
        (if-not tote
          (persistent! timeline-ret)
          
          (let [entry (tote-entry tote)
                fid   (tote-flow-id tote)
                tid   (tote-thread-id tote)
                tidx  (tote-thread-tl-idx tote)]
            (cond
              (fn-call-trace? entry)
              (recur r
                     (update threads-stacks tid conj entry)
                     (conj! timeline-ret {:type                :fn-call
                                          :flow-id             fid
                                          :thread-id           tid
                                          :thread-timeline-idx tidx
                                          :fn-ns               (fn-call-trace/get-fn-ns entry)
                                          :fn-name             (fn-call-trace/get-fn-name entry)}))
              
              (fn-return-trace? entry)
              (recur r
                     (update threads-stacks tid pop)
                     (conj! timeline-ret {:type                :fn-return
                                          :flow-id             fid
                                          :thread-id           tid
                                          :thread-timeline-idx tidx}))
              
              (expr-trace? entry)
              (let [[curr-fn-call] (get threads-stacks tid)
                    form-id (fn-call-trace/get-form-id curr-fn-call)
                    form-data (index-protos/get-form forms-registry form-id)
                    coord (index-protos/get-coord-vec entry)
                    expr-val (index-protos/get-expr-val entry)]
                
                (recur r
                       threads-stacks
                       (conj! timeline-ret {:type                :expr-exec
                                            :flow-id             fid
                                            :thread-id           tid
                                            :thread-timeline-idx tidx
                                            :expr-str            (binding [*print-length* 5
                                                                           *print-level*  3]
                                                                   (pr-str
                                                                    (hansel-utils/get-form-at-coord (:form/form form-data)
                                                                                                    coord)))
                                            :expr-type (pr-str (type expr-val))
                                            :expr-val-str  (binding [*print-length* 3
                                                                     *print-level*  2]
                                                             (pr-str expr-val))}))))))))))

(defn make-thread-registry []
  (map->ThreadRegistry {:registry (atom (int-map))
                        :total-order-timeline (make-mutable-list)
                        :*callbacks (atom {})}))
