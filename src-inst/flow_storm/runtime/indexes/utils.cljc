(ns flow-storm.runtime.indexes.utils
  #?(:clj (:require [clojure.data.int-map :as int-map])
     :cljd/clj-host nil)
  #?(:clj (:import [java.util ArrayList Vector ArrayDeque HashMap]
                   [java.util.concurrent ConcurrentHashMap])
     :cljd/clj-host nil))

;;;;;;;;;;;;;;;;;;;
;; Mutable stack ;;
;;;;;;;;;;;;;;;;;;;

#?(:cljs (defn make-mutable-stack [] #js [])
   :clj  (defn make-mutable-stack [] (ArrayDeque.))
   :cljd (defn make-mutable-stack [] (atom nil)))

#?(:cljs (defn ms-peek [mstack]
           (aget mstack (dec (.-length mstack))))
   :clj (defn ms-peek [^ArrayDeque mstack]
          (.peek mstack))
   :cljd (defn ms-peek [mstack]
           (first @mstack)))

#?(:cljs (defn ms-push [mstack elem]
           (.push mstack elem))
   :clj (defn ms-push [^ArrayDeque mstack elem]
          (.push mstack elem))
   :cljd (defn ms-push [mstack elem]
          (swap! mstack conj elem)))

#?(:cljs (defn ms-pop [mstack]
           (.pop mstack))
   :clj (defn ms-pop [^ArrayDeque mstack]
          (.pop mstack))
   :cljd (defn ms-pop [mstack]
          (swap! mstack pop)))

#?(:cljs (defn ms-count [mstack]
           (.-length mstack))
   :clj (defn ms-count [^ArrayDeque mstack]
          (.size mstack))
   :cljd (defn ms-count [mstack]
          (count @mstack)))

;;;;;;;;;;;;;;;;;;
;; Mutable list ;;
;;;;;;;;;;;;;;;;;;

#?(:cljs (defn make-mutable-list []
           #js [])
   :clj (defn make-mutable-list []
          (ArrayList.))
   :cljd (defn make-mutable-list []
           (atom [])))

#?(:cljs (defn ml-get [mlist idx]
           (aget mlist idx))
   :clj (defn ml-get [^ArrayList mlist idx]
          (.get mlist idx))
   :cljd (defn ml-get [mlist idx]
           (get @mlist idx)))

#?(:cljs (defn ml-add [mlist elem]
           (.push mlist elem))
   :clj (defn ml-add [^ArrayList mlist elem]
          (.add mlist elem))
   :cljd (defn ml-add [mlist elem]
           (swap! mlist conj elem)))

#?(:cljs (defn ml-count [mlist]
           (.-length mlist))
   :clj (defn ml-count [^ArrayList mlist]
          (.size mlist))
   :cljd (defn ml-count [mlist]
           (count @mlist)))

#?(:cljs (defn ml-sub-list [mlist from to]
           (.slice mlist from to))
   :clj (defn ml-sub-list [^ArrayList mlist from to]
          (.subList mlist from to))
   :cljd (defn ml-sub-list [mlist from to]
           (subvec @mlist from to)))

#?(:cljs (defn ml-clear [mlist]
           (set! (.-length mlist) 0))
   :clj (defn ml-clear [^ArrayList mlist]
          (.clear mlist))
   :cljd (defn ml-clear [mlist]
           (reset! mlist [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutable concurrent list ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs (defn make-concurrent-mutable-list []
           #js [])
   :clj (defn make-concurrent-mutable-list []
          (Vector.))
   :cljd (defn make-concurrent-mutable-list []
           (atom [])))

#?(:cljs (defn mcl-get [mlist idx]
           (aget mlist idx))
   :clj (defn mcl-get [^Vector mlist idx]
          (.get mlist idx))
   :cljd (defn mcl-get [mlist idx]
           (get @mlist idx)))

#?(:cljs (defn mcl-add [mlist elem]
           (.push mlist elem))
   :clj (defn mcl-add [^Vector mlist elem]
          (.add mlist elem))
   :cljd (defn mcl-add [mlist elem]
           (swap! mlist conj elem)))

#?(:cljs (defn mcl-count [mlist]
           (.-length mlist))
   :clj (defn mcl-count [^Vector mlist]
          (.size mlist))
   :cljd (defn mcl-count [mlist]
          (count @mlist)))

