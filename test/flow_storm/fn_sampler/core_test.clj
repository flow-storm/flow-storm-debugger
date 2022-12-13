(ns flow-storm.fn-sampler.core-test
  (:require  [flow-storm.fn-sampler.core :as sut]
             [clojure.test :as t :refer [deftest is testing]]
             [dev-tester]))

(deftest type-dec-test
  (testing "Type description generation"

    (is (= {:type/name "clojure.lang.PersistentArrayMap"
            :type/type :map
            :map/kind :entity
            :map/domain {:person/name "java.lang.String"
                         :person/lastname "java.lang.String"
                         :age "java.lang.Long"}}
           (sut/type-desc {:person/name "Jhon"
                           :person/lastname "Doe"
                           :age 35}))
        "Type description for entity maps should be correct")

    (is (= {:type/name "clojure.lang.PersistentHashMap"
            :type/type :map
            :map/kind :regular
            :map/domain {"java.lang.Long" "java.lang.Long"}}
           (sut/type-desc (zipmap (range 20) (range 20))))
        "Type description for regular maps should be correct")

    (is (= {:type/name "clojure.lang.PersistentArrayMap"
            :type/type :map}
           (sut/type-desc {'a 10
                           3 8}))
        "Type description for any other maps should be correct")

    (is (= {:type/name "clojure.lang.PersistentVector",
            :type/type :seqable,
            :seq/first-elem-type "java.lang.Long"}
           (sut/type-desc [1 2 3 4]))
        "Type description for seqable collections should be correct")))

(deftest sample-test
  (testing "Sampler integration"
    (let [result (atom nil)
          result-name "dev-tester-flow-docs-0.1.0"]
      (with-redefs [sut/save-result (fn [fns-map {:keys [result-name]}]
                                      (reset! result {:fns-map fns-map
                                                      :result-name result-name}))]
        (let [{:keys [unsampled-fns]} (sut/sample
                                       {:result-name "dev-tester-flow-docs-0.1.0"
                                        :inst-ns-prefixes #{"dev-tester"}
                                        :verbose? true
                                        :print-unsampled? true}
                                       (dev-tester/boo [1 "hello" 6]))]

          (is (= result-name (:result-name @result))
              "Result name should be correct")

          (is (= '#{dev-tester/->ARecord
                    dev-tester/map->ARecord}
                 unsampled-fns)
              "Unsampled fns should be correct")

          (is (= '#:dev-tester{dummy-sum-macro {:args-types #{[{:type/name "clojure.lang.PersistentList",
                                                                :type/type :seqable,
                                                                :seq/first-elem-type "clojure.lang.Symbol"}
                                                               nil
                                                               #:type{:name "clojure.lang.Symbol"}
                                                               #:type{:name "java.lang.Long"}]},
                                                :return-types #{{:type/name "clojure.lang.Cons",
                                                                 :type/type :seqable,
                                                                 :seq/first-elem-type "clojure.lang.Symbol"}},
                                                :call-examples ({:args ["(dummy-sum-macro :...)" "nil" "a" "4"],
                                                                 :ret "(clojure.core/+ :...)"}),
                                                :var-meta {:ns dev-tester,
                                                           :name dummy-sum-macro,
                                                           :file "dev_tester.clj",
                                                           :column 1,
                                                           :line 7,
                                                           :arglists "([a b])"}},
                               other-function {:args-types #{[#:type{:name "java.lang.Long"}
                                                              #:type{:name "java.lang.Long"}]},
                                               :return-types #{#:type{:name "java.lang.Long"}},
                                               :call-examples ({:args ["4" "5"], :ret "19"}),
                                               :var-meta {:ns dev-tester,
                                                          :name other-function,
                                                          :file "dev_tester.clj",
                                                          :column 1,
                                                          :line 46,
                                                          :arglists ""}},
                               add {:args-types #{[#:type{:name "java.lang.Long"}]
                                                  [{:type/name "dev_tester.ARecord",
                                                    :type/type :map,
                                                    :map/domain {:n "java.lang.Long"},
                                                    :map/kind :entity}]},
                                    :return-types #{#:type{:name "java.lang.Long"}},
                                    :call-examples ({:args ["#dev_tester.ARecord{:n 5}"], :ret "1005"}
                                                    {:args ["729"], :ret "734"}),
                                    :var-meta {:ns dev-tester,
                                               :name add,
                                               :arglists "([x])",
                                               :doc nil}},
                               factorial {:args-types #{[#:type{:name "java.lang.Long"}]},
                                          :return-types #{#:type{:name "java.lang.Long"}},
                                          :call-examples ({:args ["2"], :ret "2"}
                                                          {:args ["1"], :ret "1"}
                                                          {:args ["0"], :ret "1"}),
                                          :var-meta {:ns dev-tester,
                                                     :name factorial,
                                                     :file "dev_tester.clj",
                                                     :column 1,
                                                     :line 10,
                                                     :arglists "([n])"}},
                               do-it {:args-types #{[#:type{:name "java.lang.String"}]
                                                    [#:type{:name "java.lang.Long"}]},
                                      :return-types #{#:type{:name "java.lang.Integer"}
                                                      #:type{:name "java.lang.Long"}},
                                      :call-examples ({:args ["hello"], :ret "5"}
                                                      {:args ["1"], :ret "1"}
                                                      {:args ["6"], :ret "720"}),
                                      :var-meta {:ns dev-tester,
                                                 :name do-it,
                                                 :file "dev_tester.clj",
                                                 :column 1,
                                                 :line 15,
                                                 :arglists ""}},
                               sub {:args-types #{[#:type{:name "java.lang.Long"}]},
                                    :return-types #{#:type{:name "java.lang.Long"}},
                                    :call-examples ({:args ["734"], :ret "692"}),
                                    :var-meta {:ns dev-tester,
                                               :name sub,
                                               :arglists "([x])",
                                               :doc nil}},
                               boo {:args-types #{[{:type/name "clojure.lang.PersistentVector",
                                                    :type/type :seqable,
                                                    :seq/first-elem-type "java.lang.Long"}]},
                                    :return-types #{#:type{:name "java.lang.Long"}},
                                    :call-examples ({:args ["[1 :...]"], :ret "6808"}),
                                    :var-meta {:ns dev-tester,
                                               :name boo,
                                               :file "dev_tester.clj",
                                               :column 1,
                                               :line 50,
                                               :arglists "([xs])"}}}
                 (:fns-map @result))))))))
