(ns dev-tester-12)

(defn method-values []
  (let [parser ^[String] Integer/parseInt
        parsed (mapv parser ["1" "2" "3"])
        s (^[byte*] String/new (byte-array [64 64]))
        i (Long/parseLong "123")]
    (+ (reduce + parsed)
       (count s)
       i)))

(defn run []
  (+ (method-values)
     42))
