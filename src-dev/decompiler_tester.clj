(ns decompiler-tester)

^:clojure.storm/collect-emitted
(defn sum [a b c]
  (+ a b c))

^:clojure.storm/collect-emitted
(defn choose [x]
  (if (< x 10)
    (inc x)
    (dec x)))

^:clojure.storm/collect-emitted
(defn a-bunch-of-sums [a b]
  (let [m {:y 20}
        x (+ a b)
        y #_(:y m) #_(get m :y) (.get m :y)
        z (+ x a b)]
    (+ x y z)))

^:clojure.storm/collect-emitted
(defn multi-arity
  ([] 0)
  ([a] a)
  ([a b] (+ a b)))

(defn a-fn [] 5)

^:clojure.storm/collect-emitted
(defn a-caller [] ;; could be direct linked
  (a-fn))

^:clojure.storm/collect-emitted
(defn exceptional []
  (try
    (+ 1 2)
    (catch ArithmeticException ae
      (println "Wrong arithmetic"))
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
