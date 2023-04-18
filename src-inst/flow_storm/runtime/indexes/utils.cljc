(ns flow-storm.runtime.indexes.utils  
  #?(:clj (:import [java.util ArrayList ArrayDeque HashMap]
                   [java.util.concurrent ConcurrentHashMap])))

;;;;;;;;;;;;;;;;;;;
;; Mutable stack ;;
;;;;;;;;;;;;;;;;;;;

#?(:cljs (defn make-mutable-stack [] #js [])
   :clj  (defn make-mutable-stack [] (ArrayDeque.)))

#?(:cljs (defn ms-peek [mstack]
           (aget mstack (dec (.-length mstack))))
   :clj (defn ms-peek [^ArrayDeque mstack]
          (.peek mstack)))

#?(:cljs (defn ms-push [mstack elem]
           (.push mstack elem))
   :clj (defn ms-push [^ArrayDeque mstack elem]
          (.push mstack elem)))

#?(:cljs (defn ms-pop [mstack]
           (.pop mstack))
   :clj (defn ms-pop [^ArrayDeque mstack]
          (.pop mstack)))

#?(:cljs (defn ms-count [mstack]
           (.-length mstack))
   :clj (defn ms-count [^ArrayDeque mstack]
          (.size mstack)))

;;;;;;;;;;;;;;;;;;
;; Mutable list ;;
;;;;;;;;;;;;;;;;;;

#?(:cljs (defn make-mutable-list []
           #js [])
   :clj (defn make-mutable-list []
          (ArrayList.)))

#?(:cljs (defn ml-get [mlist idx]
           (aget mlist idx))
   :clj (defn ml-get [^ArrayList mlist idx]
          (.get mlist idx)))

#?(:cljs (defn ml-add [mlist elem]
           (.push mlist elem))
   :clj (defn ml-add [^ArrayList mlist elem]
          (.add mlist elem)))

#?(:cljs (defn ml-count [mlist]
           (.-length mlist))
   :clj (defn ml-count [^ArrayList mlist]
          (.size mlist)))

#?(:cljs (defn ml-sub-list [mlist from to]
           (.slice mlist from to))
   :clj (defn ml-sub-list [^ArrayList mlist from to]
          (.subList mlist from to)))

;;;;;;;;;;;;;;;;;;;;;
;; Mutable hashmap ;;
;;;;;;;;;;;;;;;;;;;;;

#?(:clj (defn make-mutable-hashmap [] (HashMap.))
   :cljs (defn make-mutable-hashmap [] (atom {})))

#?(:clj (defn mh->immutable-map [^HashMap mh]
          (into {} mh))
   :cljs (defn mh->immutable-map [mh]
           @mh))

#?(:clj (defn mh-put [^HashMap mh k v]
          (.put mh k v))
   :cljs (defn mh-put [mh k v]
           (swap! mh assoc k v)))

#?(:clj (defn mh-contains? [^HashMap mh k]
          (.containsKey mh k))
   :cljs (defn mh-contains? [mh k]
           (contains? @mh k)))

#?(:clj (defn mh-get [^HashMap mh k]
          (.get mh k))
   :cljs (defn mh-get [mh k]
           (get @mh k)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutable concurrent hashmap ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj (defn make-mutable-concurrent-hashmap [] (ConcurrentHashMap.))
   :cljs (defn make-mutable-concurrent-hashmap [] (atom {})))

#?(:clj (defn mch->immutable-map [^ConcurrentHashMap mh]
          (into {} mh))
   :cljs (defn mch->immutable-map [mh]
           @mh))

#?(:clj (defn mch-put [^ConcurrentHashMap mh k v]
          (.put mh k v))
   :cljs (defn mch-put [mh k v]
           (swap! mh assoc k v)))

#?(:clj (defn mch-contains? [^ConcurrentHashMap mh k]
          (.containsKey mh k))
   :cljs (defn mch-contains? [mh k]
           (contains? @mh k)))

#?(:clj (defn mch-keys [^ConcurrentHashMap mh]
          (enumeration-seq (.keys mh)))
   :cljs (defn mch-keys [mh]
           (keys @mh)))

#?(:clj (defn mch-get [^ConcurrentHashMap mh k]
          (.get mh k))
   :cljs (defn mch-get [mh k]
           (get @mh k)))

#?(:clj (defn mch-remove [^ConcurrentHashMap mh k]
          (.remove mh k))
   :cljs (defn mch-remove [mh k]
           (swap! mh dissoc k)))
