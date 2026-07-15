(ns flow-storm.runtime.forms-bytecode-emission
  (:require [flow-storm.runtime.events :as rt-events]))

(defn- parse-class-methods [class-emissions]
  (->> class-emissions
       (partition-by #(= :method (:emitted/type %)))
       (partition 2)
       (reduce (fn [methods [[method-def] method-emissions]]
                 (let [vars (->> method-emissions
                                 (keep (fn [e]
                                         (when (= :var (:emitted/type e))
                                           [(:index e) e])))
                                 (into {}))
                       inst-without-vars (->> method-emissions
                                              (remove #(= :var (:emitted/type %))))

                       labels-renames (->> inst-without-vars
                                           (reduce (fn [labels-ref {:keys [label start-label end-label labels default-label handler-label]}]
                                                     (cond-> labels-ref
                                                       label         (conj label)
                                                       start-label   (conj start-label)
                                                       end-label     (conj end-label)
                                                       default-label (conj default-label)
                                                       handler-label (conj handler-label)
                                                       (seq labels)  (into labels)))
                                                   #{})
                                           (map-indexed (fn [i lbl] [lbl (str "L" i)]))
                                           (into {}))
                       renamed-insts (reduce (fn [insts {:keys [label start-label end-label labels default-label handler-label] :as inst}]
                                               (conj insts
                                                     (cond-> inst
                                                       (:label/name inst) (assoc :label/name (labels-renames (:label/name inst)))
                                                       label              (assoc :label (labels-renames label))
                                                       start-label        (assoc :start-label (labels-renames start-label))
                                                       end-label          (assoc :end-label (labels-renames end-label))
                                                       default-label      (assoc :default-label (labels-renames default-label))
                                                       handler-label      (assoc :handler-label (labels-renames handler-label))
                                                       (seq labels)       (assoc :labels (mapv labels-renames labels)))))
                                             []
                                             inst-without-vars)
                       try-catch-blocks (filterv #(= :try-catch-block (:emitted/type %)) renamed-insts)
                       clean-inst (->> renamed-insts
                                       (remove #(= :try-catch-block (:emitted/type %)))
                                       (remove #(and (= :label (:emitted/type %))
                                                     (nil? (:label/name %))))
                                       (into []))]
                   (conj methods {:method method-def
                                  :vars vars
                                  :try-catch-blocks try-catch-blocks
                                  :instructions clean-inst})))
               [])))

(defn- parse-emissions-classes [form-id emissions]
  (let [classes (->> emissions
                     (partition-by #(= :class (:emitted/type %)))
                     (partition 2)
                     (reduce (fn [classes [[class-def] class-emissions]]
                               (conj classes {:class class-def
                                              :fields (filter #(= :field (:emitted/type %)) class-emissions)
                                              :methods (->> class-emissions
                                                            (remove #(= :field (:emitted/type %)))
                                                            (parse-class-methods))}))
                             []))]
    {:form-id form-id
     :emitted-coords  (->> emissions
                           (map :coord)
                           (into #{}))
     :form-classes classes}))

(defn- normalize-coords [emitted-data]
  (mapv (fn [d] (update d :coord #(or % []))) emitted-data))

(defn handle-form-bytecode-emitted [form-id emitted-data]
  (let [form-classes (->> emitted-data
                          normalize-coords
                          (parse-emissions-classes form-id))]
    (rt-events/publish-event!
       (rt-events/make-form-bytecode-emitted-event form-classes))))



#_(def fb
  [{:emitted/type :class,
  :class/name "user$sum",
  :class/signature nil,
  :class/super-name "clojure/lang/AFunction",
  :class/interfaces nil,
  :coord [],
  :idx 0,
  :form-id -1644763934}
 {:emitted/type :method,
  :method/name "<init>",
  :method/descriptor "()V",
  :method/signature nil,
  :method/exceptions nil,
  :coord [],
  :idx 1,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1371129741",
  :coord [],
  :idx 2,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1466296368",
  :coord [],
  :idx 3,
  :form-id -1644763934}
 {:instruction/op :ALOAD,
  :var 0,
  :emitted/type :instruction,
  :coord [],
  :idx 4,
  :form-id -1644763934}
 {:instruction/op :INVOKESPECIAL,
  :owner "clojure/lang/AFunction",
  :name "<init>",
  :descriptor "()V",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 5,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1205077419",
  :coord [],
  :idx 6,
  :form-id -1644763934}
 {:instruction/op :RETURN,
  :emitted/type :instruction,
  :coord [],
  :idx 7,
  :form-id -1644763934}
 {:emitted/type :method,
  :method/name "invokeStatic",
  :method/descriptor
  "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
  :method/signature nil,
  :method/exceptions [],
  :coord [],
  :idx 8,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L379394929",
  :coord [],
  :idx 9,
  :form-id -1644763934}
 {:instruction/op :GETSTATIC,
  :field-owner "user$sum",
  :field-name "const__2",
  :field-descriptor "Lclojure/lang/AFn;",
  :emitted/type :instruction,
  :coord [3],
  :idx 10,
  :form-id -1644763934}
 {:instruction/op :ASTORE,
  :var 2,
  :emitted/type :instruction,
  :coord [3],
  :idx 11,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L159851392",
  :coord [3],
  :idx 12,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L373675061",
  :coord [3],
  :idx 13,
  :form-id -1644763934}
 {:instruction/op :ALOAD,
  :var 0,
  :emitted/type :instruction,
  :coord [],
  :idx 14,
  :form-id -1644763934}
 {:instruction/op :ACONST_NULL,
  :emitted/type :instruction,
  :coord [],
  :idx 15,
  :form-id -1644763934}
 {:instruction/op :ASTORE,
  :var 0,
  :emitted/type :instruction,
  :coord [],
  :idx 16,
  :form-id -1644763934}
 {:instruction/op :ALOAD,
  :var 1,
  :emitted/type :instruction,
  :coord [],
  :idx 17,
  :form-id -1644763934}
 {:instruction/op :ACONST_NULL,
  :emitted/type :instruction,
  :coord [],
  :idx 18,
  :form-id -1644763934}
 {:instruction/op :ASTORE,
  :var 1,
  :emitted/type :instruction,
  :coord [],
  :idx 19,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1753023263",
  :coord [],
  :idx 20,
  :form-id -1644763934}
 {:instruction/op :INVOKESTATIC,
  :owner "clojure/lang/Numbers",
  :name "add",
  :descriptor
  "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 21,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1490466483",
  :coord [3 2 3],
  :idx 22,
  :form-id -1644763934}
 {:instruction/op :GETSTATIC,
  :field-owner "user$sum",
  :field-name "__thunk__0__",
  :field-descriptor "Lclojure/lang/ILookupThunk;",
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 23,
  :form-id -1644763934}
 {:instruction/op :DUP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 24,
  :form-id -1644763934}
 {:instruction/op :ALOAD,
  :var 2,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 25,
  :form-id -1644763934}
 {:instruction/op :ACONST_NULL,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 26,
  :form-id -1644763934}
 {:instruction/op :ASTORE,
  :var 2,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 27,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1926283119",
  :coord [3 2 3],
  :idx 28,
  :form-id -1644763934}
 {:instruction/op :DUP_X2,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 29,
  :form-id -1644763934}
 {:instruction/op :INVOKEINTERFACE,
  :owner "clojure/lang/ILookupThunk",
  :name "get",
  :descriptor "(Ljava/lang/Object;)Ljava/lang/Object;",
  :interface? true,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 30,
  :form-id -1644763934}
 {:instruction/op :DUP_X2,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 31,
  :form-id -1644763934}
 {:instruction/op :IF_ACMPEQ,
  :label "L1242311036",
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 32,
  :form-id -1644763934}
 {:instruction/op :POP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 33,
  :form-id -1644763934}
 {:instruction/op :GOTO,
  :label "L1256832729",
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 34,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1242311036",
  :coord [3 2 3],
  :idx 35,
  :form-id -1644763934}
 {:instruction/op :SWAP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 36,
  :form-id -1644763934}
 {:instruction/op :POP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 37,
  :form-id -1644763934}
 {:instruction/op :DUP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 38,
  :form-id -1644763934}
 {:instruction/op :GETSTATIC,
  :field-owner "user$sum",
  :field-name "__site__0__",
  :field-descriptor "Lclojure/lang/KeywordLookupSite;",
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 39,
  :form-id -1644763934}
 {:instruction/op :SWAP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 40,
  :form-id -1644763934}
 {:instruction/op :INVOKEINTERFACE,
  :owner "clojure/lang/ILookupSite",
  :name "fault",
  :descriptor "(Ljava/lang/Object;)Lclojure/lang/ILookupThunk;",
  :interface? true,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 41,
  :form-id -1644763934}
 {:instruction/op :DUP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 42,
  :form-id -1644763934}
 {:instruction/op :PUTSTATIC,
  :field-owner "user$sum",
  :field-name "__thunk__0__",
  :field-descriptor "Lclojure/lang/ILookupThunk;",
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 43,
  :form-id -1644763934}
 {:instruction/op :SWAP,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 44,
  :form-id -1644763934}
 {:instruction/op :INVOKEINTERFACE,
  :owner "clojure/lang/ILookupThunk",
  :name "get",
  :descriptor "(Ljava/lang/Object;)Ljava/lang/Object;",
  :interface? true,
  :emitted/type :instruction,
  :coord [3 2 3],
  :idx 45,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1256832729",
  :coord [3 2 3],
  :idx 46,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L661121561",
  :coord [3 2],
  :idx 47,
  :form-id -1644763934}
 {:instruction/op :INVOKESTATIC,
  :owner "clojure/lang/Numbers",
  :name "add",
  :descriptor
  "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;",
  :interface? false,
  :emitted/type :instruction,
  :coord [3 2],
  :idx 48,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1354433117",
  :coord [3],
  :idx 49,
  :form-id -1644763934}
 {:name "m",
  :descriptor "Ljava/lang/Object;",
  :signature nil,
  :start-label "L159851392",
  :end-label "L1354433117",
  :index 2,
  :emitted/type :var,
  :coord [3],
  :idx 50,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L2077726463",
  :coord [],
  :idx 51,
  :form-id -1644763934}
 {:name "a",
  :descriptor "Ljava/lang/Object;",
  :signature nil,
  :start-label "L379394929",
  :end-label "L2077726463",
  :index 0,
  :emitted/type :var,
  :coord [],
  :idx 52,
  :form-id -1644763934}
 {:name "b",
  :descriptor "Ljava/lang/Object;",
  :signature nil,
  :start-label "L379394929",
  :end-label "L2077726463",
  :index 1,
  :emitted/type :var,
  :coord [],
  :idx 53,
  :form-id -1644763934}
 {:instruction/op :ARETURN,
  :emitted/type :instruction,
  :coord [],
  :idx 54,
  :form-id -1644763934}



 {:emitted/type :method,
  :method/name "invoke",
  :method/descriptor
  "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
  :method/signature nil,
  :method/exceptions [],
  :coord [],
  :idx 55,
  :form-id -1644763934}
 {:instruction/op :ALOAD,
  :var 1,
  :emitted/type :instruction,
  :coord [],
  :idx 56,
  :form-id -1644763934}
 {:instruction/op :ACONST_NULL,
  :emitted/type :instruction,
  :coord [],
  :idx 57,
  :form-id -1644763934}
 {:instruction/op :ASTORE,
  :var 1,
  :emitted/type :instruction,
  :coord [],
  :idx 58,
  :form-id -1644763934}
 {:instruction/op :ALOAD,
  :var 2,
  :emitted/type :instruction,
  :coord [],
  :idx 59,
  :form-id -1644763934}
 {:instruction/op :ACONST_NULL,
  :emitted/type :instruction,
  :coord [],
  :idx 60,
  :form-id -1644763934}
 {:instruction/op :ASTORE,
  :var 2,
  :emitted/type :instruction,
  :coord [],
  :idx 61,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L2085609514",
  :coord [],
  :idx 62,
  :form-id -1644763934}
 {:instruction/op :INVOKESTATIC,
  :owner "user$sum",
  :name "invokeStatic",
  :descriptor
  "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 63,
  :form-id -1644763934}
 {:instruction/op :ARETURN,
  :emitted/type :instruction,
  :coord [],
  :idx 64,
  :form-id -1644763934}
 {:field/access 25,
  :field/name "const__2",
  :field/signature nil,
  :field/descriptor "Lclojure/lang/AFn;",
  :field/value nil,
  :emitted/type :field,
  :coord [],
  :idx 65,
  :form-id -1644763934}
 {:field/access 24,
  :field/name "__site__0__",
  :field/signature nil,
  :field/descriptor "Lclojure/lang/KeywordLookupSite;",
  :field/value nil,
  :emitted/type :field,
  :coord [],
  :idx 66,
  :form-id -1644763934}
 {:field/access 8,
  :field/name "__thunk__0__",
  :field/signature nil,
  :field/descriptor "Lclojure/lang/ILookupThunk;",
  :field/value nil,
  :emitted/type :field,
  :coord [],
  :idx 67,
  :form-id -1644763934}
 {:emitted/type :method,
  :method/name "<clinit>",
  :method/descriptor "()V",
  :method/signature nil,
  :method/exceptions nil,
  :coord [],
  :idx 68,
  :form-id -1644763934}
 {:emitted/type :label,
  :label/name "L1684077208",
  :coord [],
  :idx 69,
  :form-id -1644763934}
 {:instruction/op :ICONST_2,
  :emitted/type :instruction,
  :coord [],
  :idx 70,
  :form-id -1644763934}
 {:instruction/op :DUP,
  :emitted/type :instruction,
  :coord [],
  :idx 71,
  :form-id -1644763934}
 {:instruction/op :ICONST_0,
  :emitted/type :instruction,
  :coord [],
  :idx 72,
  :form-id -1644763934}
 {:instruction/op :ACONST_NULL,
  :emitted/type :instruction,
  :coord [],
  :idx 73,
  :form-id -1644763934}
 {:instruction/op :ldc,
  :value "x",
  :emitted/type :instruction,
  :coord [],
  :idx 74,
  :form-id -1644763934}
 {:instruction/op :INVOKESTATIC,
  :owner "clojure/lang/RT",
  :name "keyword",
  :descriptor
  "(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Keyword;",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 75,
  :form-id -1644763934}
 {:instruction/op :AASTORE,
  :emitted/type :instruction,
  :coord [],
  :idx 76,
  :form-id -1644763934}
 {:instruction/op :DUP,
  :emitted/type :instruction,
  :coord [],
  :idx 77,
  :form-id -1644763934}
 {:instruction/op :ICONST_1,
  :emitted/type :instruction,
  :coord [],
  :idx 78,
  :form-id -1644763934}
 {:instruction/op :ldc,
  :value 100,
  :emitted/type :instruction,
  :coord [],
  :idx 79,
  :form-id -1644763934}
 {:instruction/op :INVOKESTATIC,
  :owner "java/lang/Long",
  :name "valueOf",
  :descriptor "(J)Ljava/lang/Long;",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 80,
  :form-id -1644763934}
 {:instruction/op :AASTORE,
  :emitted/type :instruction,
  :coord [],
  :idx 81,
  :form-id -1644763934}
 {:instruction/op :INVOKESTATIC,
  :owner "clojure/lang/RT",
  :name "map",
  :descriptor "([Ljava/lang/Object;)Lclojure/lang/IPersistentMap;",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 82,
  :form-id -1644763934}
 {:instruction/op :PUTSTATIC,
  :field-owner "user$sum",
  :field-name "const__2",
  :field-descriptor "Lclojure/lang/AFn;",
  :emitted/type :instruction,
  :coord [],
  :idx 83,
  :form-id -1644763934}
 {:instruction/op :DUP,
  :emitted/type :instruction,
  :coord [],
  :idx 84,
  :form-id -1644763934}
 {:instruction/op :ACONST_NULL,
  :emitted/type :instruction,
  :coord [],
  :idx 85,
  :form-id -1644763934}
 {:instruction/op :ldc,
  :value "x",
  :emitted/type :instruction,
  :coord [],
  :idx 86,
  :form-id -1644763934}
 {:instruction/op :INVOKESTATIC,
  :owner "clojure/lang/RT",
  :name "keyword",
  :descriptor
  "(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Keyword;",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 87,
  :form-id -1644763934}
 {:instruction/op :INVOKESPECIAL,
  :owner "clojure/lang/KeywordLookupSite",
  :name "<init>",
  :descriptor "(Lclojure/lang/Keyword;)V",
  :interface? false,
  :emitted/type :instruction,
  :coord [],
  :idx 88,
  :form-id -1644763934}
 {:instruction/op :DUP,
  :emitted/type :instruction,
  :coord [],
  :idx 89,
  :form-id -1644763934}
 {:instruction/op :PUTSTATIC,
  :field-owner "user$sum",
  :field-name "__site__0__",
  :field-descriptor "Lclojure/lang/KeywordLookupSite;",
  :emitted/type :instruction,
  :coord [],
  :idx 90,
  :form-id -1644763934}
 {:instruction/op :PUTSTATIC,
  :field-owner "user$sum",
  :field-name "__thunk__0__",
  :field-descriptor "Lclojure/lang/ILookupThunk;",
  :emitted/type :instruction,
  :coord [],
  :idx 91,
  :form-id -1644763934}
 {:instruction/op :RETURN,
  :emitted/type :instruction,
  :coord [],
  :idx 92,
  :form-id -1644763934}])
