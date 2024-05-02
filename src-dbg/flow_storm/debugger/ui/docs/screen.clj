(ns flow-storm.debugger.ui.docs.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.docs :as dbg-docs]
            [clojure.string :as str]))

(defn create-fn-doc-pane []
  (let [fn-name-lbl (ui/label :text "" :class "docs-fn-name")
        args-box (ui/v-box :childs []
                           :spacing 10)
        rets-box (ui/v-box :childs [] :class "docs-box")
        examples-box (ui/v-box :childs []
                               :spacing 10)
        file-lbl (ui/label :text "")
        line-lbl (ui/label :text "")
        doc-lbl  (ui/label :text "")
        doc-box (ui/v-box
                 :childs [fn-name-lbl
                          doc-lbl
                          (ui/h-box :childs [(ui/label :text "File :" :class "docs-label") file-lbl])
                          (ui/h-box :childs [(ui/label :text "Line :" :class "docs-label") line-lbl])
                          (ui/label :text "Args :" :class "docs-label")
                          args-box
                          (ui/label :text "Returns :" :class "docs-label")
                          rets-box
                          (ui/label :text "Examples :" :class "docs-label")
                          examples-box]
                 :spacing 10)

        doc-pane (doto (ui/scroll-pane :class "hidden-pane")
                   (.setFitToHeight true)
                   (.setFitToWidth true))]

    (.setContent doc-pane doc-box)

    (store-obj "docs-fn-name-lbl" fn-name-lbl)
    (store-obj "docs-args-box" args-box)
    (store-obj "docs-rets-box" rets-box)
    (store-obj "docs-examples-box" examples-box)
    (store-obj "docs-file-lbl" file-lbl)
    (store-obj "docs-line-lbl" line-lbl)
    (store-obj "docs-doc-lbl" doc-lbl)
    (store-obj "docs-doc-pane" doc-pane)
    doc-pane))

