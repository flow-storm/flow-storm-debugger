(ns flow-storm.debugger.ui.value-inspector
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler button label h-box v-box border-pane table-view]]
            [clojure.string :as str]
            [flow-storm.utils :as utils :refer [log-error]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars :as ui-vars])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.layout HBox VBox Priority]
           [javafx.geometry Orientation]
           [javafx.scene.control TextInputDialog SplitPane]))

(declare create-shallow-frame-pane)

(defn def-val [val]
  (let [tdiag (doto (TextInputDialog.)
                (.setHeaderText "Def var with name. You can use / to provide a namespace, otherwise will be defined under [cljs.]user ")
                (.setContentText "Var name :"))
        _ (.showAndWait tdiag)
        val-name (-> tdiag .getEditor .getText)]
    (when-not (str/blank? val-name)
      (runtime-api/def-value rt-api (symbol val-name) val))))

(defn- update-vals-panes [{:keys [main-split vals-stack]}]
  (let [[head prev & _] @vals-stack
        value-full-pane (let [def-btn (button :label "def"
                                              :class "def-btn"
                                              :on-click (:def-fn head))
                              tap-btn (button :label "tap"
                                              :class "def-btn"
                                              :on-click (:tap-fn head))
                              buttons-box (doto (h-box [def-btn tap-btn])
                                            (.setSpacing 5))
                              type-lbl (label (format "Type: %s" (-> head :shallow-val :val/type)))
                              val-header-box (border-pane {:left (v-box (if-let [cnt (-> head :shallow-val :total-count)]
                                                                          [type-lbl (label (format "Count: %d" cnt))]
                                                                          [type-lbl]))
                                                           :right buttons-box}
                                                          "value-inspector-header")
                              meta-box (when-let [mp (:meta-pane head)]
                                         (v-box [(label "Meta")
                                                 mp]))
                              val-pane (border-pane {:top val-header-box
                                                     :center (:val-pane head)})
                              val-full-pane (v-box (if meta-box
                                                     [meta-box val-pane]
                                                     [val-pane]))]
                          val-full-pane)]
    (HBox/setHgrow value-full-pane Priority/ALWAYS)

    (.clear (.getItems main-split))
    (.addAll (.getItems main-split) (if prev
                                    (let [prev-pane (doto (h-box [(:val-pane prev)])
                                                      (.setId "prev-pane"))]
                                      [prev-pane value-full-pane])
                                    [value-full-pane]))))

(defn- drop-stack-to-frame [{:keys [vals-stack]} val-frame]
  (swap! vals-stack
         (fn [stack]
           (loop [[fr :as stack] stack]
             (if (= fr val-frame)
               stack
               (recur (pop stack)))))))

(defn- update-stack-bar-pane [{:keys [stack-bar-pane vals-stack] :as ctx}]
  (.clear (.getChildren stack-bar-pane))
  (.addAll (.getChildren stack-bar-pane)
           (mapv (fn [{:keys [stack-txt] :as val-frame}]
                   (doto (button :label stack-txt :class "stack-bar-btn")
                     (.setOnAction
                      (event-handler
                       [_]
                       (drop-stack-to-frame ctx val-frame)
                       (update-vals-panes ctx)
                       (update-stack-bar-pane ctx)))))
                 (reverse @vals-stack))))

(defn- make-stack-frame [ctx stack-txt v]
  (let [shallow-val (runtime-api/shallow-val rt-api v)]
    {:stack-txt stack-txt
     :val-pane (create-shallow-frame-pane ctx shallow-val)
     :meta-pane (when-let [sm (:val/shallow-meta shallow-val)] (create-shallow-frame-pane ctx sm))
     :def-fn (fn [] (def-val (:val/full shallow-val)))
     :tap-fn (fn [] (runtime-api/tap-value rt-api (:val/full shallow-val)))
     :shallow-val shallow-val}))

(defn dig-node? [x]
  (and (vector? x)
       (= :val/dig-node (first x))))

(defn dig-node-val [v]
  (when (dig-node? v)
    (second v)))

(defn- create-dig-node [ctx {:keys [stack-txt val-ref val-txt]}]
  (let [node-in-prev-pane? (fn [node]
                             (loop [n node]
                               (when n
                                 (if (= "prev-pane" (.getId n))
                                   true
                                   (recur (.getParent n))))))

        click-handler (event-handler
                       [mev]
                       (let [new-frame (make-stack-frame ctx stack-txt val-ref)]

                         ;; this is kind of HACKY, so if a dig link is clicked on the
                         ;; prev-pane, discard the top of the stack
                         (when (node-in-prev-pane? (.getSource mev))
                           (swap! (:vals-stack ctx) pop))

                         (swap! (:vals-stack ctx) conj new-frame)
                         (update-vals-panes ctx)
                         (update-stack-bar-pane ctx)))
        lbl (doto (label val-txt "link-lbl")
              (.setOnMouseClicked click-handler))]
    {:node-obj lbl
     :click-handler click-handler}))

