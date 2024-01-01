(ns dev-tester)

;;;;;;;;;;;;;;;;;;;;;;;
;; Some testing code ;;
;;;;;;;;;;;;;;;;;;;;;;;

(defn uncatched-throw []
  (let [a (+ 1 2)]
    (throw (Exception. "damn"))
    (+ a 3)))

(defn throw-forwarder []
  (uncatched-throw))

(defn catcher []
  (try
    (throw-forwarder)
    (catch Exception _ 45)))

(defmacro dummy-sum-macro [a b]
  `(+ ~a ~b))

(defn factorial [n]
  (if (zero? n)
    1
    (* n (factorial (dec n)))))

(defmulti do-it type)

(defmethod do-it java.lang.Long
  [l]
  (factorial l))

(defmethod do-it java.lang.String
  [s]
  (count s))

(defprotocol Adder
  (add [x]))

(defrecord ARecord [n]

  Adder
  (add [_] (+ n 1000)))

(deftype AType [^int n]
  Adder
  (add [_] (int (+ n 42))))

(defprotocol Suber
  (sub [x]))

(extend-protocol Adder

  java.lang.Long
  (add [l] (+ l 5)))

(extend-type java.lang.Long

  Suber
  (sub [l] (- l 42)))

(def other-function
  (fn [a b]
    (+ a b 10)))

(defn inc-atom [a]
  (swap! a inc))

(defn hinted [a ^long b c ^long d]
  (+ a c (+ b d)))

(defn lorem-ipsum [arg1 arg2 arg3]
  (str "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
        "Proin vehicula euismod ligula, eu consectetur tortor facilisis vel."
        "Pellentesque " arg1 " elit nec, " arg2 " sagittis turpis. "
        "Duis fermentum mi et eros vehicula, id fringilla justo tincidunt."
        "Integer " arg3 " ut justo in dignissim. "
        "Proin ac ex eu sem sollicitudin hendrerit."))

(defn generate-lorem-ipsum []
  (let [long-arg1 (apply str (repeat 120 "a"))
        long-arg2 (apply str (repeat 120 "b"))
        long-arg3 (apply str (repeat 120 "c"))]

    (lorem-ipsum long-arg1 long-arg2 long-arg3)))

(defn boo [xs]
  (let [a 25
        yy (other-function 4 5)
        hh (range)
        *a (atom 10)
        _ (inc-atom *a)
        xx @*a
        b (dummy-sum-macro a 4)
        m ^{:meta1 true :meta2 "nice-meta-value"} {:a 5 :b ^:interesting-vector [1 2 3]}
        mm (assoc m :c 10)
        c (+ a b 7)
        d (add (->ARecord 5))
        e (add (AType. 10))
        j (loop [i 100
                 sum 0]
            (if (> i 0)
              (recur (dec i) (+ sum i))
              sum))
        z (catcher)
        li (generate-lorem-ipsum)]
    (->> xs
         (map (fn [x] (+ 1 (do-it x))))
         (reduce + )
         add
         sub
         (+ c d j e (hinted a c d j)))))

(defn run []
  (boo [1 "hello" 4]))

(defn run-parallel []
  (->> (range 4)
       (pmap (fn [i] (factorial i)))
       (reduce +)))
