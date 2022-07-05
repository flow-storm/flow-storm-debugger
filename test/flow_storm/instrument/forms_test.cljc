(ns flow-storm.instrument.forms-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [flow-storm.api :as fs-api]
            [flow-storm.instrument.tester :as tester :refer [map->Circle map->Rectangle]]
            [flow-storm.tracer :as tracer]
            [flow-storm.utils :as utils]
            [flow-storm.trace-types :refer [map->FlowInitTrace map->FormInitTrace map->FnCallTrace map->BindTrace map->ExecTrace]])
  (:import [flow_storm.trace_types FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace]
           [flow_storm.instrument.tester Circle Rectangle]))

(defn print-traces [traces]
  (doseq [t traces]    
    (prn (pr-str t))))

(defmethod clojure.core/print-method java.lang.Object [v w]
  (.write w "#object[...]"))

;; Very dumb test but will probably help to catch regressions

(deftest integration-tracing-test
  (let [*sent-traces (atom [])
        res-before-inst (tester/do-all)]
    (with-redefs [tracer/enqueue-trace! (fn [trace] (swap! *sent-traces conj trace))
                  utils/get-monotonic-timestamp (constantly 0)
                  utils/get-current-thread-id (constantly 0)]

      (flow-storm.instrument.namespaces/instrument-files-for-namespaces #{"flow-storm.instrument.tester"} {})

      
      (let [res-after-inst (tester/do-all)

            expected-traces ["#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :form (defn do-all [] (let [map-shapes [{:type :circle, :r 5} {:type :rectangle, :w 7, :h 8}] rec-shapes [(->Circle 5) (->Rectangle 7 8) (->Triangle 10 5)]] (+ (perimeter-sum map-shapes) (area-sum rec-shapes) (reduce + (build-lazy))))), :ns \"flow-storm.instrument.tester\", :def-kind :defn, :mm-dispatch-val nil, :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :fn-name \"do-all\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [3], :timestamp 0, :symbol \"map-shapes\", :value [{:type :circle, :r 5} {:type :rectangle, :w 7, :h 8}]}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 1 3 0], :result #flow_storm.instrument.tester.Circle{:r 5}, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 1 3 1], :result #flow_storm.instrument.tester.Rectangle{:w 7, :h 8}, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 1 3 2], :result #object[...], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [3], :timestamp 0, :symbol \"rec-shapes\", :value [#flow_storm.instrument.tester.Circle{:r 5} #flow_storm.instrument.tester.Rectangle{:w 7, :h 8} #object[...]]}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2 1 1], :result [{:type :circle, :r 5} {:type :rectangle, :w 7, :h 8}], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :form (defn perimeter-sum [shapes] (reduce + (map perimeter shapes))), :ns \"flow-storm.instrument.tester\", :def-kind :defn, :mm-dispatch-val nil, :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :fn-name \"perimeter-sum\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [[{:type :circle, :r 5} {:type :rectangle, :w 7, :h 8}]], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"shapes\", :value [{:type :circle, :r 5} {:type :rectangle, :w 7, :h 8}]}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :coor [3 1], :result #object[...], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :coor [3 2 1], :result #object[...], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :coor [3 2 2], :result [{:type :circle, :r 5} {:type :rectangle, :w 7, :h 8}], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :coor [3 2], :result (31.41592653589793 30), :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id -856944765, :form (defmethod perimeter :circle [{:keys [r]}] (* 2 Math/PI r)), :ns \"flow-storm.instrument.tester\", :def-kind :defmethod, :mm-dispatch-val \":circle\", :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id -856944765, :fn-name \"perimeter\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [{:type :circle, :r 5}], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"r\", :value 5}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -856944765, :coor [4 2], :result 3.141592653589793, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -856944765, :coor [4 3], :result 5, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -856944765, :coor [4], :result 31.41592653589793, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -856944765, :coor [], :result 31.41592653589793, :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id 522775682, :form (defmethod perimeter :rectangle [{:keys [w h]}] (* 2 (+ w h))), :ns \"flow-storm.instrument.tester\", :def-kind :defmethod, :mm-dispatch-val \":rectangle\", :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id 522775682, :fn-name \"perimeter\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [{:type :rectangle, :w 7, :h 8}], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"w\", :value 7}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"h\", :value 8}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 522775682, :coor [4 2 1], :result 7, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 522775682, :coor [4 2 2], :result 8, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 522775682, :coor [4 2], :result 15, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 522775682, :coor [4], :result 30, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 522775682, :coor [], :result 30, :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :coor [3], :result 61.41592653589793, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1737122858, :coor [], :result 61.41592653589793, :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2 1], :result 61.41592653589793, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2 2 1], :result [#flow_storm.instrument.tester.Circle{:r 5} #flow_storm.instrument.tester.Rectangle{:w 7, :h 8} #object[...]], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :form (defn area-sum [shapes] (reduce + (map area shapes))), :ns \"flow-storm.instrument.tester\", :def-kind :defn, :mm-dispatch-val nil, :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :fn-name \"area-sum\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [[#flow_storm.instrument.tester.Circle{:r 5} #flow_storm.instrument.tester.Rectangle{:w 7, :h 8} #object[...]]], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"shapes\", :value [#flow_storm.instrument.tester.Circle{:r 5} #flow_storm.instrument.tester.Rectangle{:w 7, :h 8} #object[...]]}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :coor [3 1], :result #object[...], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :coor [3 2 1], :result #object[...], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :coor [3 2 2], :result [#flow_storm.instrument.tester.Circle{:r 5} #flow_storm.instrument.tester.Rectangle{:w 7, :h 8} #object[...]], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :coor [3 2], :result (78.53981633974483 56 25), :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :form (extend-protocol Shape Circle (area [c] (* Math/PI (Math/pow (:r c) 2)))), :ns \"flow-storm.instrument.tester\", :def-kind :extend-protocol, :mm-dispatch-val nil, :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :fn-name \"area\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [#flow_storm.instrument.tester.Circle{:r 5}], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"c\", :value #flow_storm.instrument.tester.Circle{:r 5}}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :coor [3 2 1], :result 3.141592653589793, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :coor [3 2 2 1 1], :result #flow_storm.instrument.tester.Circle{:r 5}, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :coor [3 2 2 1], :result 5, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :coor [3 2 2], :result 25.0, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :coor [3 2], :result 78.53981633974483, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1044453888, :coor [], :result 78.53981633974483, :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :form (defrecord Rectangle [w h] Shape (area [r] (* (:w r) (:h r)))), :ns \"flow-storm.instrument.tester\", :def-kind :extend-type, :mm-dispatch-val nil, :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :fn-name \"area\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [#flow_storm.instrument.tester.Rectangle{:w 7, :h 8}], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"r\", :value #flow_storm.instrument.tester.Rectangle{:w 7, :h 8}}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :coor [4 2 1 1], :result #flow_storm.instrument.tester.Rectangle{:w 7, :h 8}, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :coor [4 2 1], :result 7, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :coor [4 2 2 1], :result #flow_storm.instrument.tester.Rectangle{:w 7, :h 8}, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :coor [4 2 2], :result 8, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :coor [4 2], :result 56, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1274053196, :coor [], :result 56, :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id -2041173469, :form (extend-type Triangle Shape (area [t] (/ (* (.-b t) (.-h t)) 2))), :ns \"flow-storm.instrument.tester\", :def-kind :extend-type, :mm-dispatch-val nil, :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id -2041173469, :fn-name \"area\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [#object[...]], :timestamp 0}"
                             "#flow_storm.trace_types.BindTrace{:flow-id -1, :thread-id 0, :coor [], :timestamp 0, :symbol \"t\", :value #object[...]}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -2041173469, :coor [3 2 1 1], :result 10, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -2041173469, :coor [3 2 1 2], :result 5, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -2041173469, :coor [3 2 1], :result 50, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -2041173469, :coor [3 2], :result 25, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -2041173469, :coor [], :result 25, :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :coor [3], :result 159.53981633974485, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1906125853, :coor [], :result 159.53981633974485, :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2 2], :result 159.53981633974485, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2 3 1], :result #object[...], :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.FormInitTrace{:flow-id -1, :thread-id 0, :form-id -1549881545, :form (defn build-lazy ([] (build-lazy 0)) ([n] (lazy-seq (when (< n 2) (cons n (build-lazy (+ n 2))))))), :ns \"flow-storm.instrument.tester\", :def-kind :defn, :mm-dispatch-val nil, :timestamp 0}"
                             "#flow_storm.trace_types.FnCallTrace{:flow-id -1, :thread-id 0, :form-id -1549881545, :fn-name \"build-lazy\", :fn-ns \"flow-storm.instrument.tester\", :args-vec [], :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1549881545, :coor [2 1], :result (0), :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id -1549881545, :coor [], :result (0), :outer-form? true, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2 3 2], :result (0), :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2 3], :result 0, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3 2], :result 220.95574287564278, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [3], :result 220.95574287564278, :outer-form? nil, :timestamp 0}"
                             "#flow_storm.trace_types.ExecTrace{:flow-id -1, :thread-id 0, :form-id 1661037774, :coor [], :result 220.95574287564278, :outer-form? true, :timestamp 0}"
                             ]]
        
        (is (= res-before-inst res-after-inst)
            "The expression value after instrumentation should be the same as the expression value before it.")

        (doseq [[expected got] (map vector expected-traces (mapv pr-str @*sent-traces))]
          (is (= expected got) "Traces should be equal"))))))
