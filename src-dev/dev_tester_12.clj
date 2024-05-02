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

(defn run []
  (+ (method-values)
     (count (instance-methods))
     42))
