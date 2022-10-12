(ns flow-storm.instrument.forms-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure.core.async :as async]
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

(defn fn-str? [s]
  (and (string? s)
       (str/starts-with? s "fn-")))

;;;;;;;;;;;
;; Tests ;;
;;;;;;;;;;;

(def-instrumentation-test function-definition-test "Test defn instrumentation"
  
  :form (defn foo [a b] (+ a b))
  :run-form (foo 5 6)
  :should-return 11
  ;; :print-collected? true
  :tracing '[[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -1653360108} (defn foo [a b] (+ a b)) nil]
             [:trace-fn-call -1653360108 "flow-storm.instrument.forms-test" "foo" [5 6] nil]
             [:trace-bind a 5 {:coor nil, :form-id -1653360108} nil]
             [:trace-bind b 6 {:coor nil, :form-id -1653360108} nil]
             [:trace-expr-exec 5 {:coor [3 1], :form-id -1653360108} nil]
             [:trace-expr-exec 6 {:coor [3 2], :form-id -1653360108} nil]
             [:trace-expr-exec 11 {:coor [3], :form-id -1653360108} nil]
             [:trace-expr-exec 11 {:coor [], :form-id -1653360108, :outer-form? true} nil]])

(def-instrumentation-test function-definition-test2 "Test def fn* instrumentation"
  
  :form (def foo2 (fn [a b] (+ a b)))
  :run-form (foo2 5 6)
  :should-return 11
  ;; :print-collected? true
  :tracing '[[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -1100750367} (def foo2 (fn [a b] (+ a b))) nil]
             [:trace-fn-call -1100750367 "flow-storm.instrument.forms-test" "foo2" [5 6] nil]
             [:trace-bind a 5 {:coor [2], :form-id -1100750367} nil]
             [:trace-bind b 6 {:coor [2], :form-id -1100750367} nil]
             [:trace-expr-exec 5 {:coor [2 2 1], :form-id -1100750367} nil]
             [:trace-expr-exec 6 {:coor [2 2 2], :form-id -1100750367} nil]
             [:trace-expr-exec 11 {:coor [2 2], :form-id -1100750367} nil]
             [:trace-expr-exec 11 {:coor [], :form-id -1100750367, :outer-form? true} nil]])