(defn make-node [ctx {:keys [dig-val? val-txt] :as item}]
  (if dig-val?
    (create-dig-node ctx item)
    {:node-obj (label val-txt)}))

(defn make-item [stack-key maybe-dig-v]
  (let [dig-val? (dig-node? maybe-dig-v)
        item {:dig-val? dig-val?
              :stack-txt (if (dig-node? stack-key)
                           (runtime-api/val-pprint rt-api (dig-node-val stack-key) {:print-level 4 :pprint? false :print-length 20})
                           (pr-str stack-key))}]
    (if dig-val?
      (let [val-ref (dig-node-val maybe-dig-v)]
        (assoc item
               :val-ref val-ref
               :val-txt (runtime-api/val-pprint rt-api val-ref {:print-level 4 :pprint? false :print-length 20})))

      (assoc item
             :val-ref maybe-dig-v
             :val-txt (pr-str maybe-dig-v)))))

(defn- create-shallow-map-pane [ctx shallow-v]
  (let [{:keys [table-view-pane]}
        (table-view {:columns ["Key" "Value"]
                     :cell-factory-fn (fn [item]
                                        (:node-obj (make-node ctx item)))
                     :search-predicate (fn [[k-item v-item] search-str]
                                         (boolean
                                          (or (str/includes? (:val-txt k-item) search-str)
                                              (str/includes? (:val-txt v-item) search-str))))
                     :items (->> (:val/map-entries shallow-v)
                                 (map (fn [[k v]]
                                        [(make-item "<key>" k) (make-item k v)])))})]
    (VBox/setVgrow table-view-pane Priority/ALWAYS)
    (HBox/setHgrow table-view-pane Priority/ALWAYS)
    table-view-pane))

(defn- create-shallow-seq-pane [ctx shallow-v]
  (let [{:keys [list-view-pane add-all]} (ui-utils/list-view
                                              {:editable? false
                                               :cell-factory-fn (fn [list-cell item]
                                                                  (let [{:keys [node-obj click-handler]} (make-node ctx item)]
                                                                    (.setText list-cell nil)
                                                                    (.setGraphic list-cell node-obj)
                                                                    ;; the node-obj will already handle the click
                                                                    ;; but we also add the handler to the list-cell
                                                                    ;; so one single click executes the action instead of
                                                                    ;; selecting the cell
                                                                    (.setOnMouseClicked list-cell click-handler)))
                                               :search-predicate (fn [item search-str]
                                                                   (str/includes? (:val-txt item) search-str))})
        add-shallow-page-to-list (fn [{:keys [val/page page/offset]}]
                                   (add-all (map-indexed
                                             (fn [i v]
                                               (make-item (+ offset i) v))
                                             page)))
        more-button (when (:val/more shallow-v) (button :label "More.."))
        change-more-handler-for-shallow (fn change-more-handler-for-shallow [{:keys [val/more]}]
                                          (if more
                                            (doto more-button
                                              (.setOnAction (event-handler
                                                             [_]
                                                             (let [new-shallow-v (runtime-api/shallow-val rt-api more)]
                                                               (add-shallow-page-to-list new-shallow-v)
                                                               (change-more-handler-for-shallow new-shallow-v)))))
                                            (doto more-button
                                              (.setDisable true)
                                              (.setOnAction (event-handler [_])))))

        container (v-box (cond-> [list-view-pane]
                           more-button (conj (change-more-handler-for-shallow shallow-v))))]
    (VBox/setVgrow list-view-pane Priority/ALWAYS)
    (HBox/setHgrow container Priority/ALWAYS)
    (add-shallow-page-to-list shallow-v)

    container))

(defn- create-shallow-frame-pane [ctx shallow-v]
  (case (:val/kind shallow-v)
    :simple (h-box [(label (:val/str shallow-v))])
    :map (create-shallow-map-pane ctx shallow-v)
    :seq (create-shallow-seq-pane ctx shallow-v)))

(defn- create-inspector-pane [v]
  (let [*vals-stack (atom nil)
        stack-bar-pane (doto (h-box [] "value-inspector-stack-pane")
                         (.setSpacing 5))
        main-split (doto (SplitPane.)
                   (.setOrientation (Orientation/HORIZONTAL))
                   (.setDividerPosition 0 0.5))
        ctx {:stack-bar-pane stack-bar-pane
             :main-split main-split
             :vals-stack *vals-stack}

        mp (border-pane {:top stack-bar-pane
                         :center main-split}
                        "value-inspector-main-pane")]


    (swap! *vals-stack conj (make-stack-frame ctx "/" v))
    (update-vals-panes ctx)
    (update-stack-bar-pane ctx)

    mp))

(defn create-inspector [v]
  (try
    (let [scene (Scene. (create-inspector-pane v) 500 500)
          stage (doto (Stage.)
                  (.setTitle "FlowStorm value inspector")
                  (.setScene scene))]

      (ui-vars/register-and-init-stage! stage)

      (-> stage .show))

    (catch Exception e
      (log-error "UI Thread exception" e))))
