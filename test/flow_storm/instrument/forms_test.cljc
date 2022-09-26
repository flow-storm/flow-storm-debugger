(ns flow-storm.instrument.forms-test
  (:require [clojure.test :refer [deftest is testing]]
            #?@(:clj [[flow-storm.instrument.forms :as inst-forms]
                      [flow-storm.runtime.debuggers-api :as dbg-api]
                      [flow-storm.api :as fsa]
                      [flow-storm.instrument.test-utils :refer [def-instrumentation-test]]]
                :cljs [[flow-storm.instrument.forms :as inst-forms]
                       [flow-storm.runtime.debuggers-api :as dbg-api :include-macros true]
                       [flow-storm.api :as fsa]
                       [flow-storm.instrument.test-utils :refer [def-instrumentation-test] :include-macros true]
                       ])
            [clojure.string :as str]))

;;;;;;;;;;;
;; Tests ;;
;;;;;;;;;;;

(def-instrumentation-test function-definition-test "Function definition"
  
  :form (defn foo [a b] (+ a b))
  :run-form (foo 5 6)
  
  :target-traces '[[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -1653360108} (defn foo [a b] (+ a b)) nil]
                   [:trace-fn-call -1653360108 "flow-storm.instrument.forms-test" "foo" [5 6] nil]
                   [:trace-bind a 5 {:coor nil, :form-id -1653360108} nil]
                   [:trace-bind b 6 {:coor nil, :form-id -1653360108} nil]
                   [:trace-expr-exec {:coor [3 1], :form-id -1653360108} nil]
                   [:trace-expr-exec {:coor [3 2], :form-id -1653360108} nil]
                   [:trace-expr-exec {:coor [3], :form-id -1653360108} nil]
                   [:trace-expr-exec {:coor [], :form-id -1653360108, :outer-form? true} nil]])

(def-instrumentation-test multi-arity-function-definition-test "Multiarity function definition"
  
  :form (defn bar
          ([a] (bar a 10))
          ([a b] (+ a b)))
  :run-form (bar 5)
  
  :target-traces '[[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -1955739707} (defn bar ([a] (bar a 10)) ([a b] (+ a b))) nil]
                   [:trace-fn-call -1955739707 "flow-storm.instrument.forms-test" "bar" [5] nil]
                   [:trace-bind a 5 {:coor nil, :form-id -1955739707} nil]
                   [:trace-expr-exec {:coor [2 1 1], :form-id -1955739707} nil]
                   [:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -1955739707} (defn bar ([a] (bar a 10)) ([a b] (+ a b))) nil]
                   [:trace-fn-call -1955739707 "flow-storm.instrument.forms-test" "bar" [5 10] nil]
                   [:trace-bind a 5 {:coor nil, :form-id -1955739707} nil]
                   [:trace-bind b 10 {:coor nil, :form-id -1955739707} nil]
                   [:trace-expr-exec {:coor [3 1 1], :form-id -1955739707} nil]
                   [:trace-expr-exec {:coor [3 1 2], :form-id -1955739707} nil]
                   [:trace-expr-exec {:coor [3 1], :form-id -1955739707} nil]
                   [:trace-expr-exec {:coor [], :form-id -1955739707, :outer-form? true} nil]
                   [:trace-expr-exec {:coor [2 1], :form-id -1955739707} nil]
                   [:trace-expr-exec {:coor [], :form-id -1955739707, :outer-form? true} nil]])


