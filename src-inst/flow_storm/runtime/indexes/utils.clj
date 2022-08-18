(ns flow-storm.runtime.indexes.utils
  (:import [java.util ArrayList ArrayDeque]))

;; Mutable stack

(defn make-mutable-stack []
  (ArrayDeque.))

(defn ms-peek [^ArrayDeque mstack]
  (.peek mstack))

(defn ms-push [^ArrayDeque mstack elem]
  (.push mstack elem))

(defn ms-pop [^ArrayDeque mstack]
  (.pop mstack))

;; Mutable list

(defn make-mutable-list []
  (ArrayList.))

(defn ml-get [^ArrayList mlist idx]
  (.get mlist idx))

(defn ml-add [^ArrayList mlist elem]
  (.add mlist elem))

(defn ml-count [^ArrayList mlist]
  (.size mlist))
