(ns flow-storm.instrument.test-utils
  (:require [flow-storm.runtime.debuggers-api :as dbg-api]))

(defmacro def-instrumentation-test [tname tdesc & {:keys [form run-form target-traces]}]
  `(do
     (dbg-api/instrument* {} ~form)
     (clojure.test/deftest ~tname
       (clojure.test/testing ~tdesc
         (let [collected-traces# (atom [])]
           (with-redefs [flow-storm.tracer/trace-form-init (fn [& args#] (swap! collected-traces# conj (into [:trace-form-init] args#)))
                         flow-storm.tracer/trace-fn-call (fn [& args#] (swap! collected-traces# conj (into [:trace-fn-call] args#)) )
                         flow-storm.tracer/trace-bind (fn [& args#] (swap! collected-traces# conj (into [:trace-bind] args#)))
                         flow-storm.tracer/trace-expr-exec (fn [r# & args#] (swap! collected-traces# conj (into [:trace-expr-exec] args#)) r#)]

             ~run-form

             #_(binding [*out* *err*] (prn ">>>>>>>>" @collected-traces#))

             (doseq [[coll-trace# target-trace#] (map vector ~target-traces @collected-traces#)]
               (clojure.test/is (= coll-trace# target-trace#)))))))))
