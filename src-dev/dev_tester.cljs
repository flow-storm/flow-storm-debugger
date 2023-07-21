(ns dev-tester
  (:require-macros [dev-tester :refer [dummy-sum-macro]]))

(defn factorial [n]
  (if (zero? n)
    1
    (* n (factorial (dec n)))))

(defn multi-arity
  ([a] (multi-arity a 10))
  ([a b] (+ a b)))

(defmulti do-it #(.-name (type %)))

(defmethod do-it "Number"
  [l]
  (factorial l))

(defmethod do-it "String"
  [s]
  (count s))

(defprotocol Adder
  (add [x]))

(defprotocol Suber
  (sub [x]))

(defrecord ARecord [n]

  Adder
  (add [_] (+ n 1000)))

(extend-protocol Adder

  number
  (add [l] (+ l 5)))

(extend-type number

  Suber
  (sub [l] (- l 42)))

(extend-type ARecord

  Suber
  (sub [r] (+ 42 (* 2 32))))

(defn boo [xs]
  (let [a (dummy-sum-macro 25 8)
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
  (js/console.log (boo [2 "hello" 8])))
