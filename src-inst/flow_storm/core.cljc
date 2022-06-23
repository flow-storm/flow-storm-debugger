(ns flow-storm.core
  (:require [flow-storm.instrument.trace-types :as trace-types]
            [clojure.pprint :as pp]))

(defn get-remote-value-command [{:keys [vid print-length print-level print-meta? pprint? nth-elems]}]
  (let [v (trace-types/get-reference-value vid)
        print-fn (if pprint? pp/pprint print)]
    
    (with-out-str
      (binding [*print-level* print-level
                *print-length* print-length
                *print-meta* print-meta?]

        (if nth-elems

          (let [max-idx (dec (count v))
                nth-valid-elems (filter #(<= % max-idx) nth-elems)]
            (doseq [n nth-valid-elems]
              (print-fn (nth v n))
              (print " ")))

          (print-fn v))))))
