(ns flow-storm.runtime.indexes.utils  
  #?(:clj (:import [java.util ArrayList ArrayDeque HashMap])))

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