(def-instrumentation-test anonymous-fn-test "Test anonymous function instrumentation"

  :form (defn foo3 [xs]
          (->> xs (map (fn [i] (inc i))) doall))
  :run-form (foo3 [1 2 3])
  :should-return [2 3 4]
  ;; :print-collected? true
  :tracing ['[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -313781675} (defn foo3 [xs] (->> xs (map (fn [i] (inc i))) doall)) nil]
            '[:trace-fn-call -313781675 "flow-storm.instrument.forms-test" "foo3" [[1 2 3]] nil]
            '[:trace-bind xs [1 2 3] {:coor nil, :form-id -313781675} nil]
            '[:trace-expr-exec [1 2 3] {:coor [3 1], :form-id -313781675} nil]
            '[:trace-expr-exec (2 3 4) {:coor [3 2], :form-id -313781675} nil]
            '[:trace-form-init {:dispatch-val nil, :def-kind nil, :ns "flow-storm.instrument.forms-test", :form-id -313781675} (defn foo3 [xs] (->> xs (map (fn [i] (inc i))) doall)) nil]
            [:trace-fn-call -313781675 "flow-storm.instrument.forms-test" fn-str? [1] nil]
            '[:trace-bind i 1 {:coor [3 2 1], :form-id -313781675} nil]
            '[:trace-expr-exec 1 {:coor [3 2 1 2 1], :form-id -313781675} nil]
            '[:trace-expr-exec 2 {:coor [3 2 1 2], :form-id -313781675} nil]
            '[:trace-expr-exec 2 {:coor [], :form-id -313781675, :outer-form? true} nil]
            '[:trace-form-init {:dispatch-val nil, :def-kind nil, :ns "flow-storm.instrument.forms-test", :form-id -313781675} (defn foo3 [xs] (->> xs (map (fn [i] (inc i))) doall)) nil]
            [:trace-fn-call -313781675 "flow-storm.instrument.forms-test" fn-str? [2] nil]
            '[:trace-bind i 2 {:coor [3 2 1], :form-id -313781675} nil]
            '[:trace-expr-exec 2 {:coor [3 2 1 2 1], :form-id -313781675} nil]
            '[:trace-expr-exec 3 {:coor [3 2 1 2], :form-id -313781675} nil]
            '[:trace-expr-exec 3 {:coor [], :form-id -313781675, :outer-form? true} nil]
            '[:trace-form-init {:dispatch-val nil, :def-kind nil, :ns "flow-storm.instrument.forms-test", :form-id -313781675} (defn foo3 [xs] (->> xs (map (fn [i] (inc i))) doall)) nil]
            [:trace-fn-call -313781675 "flow-storm.instrument.forms-test" fn-str? [3] nil]
            '[:trace-bind i 3 {:coor [3 2 1], :form-id -313781675} nil]
            '[:trace-expr-exec 3 {:coor [3 2 1 2 1], :form-id -313781675} nil]
            '[:trace-expr-exec 4 {:coor [3 2 1 2], :form-id -313781675} nil]
            '[:trace-expr-exec 4 {:coor [], :form-id -313781675, :outer-form? true} nil]
            '[:trace-expr-exec (2 3 4) {:coor [3], :form-id -313781675} nil]
            '[:trace-expr-exec (2 3 4) {:coor [], :form-id -313781675, :outer-form? true} nil]])

(def-instrumentation-test multi-arity-function-definition-test "Test multiarity function instrumentation"
  
  :form (defn bar
          ([a] (bar a 10))
          ([a b] (+ a b)))
  :run-form (bar 5)
  ;; :print-collected? true
  :should-return 15
  :tracing '[[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -1955739707} (defn bar ([a] (bar a 10)) ([a b] (+ a b))) nil]
             [:trace-fn-call -1955739707 "flow-storm.instrument.forms-test" "bar" [5] nil]
             [:trace-bind a 5 {:coor nil, :form-id -1955739707} nil]
             [:trace-expr-exec 5 {:coor [2 1 1], :form-id -1955739707} nil]
             [:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -1955739707} (defn bar ([a] (bar a 10)) ([a b] (+ a b))) nil]
             [:trace-fn-call -1955739707 "flow-storm.instrument.forms-test" "bar" [5 10] nil]
             [:trace-bind a 5 {:coor nil, :form-id -1955739707} nil]
             [:trace-bind b 10 {:coor nil, :form-id -1955739707} nil]
             [:trace-expr-exec 5 {:coor [3 1 1], :form-id -1955739707} nil]
             [:trace-expr-exec 10 {:coor [3 1 2], :form-id -1955739707} nil]
             [:trace-expr-exec 15 {:coor [3 1], :form-id -1955739707} nil]
             [:trace-expr-exec 15 {:coor [], :form-id -1955739707, :outer-form? true} nil]
             [:trace-expr-exec 15 {:coor [2 1], :form-id -1955739707} nil]
             [:trace-expr-exec 15 {:coor [], :form-id -1955739707, :outer-form? true} nil]])

(defmulti a-multi-method :type)

(def-instrumentation-test defmethod-test "Test defmethod instrumentation"

  :form (defmethod a-multi-method :some-type
          [m]
          (:x m))
  :run-form (a-multi-method {:type :some-type :x 42})
  :should-return 42
  ;; :print-collected? true
  :tracing '[[:trace-form-init {:dispatch-val ":some-type", :def-kind :defmethod, :ns "flow-storm.instrument.forms-test", :form-id 1944849841} (defmethod a-multi-method :some-type [m] (:x m)) nil]
             [:trace-fn-call 1944849841 "flow-storm.instrument.forms-test" "a-multi-method" [{:type :some-type, :x 42}] nil]
             [:trace-bind m {:type :some-type, :x 42} {:coor nil, :form-id 1944849841} nil]
             [:trace-expr-exec {:type :some-type, :x 42} {:coor [4 1], :form-id 1944849841} nil]
             [:trace-expr-exec 42 {:coor [4], :form-id 1944849841} nil]
             [:trace-expr-exec 42 {:coor [], :form-id 1944849841, :outer-form? true} nil]])

(defprotocol FooP
  (proto-fn-1 [x])
  (proto-fn-2 [x]))