#?(:cljs (defn mcl-sub-list [mlist from to]
           (.slice mlist from to))
   :clj (defn mcl-sub-list [^Vector mlist from to]
          (.subList mlist from to))
   :cljd (defn mcl-sub-list [mlist from to]
          (subvec @mlist from to)))

#?(:cljs (defn mcl-clear [mlist]
           (set! (.-length mlist) 0))
   :clj (defn mcl-clear [^Vector mlist]
          (.clear mlist))
   :cljd (defn mcl-clear [mlist]
          (reset! mlist [])))

;;;;;;;;;;;;;;;;;;;;;
;; Mutable hashmap ;;
;;;;;;;;;;;;;;;;;;;;;

#?(:clj (defn make-mutable-hashmap [] (HashMap.))
   :cljs (defn make-mutable-hashmap [] (atom {}))
   :cljd (defn make-mutable-hashmap [] (atom {})))

#?(:clj (defn mh->immutable-map [^HashMap mh]
          (into {} mh))
   :cljs (defn mh->immutable-map [mh]
           @mh)
   :cljd (defn mh->immutable-map [mh]
           @mh))

#?(:clj (defn mh-put [^HashMap mh k v]
          (.put mh k v))
   :cljs (defn mh-put [mh k v]
           (swap! mh assoc k v))
   :cljd (defn mh-put [mh k v]
           (swap! mh assoc k v)))

#?(:clj (defn mh-contains? [^HashMap mh k]
          (.containsKey mh k))
   :cljs (defn mh-contains? [mh k]
           (contains? @mh k))
   :cljd (defn mh-contains? [mh k]
           (contains? @mh k)))

#?(:clj (defn mh-get [^HashMap mh k]
          (.get mh k))
   :cljs (defn mh-get [mh k]
           (get @mh k))
   :cljd (defn mh-get [mh k]
           (get @mh k)))

#?(:clj (defn mh-remove [^HashMap mh k]
          (.remove mh k))
   :cljs (defn mh-remove [mh k]
           (swap! mh dissoc k))
   :cljd (defn mh-remove [mh k]
           (swap! mh dissoc k)))

#?(:clj (defn mh-keys [^HashMap mh]
          (.keySet mh))
   :cljs (defn mh-keys [mh]
           (keys @mh))
   :cljd (defn mh-keys [mh]
           (keys @mh)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutable concurrent hashmap ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj (defn make-mutable-concurrent-hashmap [] (ConcurrentHashMap.))
   :cljs (defn make-mutable-concurrent-hashmap [] (atom {}))
   :cljd (defn make-mutable-concurrent-hashmap [] (atom {})))

#?(:clj (defn mch->immutable-map [^ConcurrentHashMap mh]
          (into {} mh))
   :cljs (defn mch->immutable-map [mh]
           @mh)
   :cljd (defn mch->immutable-map [mh]
           @mh))

#?(:clj (defn mch-put [^ConcurrentHashMap mh k v]
          (.put mh k v))
   :cljs (defn mch-put [mh k v]
           (swap! mh assoc k v))
   :cljd (defn mch-put [mh k v]
           (swap! mh assoc k v)))

#?(:clj (defn mch-contains? [^ConcurrentHashMap mh k]
          (.containsKey mh k))
   :cljs (defn mch-contains? [mh k]
           (contains? @mh k))
   :cljd (defn mch-contains? [mh k]
           (contains? @mh k)))

#?(:clj (defn mch-keys [^ConcurrentHashMap mh]
          (enumeration-seq (.keys mh)))
   :cljs (defn mch-keys [mh]
           (keys @mh))
   :cljd (defn mch-keys [mh]
           (keys @mh)))

#?(:clj (defn mch-get [^ConcurrentHashMap mh k]
          (.get mh k))
   :cljs (defn mch-get [mh k]
           (get @mh k))
   :cljd (defn mch-get [mh k]
           (get @mh k)))

#?(:clj (defn mch-remove [^ConcurrentHashMap mh k]
          (.remove mh k))
   :cljs (defn mch-remove [mh k]
           (swap! mh dissoc k))
   :cljd (defn mch-remove [mh k]
           (swap! mh dissoc k)))

;;;;;;;;;;;;;
;; Int Map ;;
;;;;;;;;;;;;;

#?(:clj  (defn int-map [] (int-map/int-map))
   :cljs (defn int-map [] {})
   :cljd (defn int-map [] {}))
