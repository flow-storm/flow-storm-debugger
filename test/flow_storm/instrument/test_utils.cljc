(ns flow-storm.instrument.test-utils
  (:require [flow-storm.runtime.debuggers-api :as dbg-api]))

(defn match [v1 v2]
  (and (= (count v1) (count v2))
       (every? true? (map (fn [x1 x2]
                            (if (fn? x2)
                              (x2 x1)
                              (= x1 x2)))
                          v1 v2))))
#?(:clj
   (defmacro def-instrumentation-test [tname tdesc & {:keys [form print-collected? run-form should-return tracing]}]
     `(do
        (dbg-api/instrument* {} ~form)
        (clojure.test/deftest ~tname
          (clojure.test/testing ~tdesc
            (let [collected-traces# (atom [])]
              (with-redefs [flow-storm.tracer/trace-form-init (fn [& args#] (swap! collected-traces# conj (into [:trace-form-init] args#)))
                            flow-storm.tracer/trace-fn-call (fn [& args#] (swap! collected-traces# conj (into [:trace-fn-call] args#)) )
                            flow-storm.tracer/trace-bind (fn [& args#] (swap! collected-traces# conj (into [:trace-bind] args#)))
                            flow-storm.tracer/trace-expr-exec (fn [r# & args#] (swap! collected-traces# conj (into [:trace-expr-exec r#] args#)) r#)]

                (let [form-return# ~run-form]
                  
                  (when ~print-collected?
                    (println "Collected traces" (prn-str @collected-traces#)))

                  (clojure.test/is (= form-return# ~should-return) "Instrumentation should not break the form")

                  (clojure.test/is (= (count ~tracing)
                                      (count @collected-traces#))
                                   "Collected traces should match with provided traces")

                  (doseq [[coll-trace# target-trace#] (map vector @collected-traces# ~tracing)]
                    (println "Matching " (pr-str [coll-trace# target-trace#]))
                    (println "Result " (match coll-trace# target-trace#))
                    (clojure.test/is (match coll-trace# target-trace#) "Trace should match"))))))))))
