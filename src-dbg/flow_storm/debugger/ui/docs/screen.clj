(ns flow-storm.debugger.ui.docs.screen
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [label list-view h-box v-box]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.docs :as docs]
            [clojure.string :as str])
  (:import [javafx.scene.input MouseButton]
           [javafx.geometry Orientation]
           [javafx.scene.control SplitPane]))

(defn create-fn-doc-pane []
  (let [fn-name-lbl (label "" "docs-fn-name")
        args-box (doto (v-box []) (.setSpacing 10))
        rets-box (v-box [] "docs-box")
        examples-box (doto (v-box []) (.setSpacing 10))
        file-lbl (label "")
        line-lbl (label "")
        doc-lbl (label "")
        doc-pane (doto
                     (v-box [fn-name-lbl
                             doc-lbl
                             (h-box [(label "File :" "docs-label") file-lbl])
                             (h-box [(label "Line :" "docs-label") line-lbl])
                             (label "Args :" "docs-label")
                             args-box
                             (label "Returns :" "docs-label")
                             rets-box
                             (label "Examples :" "docs-label")
                             examples-box]
                            "hidden-pane")
                   (.setSpacing 10))]
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
                                     (let [symb-lbl (doto (label (str symb))
                                                      (.setMinWidth 70))
                                           types-lbl (label (str/join " | " tset) "docs-types-set")]
                                       (doto
                                           (h-box [symb-lbl
                                                   types-lbl])
                                         (.setSpacing 10))))
                                   argv)]
              (v-box (-> [(label "[")]
                         (into argv-boxes)
                         (into [(label "]")]))
                     "docs-box")))
          arglists+)))

(defn build-arities-boxes-from-types [args-types]
  (let [type-set-by-idx (build-type-set-by-idx args-types)
        arities-sizes (keys (group-by count args-types))]
    (map (fn [asize]
           (v-box (-> [(label "[")]
                      (into (mapv (fn [idx]
                                    (label (str/join " | " (type-set-by-idx idx))
                                           "docs-types-set"))
                                  (range asize)))
                      (into [(label "]")]))
                  "docs-box"))
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


    (.setText fn-name-lbl (str fn-symb))
    (.setText doc-lbl doc)
    (.setText file-lbl (str file))
    (.setText line-lbl (str line))

    (.clear (.getChildren args-box))
    (.addAll (.getChildren args-box)
             arities-boxes)

    (.clear (.getChildren rets-box))
    (.addAll (.getChildren rets-box)
             (mapv (fn [rt]
                     (label (str rt) "docs-types-set"))
                   return-types))

    (.clear (.getChildren examples-box))
    (.addAll (.getChildren examples-box)
             (mapv (fn [{:keys [args ret]}]
                     (v-box [(label (format "(%s %s)" (name fn-symb) (str/join " " args)))
                             (label "=>")
                             (label (str ret))]
                            "docs-box"))
                   call-examples))))

(defn show-doc [fn-symb]
  (let [fn-data (get docs/fn-docs fn-symb)
        [fn-doc-pane] (obj-lookup "docs-doc-pane")]
    (ui-utils/rm-class fn-doc-pane "hidden-pane")
    (update-fn-doc-pane fn-symb fn-data)))

(defn main-pane []
  (let [fn-doc-pane (create-fn-doc-pane)

        {:keys [list-view-pane add-all]}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell fn-symb]
                                       (.setText list-cell nil)
                                       (.setGraphic list-cell (label (str fn-symb))))
                    :on-click (fn [mev sel-items _]
                                (cond (= MouseButton/PRIMARY (.getButton mev))
                                      (show-doc (first sel-items))))
                    :selection-mode :single
                    :search-predicate (fn [fn-symb search-str]
                                        (str/includes? (str fn-symb) search-str))})
        mp (doto (SplitPane.)
             (.setOrientation (Orientation/HORIZONTAL)))]

    (add-all (keys docs/fn-docs))

    (-> mp
        .getItems
        (.addAll [list-view-pane fn-doc-pane]))

    (.setDividerPosition mp 0 0.3)

    mp))

(comment

  )
