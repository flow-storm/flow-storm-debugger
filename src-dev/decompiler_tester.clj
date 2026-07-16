(ns decompiler-tester)

^:clojure.storm/collect-emitted
(defn sum [a b c]
  (+ a b c))

^:clojure.storm/collect-emitted
(defn choose [x]
  (if (< x 10)
    "Less than 10"
    "Higher or equal than 10"))

^:clojure.storm/collect-emitted
(defn a-bunch-of-sums [a b]
  (let [m {:y 20}
        x (+ a b)
        y (:y m)
        z (+ x a b)]
    (+ x y z)))

^:clojure.storm/collect-emitted
(defn a-bunch-of-sums-map-get [a b]
  (let [m {:y 20}
        x (+ a b)
        y (get m :y)
        z (+ x a b)]
    (+ x y z)))

^:clojure.storm/collect-emitted
(defn multi-arity
  ([] "empty args")
  ([a] "one arg")
  ([a b] "two args"))

(defn a-fn [] 5)

^:clojure.storm/collect-emitted
(defn a-caller []
  (a-fn))

^:clojure.storm/collect-emitted
(defn exceptional []
  (try
    (println "Great")
    (catch Exception e
      (println "Wrong"))))

^:clojure.storm/collect-emitted
(defn casey [x]
  (case x
    :a (name x)
    :b (namespace x)
    "unhandled"))

^:clojure.storm/collect-emitted
(defn a-more-complex [coll]
  (let [mapper (fn [x]
                 (let [y (* x 2)
                       z (* y 2)]
                   (+ z x y)))]
    (mapv mapper coll)))

^:clojure.storm/collect-emitted
(defn arrowed []
  (-> 10
      (+ 20)
      (- 10)
      (* 30)))


^:clojure.storm/collect-emitted
(deftype MyType [a]
  Object
  (toString [_] "hello"))