(def-instrumentation-test defrecord-test "Test defrecord instrumentation"

  :form (defrecord ARecord [n]
          FooP
          (proto-fn-1 [_] (inc n))
          (proto-fn-2 [_] (dec n)))
  :run-form (+ (proto-fn-1 (->ARecord 5)) (proto-fn-2 (->ARecord 5)))
  :should-return 10
  ;; :print-collected? true
  :tracing ['[:trace-form-init {:dispatch-val nil, :def-kind :extend-type, :ns "flow-storm.instrument.forms-test", :form-id -1038078878} (defrecord ARecord [n] FooP (proto-fn-1 [_] (inc n)) (proto-fn-2 [_] (dec n))) nil]
            #?(:clj [:trace-fn-call -1038078878 "flow-storm.instrument.forms-test" "proto-fn-1" [(->ARecord 5)] nil]
               :cljs [:trace-fn-call -1038078878 "flow-storm.instrument.forms-test" "-flow-storm$instrument$forms-test$FooP$proto_fn_1$arity$1" [(->ARecord 5)] nil])
            '[:trace-expr-exec 5 {:coor [4 2 1], :form-id -1038078878} nil]
            '[:trace-expr-exec 6 {:coor [4 2], :form-id -1038078878} nil]
            '[:trace-expr-exec 6 {:coor [], :form-id -1038078878, :outer-form? true} nil]
            '[:trace-form-init {:dispatch-val nil, :def-kind :extend-type, :ns "flow-storm.instrument.forms-test", :form-id -1038078878} (defrecord ARecord [n] FooP (proto-fn-1 [_] (inc n)) (proto-fn-2 [_] (dec n))) nil]
            #?(:clj [:trace-fn-call -1038078878 "flow-storm.instrument.forms-test" "proto-fn-2" [(->ARecord 5)] nil]
               :cljs [:trace-fn-call -1038078878 "flow-storm.instrument.forms-test" "-flow-storm$instrument$forms-test$FooP$proto_fn_2$arity$1" [(->ARecord 5)] nil])
            '[:trace-expr-exec 5 {:coor [5 2 1], :form-id -1038078878} nil]
            '[:trace-expr-exec 4 {:coor [5 2], :form-id -1038078878} nil]
            '[:trace-expr-exec 4 {:coor [], :form-id -1038078878, :outer-form? true} nil]])

(defrecord BRecord [n])