(defn- index-arity [arg-vec]
  (:arg-vec (reduce (fn [{:keys [i] :as r} symb]
                      (-> r
                          (update :arg-vec conj (cond-> {:symb symb}
                                                  (not= symb '&) (assoc :i i)))
                          (update :i inc)))
                    {:i 0
                     :arg-vec []}
                    arg-vec)))

(defn- build-type-set-by-idx [args-types]
  (fn [idx]
    (let [atypes (reduce (fn [r av-types]
                           (if (< idx (count av-types))
                             (conj r (or (get av-types idx) "UNKNOWN"))
                             r))
                         #{}
                         args-types)]
      (if (> (count atypes) 1)
        (remove #(= % "UNKNOWN") atypes)
        atypes))))

(defn- build-map-type-component [{:keys [map/kind map/domain type/name]}]
  (let [detail-box (case kind
                     :entity (ui/v-box
                              :childs (mapv (fn [[k v]]
                                              (ui/label :text (format "%s -> %s" k v)))
                                            domain))
                     :regular (ui/label :text (format "%s -> %s"
                                                      (-> domain keys first)
                                                      (-> domain vals first)))
                     (ui/label :text ""))]
    (ui/v-box :childs [(ui/label :text name :class "docs-type-name") detail-box]
              :spacing 10)))

(defn- coll-literals [type-name]
  (case type-name
    "clojure.lang.PersistentVector"  ["["  "]"]
    "clojure.lang.PersistentHashSet" ["#{" "}"]
    ["(" ")"]))

(defn- build-seqable-type-component [{:keys [type/name seq/first-elem-type]}]
  (let [[open-char close-char] (coll-literals name)
        f-elem-type-str (cond
                          (nil? first-elem-type)
                          ""

                          (string? first-elem-type)
                          first-elem-type

                          :else
                          (case (:type/type first-elem-type)
                            :fn "Fn"
                            :map (format "{ %s }" (case (:type/kind first-elem-type)
                                                    :entity (format "< %s >"(->> first-elem-type :domain keys (str/join " ")))
                                                    :regular (format "%s -> %s"
                                                                     (-> first-elem-type :domain keys first)
                                                                     (-> first-elem-type :domain vals first))
                                                    ""))
                            :seqable (:type/name first-elem-type)
                            (:type/name first-elem-type)))
        details-lbl (ui/label :text (format "%s %s ,...%s" open-char f-elem-type-str close-char))]
    (ui/h-box :childs [(ui/label :text name :class "docs-type-name") details-lbl]
              :spacing 10)))

(defn build-type-set-component [types-set]
  (let [build-type-component (fn [t]
                               (case (:type/type t)
                                 :fn      (ui/label :text "Fn")
                                 :map     (build-map-type-component t)
                                 :seqable (build-seqable-type-component t)

                                 ;; else
                                 (ui/label :text (:type/name t))))]
    (ui/v-box
     :childs (->> types-set
                  (map build-type-component))
     :spacing 10)))

(defn build-arities-boxes-form-arglists [args-types arglists]
  (let [arglists (when (seq arglists) (read-string arglists))
        type-set-by-idx (build-type-set-by-idx args-types)
        arglists+ (reduce (fn [r avec]
                            (let [arity-types (->> (index-arity avec)
                                                   (mapv (fn [{:keys [symb i]}]
                                                           [symb (when i (type-set-by-idx i))])))]
                              (conj r arity-types)))
                          []
                          arglists)]
    (mapv (fn [argv]
            (let [argv-boxes (mapv (fn [[symb tset]]
                                     (let [symb-lbl (doto (ui/label :text (str symb) :class "docs-arg-symbol")
                                                      (.setMinWidth 70))
                                           types-set-box (build-type-set-component tset)]
                                       (ui/h-box :childs [symb-lbl
                                                          types-set-box]
                                                 :spacing 10)))
                                   argv)]
              (ui/v-box
               :childs (-> [(ui/label :text "[")]
                           (into argv-boxes)
                           (into [(ui/label :text "]")]))
               :class "docs-box"
               :spacing 10)))
          arglists+)))

(defn build-arities-boxes-from-types [args-types]
  (let [type-set-by-idx (build-type-set-by-idx args-types)
        arities-sizes (keys (group-by count args-types))]
    (map (fn [asize]
           (ui/v-box
            :childs (-> [(ui/label :text "[")]
                        (into (mapv (fn [idx]
                                      (build-type-set-component (type-set-by-idx idx)))
                                    (range asize)))
                        (into [(ui/label :text "]")]))
            :class "docs-box"))
         arities-sizes)))

(defn update-fn-doc-pane [fn-symb {:keys [args-types return-types call-examples var-meta]}]
  (let [{:keys [file line arglists doc]} var-meta
        [fn-name-lbl] (obj-lookup "docs-fn-name-lbl")
        [args-box] (obj-lookup "docs-args-box")
        [rets-box] (obj-lookup "docs-rets-box")
        [examples-box] (obj-lookup "docs-examples-box")
        [file-lbl] (obj-lookup "docs-file-lbl")
        [line-lbl] (obj-lookup "docs-line-lbl")
        [doc-lbl] (obj-lookup "docs-doc-lbl")

        arities-boxes (if (seq arglists)
                        (build-arities-boxes-form-arglists args-types arglists)
                        (build-arities-boxes-from-types args-types))]


    (ui-utils/set-text fn-name-lbl (str fn-symb))
    (ui-utils/set-text doc-lbl doc)
    (ui-utils/set-text file-lbl (str file))
    (ui-utils/set-text line-lbl (str line))

    (ui-utils/observable-clear (.getChildren args-box))
    (ui-utils/observable-add-all (.getChildren args-box)
             arities-boxes)

    (ui-utils/observable-clear (.getChildren rets-box))
    (ui-utils/observable-add-all (.getChildren rets-box)
             [(build-type-set-component return-types)])

    (ui-utils/observable-clear (.getChildren examples-box))

    (ui-utils/observable-add-all (.getChildren examples-box)
             (mapv (fn [{:keys [args ret]}]
                     (ui/v-box
                      :childs [(ui-utils/set-min-size-wrap-content
                                (ui/label :text (format "(%s %s)" (name fn-symb) (str/join " " args))))

                               (ui/label :text "=>" :class "docs-example-ret-symbol")

                               (ui-utils/set-min-size-wrap-content
                                (ui/label :text (str ret)))]
                      :class "docs-box"))
                   call-examples))))

(defn show-doc [fn-symb]
  (let [fn-data (get dbg-docs/fn-docs fn-symb)
        [fn-doc-pane] (obj-lookup "docs-doc-pane")]
    (ui-utils/rm-class fn-doc-pane "hidden-pane")
    (update-fn-doc-pane fn-symb fn-data)))

(defn main-pane []
  (let [fn-doc-pane (create-fn-doc-pane)

        {:keys [list-view-pane add-all]}
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell fn-symb]
                                      (-> list-cell
                                          (ui-utils/set-text nil)
                                          (ui-utils/set-graphic (ui/label :text (str fn-symb)))))
                      :on-click (fn [mev sel-items _]
                                  (cond (ui-utils/mouse-primary? mev)
                                        (show-doc (first sel-items))))
                      :selection-mode :single
                      :search-predicate (fn [fn-symb search-str]
                                          (str/includes? (str fn-symb) search-str)))
        mp (ui/split :orientation :horizontal
                     :childs [list-view-pane fn-doc-pane]
                     :sizes [0.3])]

    (add-all (keys dbg-docs/fn-docs))

    mp))
