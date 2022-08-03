(ns dev
  (:require [flow-storm.json-serializer :as ser]
            [flow-storm.api :as fs-api]
            [flow-storm.core :as fs-core])
  (:require-macros [flow-storm.core]))

#trace
(defn factorial [n]
  (if (zero? n)
    1
    (* n (factorial (dec n)))))

#trace
(defn multi-arity
  ([a] (multi-arity a 10))
  ([a b] (+ a b)))

(defmulti do-it #(.-name (type %)))

#trace
(defmethod do-it "Number"
  [l]
  (factorial l))

#trace
(defmethod do-it "String"
  [s]
  (count s))

(defprotocol Adder
  (add [x]))

(defprotocol Suber
  (sub [x]))

#trace
(defrecord ARecord [n]

  Adder
  (add [_] (+ n 1000)))

#trace
(extend-protocol Adder

  number
  (add [l] (+ l 5)))

#trace
(extend-type number

  Suber
  (sub [l] (- l 42)))

#trace
(extend-type ARecord

  Suber
  (sub [r] (+ 42 (* 2 32))))

#trace
(defn boo [xs]
  (let [a 25
        b (multi-arity a)
        c (+ a b 7)
        d (add (->ARecord 5))
        j (loop [i 100
                 sum 0]
            (if (> i 0)
              (recur (dec i) (+ sum i))
              sum))]
    (->> xs
         (map (fn [x] (+ 1 (do-it x))))
         (reduce + )
         add
         sub
         (+ c d j))))

(defn -main [& args]
  (fs-api/remote-connect {:on-connected (fn []
                                          (js/console.log "Connected!!")
                                          #rtrace (js/console.log (boo [2 "hello" 8])))}))
