(ns dev-tester-12)

(defn method-values []
  (let [parser ^[String] Integer/parseInt
        parsed (mapv parser ["1" "2" "3"])
        s (^[byte/1] String/new (byte-array [64 64]))
        i (Long/parseLong "123")]
    (+ (reduce + parsed)
       (count s)
       i)))

(defn instance-methods []
  (let [strs ["a" "b" "c"]]
    (mapv String/.toUpperCase strs)))

(defn functional-interfaces []
  ;; converts even? to Predicate
  (let [o (.removeIf (java.util.ArrayList. [1 2 3]) even?)
        ;; pull up to let binding
        ^java.util.function.Predicate p (fn [n] (even? n))]
    (.removeIf (java.util.ArrayList. [1 2 3]) p)

    ;; converts inc to UnaryOperator, uses new stream-seq!
    (->> (java.util.stream.Stream/iterate 1 inc) stream-seq! (take 10) doall)

    (mapv str
          (java.nio.file.Files/newDirectoryStream
           (.toPath (java.io.File. "."))
           #(-> ^java.nio.file.Path % .toFile .isDirectory)))))

(defn run []
  (+ (method-values)
     (count (instance-methods))
     (functional-interfaces)
     42))