#_(def-instrumentation-test extend-protocol-test "Test extend-protocol instrumentation"

  :form (extend-protocol FooP
          BRecord
          (proto-fn-1 [this] (inc (:n this)))
          (proto-fn-2 [this] (dec (:n this))))
  :run-form (+ (proto-fn-1 (->BRecord 5)) (proto-fn-2 (->BRecord 5)))
  :should-return 10
  :print-collected? true
  :tracing ['[:trace-form-init {:dispatch-val nil, :def-kind :extend-protocol, :ns "flow-storm.instrument.forms-test", :form-id 969319502} (extend-protocol FooP BRecord (proto-fn-1 [this] (inc (:n this))) (proto-fn-2 [this] (dec (:n this)))) nil]
            [:trace-fn-call 969319502 "flow-storm.instrument.forms-test" "proto-fn-1" [(->BRecord 5)] nil]
            [:trace-bind 'this (->BRecord 5) {:coor nil, :form-id 969319502} nil]
            [:trace-expr-exec (->BRecord 5) {:coor [3 2 1 1], :form-id 969319502} nil]
            '[:trace-expr-exec 5 {:coor [3 2 1], :form-id 969319502} nil]
            '[:trace-expr-exec 6 {:coor [3 2], :form-id 969319502} nil]
            '[:trace-expr-exec 6 {:coor [], :form-id 969319502, :outer-form? true} nil]
            '[:trace-form-init {:dispatch-val nil, :def-kind :extend-protocol, :ns "flow-storm.instrument.forms-test", :form-id 969319502} (extend-protocol FooP BRecord (proto-fn-1 [this] (inc (:n this))) (proto-fn-2 [this] (dec (:n this)))) nil]
            [:trace-fn-call 969319502 "flow-storm.instrument.forms-test" "proto-fn-2" [(->BRecord 5)] nil]
            [:trace-bind 'this (->BRecord 5) {:coor nil, :form-id 969319502} nil]
            [:trace-expr-exec (->BRecord 5) {:coor [4 2 1 1], :form-id 969319502} nil]
            '[:trace-expr-exec 5 {:coor [4 2 1], :form-id 969319502} nil]
            '[:trace-expr-exec 4 {:coor [4 2], :form-id 969319502} nil]
            '[:trace-expr-exec 4 {:coor [], :form-id 969319502, :outer-form? true} nil]])

(defrecord CRecord [n])

#_(def-instrumentation-test extend-type-test "Test extend-type instrumentation"

  :form (extend-type CRecord            
          FooP
          (proto-fn-1 [this] (inc (:n this)))
          (proto-fn-2 [this] (dec (:n this))))
  :run-form (+ (proto-fn-1 (->CRecord 5)) (proto-fn-2 (->CRecord 5)))
  :should-return 10
  :print-collected? true
  :tracing ['[:trace-form-init {:dispatch-val nil, :def-kind :extend-type, :ns "flow-storm.instrument.forms-test", :form-id -1521217400} (extend-type CRecord FooP (proto-fn-1 [this] (inc (:n this))) (proto-fn-2 [this] (dec (:n this)))) nil]
            [:trace-fn-call -1521217400 "flow-storm.instrument.forms-test" "proto-fn-1" [(->CRecord 5)] nil]
            [:trace-bind 'this (->CRecord 5) {:coor nil, :form-id -1521217400} nil]
            [:trace-expr-exec (->CRecord 5) {:coor [3 2 1 1], :form-id -1521217400} nil]
            '[:trace-expr-exec 5 {:coor [3 2 1], :form-id -1521217400} nil]
            '[:trace-expr-exec 6 {:coor [3 2], :form-id -1521217400} nil]
            '[:trace-expr-exec 6 {:coor [], :form-id -1521217400, :outer-form? true} nil]
            '[:trace-form-init {:dispatch-val nil, :def-kind :extend-type, :ns "flow-storm.instrument.forms-test", :form-id -1521217400} (extend-type CRecord FooP (proto-fn-1 [this] (inc (:n this))) (proto-fn-2 [this] (dec (:n this)))) nil]
            [:trace-fn-call -1521217400 "flow-storm.instrument.forms-test" "proto-fn-2" [(->CRecord 5)] nil]
            [:trace-bind 'this (->CRecord 5) {:coor nil, :form-id -1521217400} nil]
            [:trace-expr-exec (->CRecord 5) {:coor [4 2 1 1], :form-id -1521217400} nil]
            '[:trace-expr-exec 5 {:coor [4 2 1], :form-id -1521217400} nil]
            '[:trace-expr-exec 4 {:coor [4 2], :form-id -1521217400} nil]
            '[:trace-expr-exec 4 {:coor [], :form-id -1521217400, :outer-form? true} nil]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Only testing clojure.core.async/go on Clojure since we can't block in ClojureScript and our testing system ;;
;; doesn't support async yet.                                                                                 ;;
;; We aren't doing anything special for ClojureScript go blocks, so testing on one should be enough           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn async-chan? [x]
     (instance? clojure.core.async.impl.channels.ManyToManyChannel x)))

#?(:clj
   (defn async-chan-vec? [v]
     (and (vector? v)
          (every? async-chan? v))))

#?(:clj
   (def-instrumentation-test core-async-go-block-test "Test clojure.core.async/go instrumentation"

     :form (defn some-async-fn []
             (let [in-ch (async/chan)
                   out-ch (async/go
                            (loop []
                              (when-some [x (async/<! in-ch)]          
                                (recur))))]
               
               [in-ch out-ch]))
     :run-form (let [[in-ch out-ch] (some-async-fn)]
                 (async/>!! in-ch "hello")
                 (async/>!! in-ch "bye")
                 (async/close! in-ch)
                 (async/<!! out-ch)
                 nil)
     :should-return nil
     ;; :print-collected? true
     :tracing [[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id 622089785} '(defn some-async-fn [] (let [in-ch (async/chan) out-ch (async/go (loop [] (when-some [x (async/<! in-ch)] (recur))))] [in-ch out-ch])) nil]
               [:trace-fn-call 622089785 "flow-storm.instrument.forms-test" "some-async-fn" [] nil]
               [:trace-expr-exec async-chan? {:coor [3 1 1], :form-id 622089785} nil]
               [:trace-bind 'in-ch async-chan? {:coor [3], :form-id 622089785} nil]
               [:trace-bind 'out-ch async-chan? {:coor [3], :form-id 622089785} nil]
               [:trace-expr-exec async-chan? {:coor [3 2 0], :form-id 622089785} nil]
               [:trace-expr-exec async-chan? {:coor [3 2 1], :form-id 622089785} nil]
               [:trace-expr-exec async-chan-vec? {:coor [3], :form-id 622089785} nil]
               [:trace-expr-exec async-chan-vec? {:coor [], :form-id 622089785, :outer-form? true} nil]
               [:trace-expr-exec async-chan? {:coor [3 1 3 1 2 1 1 1], :form-id 622089785} nil]
               [:trace-expr-exec "hello" {:coor [3 1 3 1 2 1 1], :form-id 622089785} nil]
               [:trace-bind 'x "hello" {:coor nil, :form-id 622089785} nil]
               [:trace-expr-exec async-chan? {:coor [3 1 3 1 2 1 1 1], :form-id 622089785} nil]
               [:trace-expr-exec "bye" {:coor [3 1 3 1 2 1 1], :form-id 622089785} nil]
               [:trace-bind 'x "bye" {:coor nil, :form-id 622089785} nil]
               [:trace-expr-exec async-chan? {:coor [3 1 3 1 2 1 1 1], :form-id 622089785} nil]
               [:trace-expr-exec nil {:coor [3 1 3 1 2 1 1], :form-id 622089785} nil]
               [:trace-expr-exec nil {:coor [3 1 3 1], :form-id 622089785} nil]]
     ))

#?(:clj
   (def-instrumentation-test core-async-go-loop-test "Test clojure.core.async/go-loop instrumentation"

     :form (defn some-other-async-fn []
             (let [in-ch (async/chan)
                   out-ch (async/go-loop []
                            (when-some [x (async/<! in-ch)]          
                              (recur)))]               
               [in-ch out-ch]))
     :run-form (let [[in-ch out-ch] (some-other-async-fn)]
                 (async/>!! in-ch "hello")
                 (async/>!! in-ch "bye")
                 (async/close! in-ch)
                 (async/<!! out-ch)
                 nil)
     :should-return nil
     ;; :print-collected? true
     :tracing [[:trace-form-init {:dispatch-val nil, :def-kind :defn, :ns "flow-storm.instrument.forms-test", :form-id -29619953} '(defn some-other-async-fn [] (let [in-ch (async/chan) out-ch (async/go-loop [] (when-some [x (async/<! in-ch)] (recur)))] [in-ch out-ch])) nil]
               [:trace-fn-call -29619953 "flow-storm.instrument.forms-test" "some-other-async-fn" [] nil]
               [:trace-expr-exec async-chan? {:coor [3 1 1], :form-id -29619953} nil]
               [:trace-bind 'in-ch async-chan? {:coor [3], :form-id -29619953} nil]
               [:trace-bind 'out-ch async-chan? {:coor [3], :form-id -29619953} nil]
               [:trace-expr-exec async-chan? {:coor [3 2 0], :form-id -29619953} nil]
               [:trace-expr-exec async-chan? {:coor [3 2 1], :form-id -29619953} nil]
               [:trace-expr-exec async-chan-vec? {:coor [3], :form-id -29619953} nil]
               [:trace-expr-exec async-chan-vec? {:coor [], :form-id -29619953, :outer-form? true} nil]
               [:trace-expr-exec async-chan? {:coor [3 1 3 2 1 1 1], :form-id -29619953} nil]
               [:trace-expr-exec "hello" {:coor [3 1 3 2 1 1], :form-id -29619953} nil]
               [:trace-bind 'x "hello" {:coor nil, :form-id -29619953} nil]
               [:trace-expr-exec async-chan? {:coor [3 1 3 2 1 1 1], :form-id -29619953} nil]
               [:trace-expr-exec "bye" {:coor [3 1 3 2 1 1], :form-id -29619953} nil]
               [:trace-bind 'x "bye" {:coor nil, :form-id -29619953} nil]
               [:trace-expr-exec async-chan? {:coor [3 1 3 2 1 1 1], :form-id -29619953} nil]
               [:trace-expr-exec nil {:coor [3 1 3 2 1 1], :form-id -29619953} nil]]))
