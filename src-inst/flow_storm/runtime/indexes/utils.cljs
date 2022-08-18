(ns flow-storm.runtime.indexes.utils)

;; Mutable stack

(defn make-mutable-stack []
  #js [])

(defn ms-peek [mstack]
  (aget mstack (dec (.-length mstack))))

(defn ms-push [mstack elem]
  (.push mstack elem))

(defn ms-pop [mstack]
  (.pop mstack))

;; Mutable list

(defn make-mutable-list []
  #js [])

(defn ml-get [mlist idx]
  (aget mlist idx))

(defn ml-add [mlist elem]
  (.push mlist elem))

(defn ml-count [mlist]
  (.-length mlist))
