(ns flow-storm.util-types
  (:require [clojure.test :refer [deftest is]]))

(set! *warn-on-reflection* true)

(defprotocol SinglyLinkedListNodeP
  (get-item [_])
  (get-next-node [_]))

(deftype SinglyLinkedListIterator [^:unsynchronized-mutable node]

  java.util.Iterator

  (hasNext [_] (boolean node))
  (next [_]
    (let [e (get-item node)]
      (set! node (get-next-node node))
      e)))

(deftype SinglyLinkedListNode [item nextNode]

  SinglyLinkedListNodeP

  (get-item [_] item)
  (get-next-node [_] nextNode))

(deftype SinglyLinkedList [^:unsynchronized-mutable       head]

  java.util.List

  (add [_ e]
    (let [n (SinglyLinkedListNode. e head)]

      (set! head  n)
      true))

  (iterator [_]
    (SinglyLinkedListIterator. head))

  java.lang.Object
  (toString [_]
    (format "head: %s" head)))

(defn make-singly-linked-list []
  (SinglyLinkedList. nil))


(deftest basic-test
  (let [^SinglyLinkedList l (make-singly-linked-list)]
    (is (= [] (into [] (iterator-seq (.iterator l)))))

    (.add l 1)
    (.add l 2)
    (.add l 3)

    (is (= [3 2 1] (into [] (iterator-seq (.iterator l)))))))

(comment
  (def l (make-singly-linked-list))
(.add l 2)
(println l)
(def i (.iterator l))
(.hasNext i)
(.next i)
  )
