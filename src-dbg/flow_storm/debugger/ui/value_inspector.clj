(ns flow-storm.debugger.ui.value-inspector
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler button label h-box v-box border-pane table-view]]
            [clojure.string :as str]
            [flow-storm.debugger.values :as dbg-values]
            [flow-storm.utils :as utils :refer [log-error]]
            [flow-storm.value-types :refer [->LocalImmValue local-imm-value? remote-imm-value?]]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.target-commands :as target-commands])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]

           [javafx.scene.layout HBox Priority]
           [javafx.scene.control TextInputDialog]
           [javafx.collections FXCollections]))

(declare create-shallow-frame-pane)

(defn def-val [val]
  (let [tdiag (doto (TextInputDialog.)
                (.setHeaderText "Def var with name (in Clojure under user/ namespace and in ClojureScript under js/) ")
                (.setContentText "Var name :"))
        _ (.showAndWait tdiag)
        val-name (let [txt (-> tdiag .getEditor .getText)]
                   (if (str/blank? txt)
                     "val0"
                     txt))]
    (cond
      (local-imm-value? val)
      (target-commands/run-command :def-value {:val (:val val)
                                               :val-name val-name})

      (remote-imm-value? val)
      (target-commands/run-command :def-remote-value {:vid (:vid val)
                                                      :val-name val-name}))))

(defn- update-center-pane [{:keys [center-pane]} frame-pane]
  (.clear (.getChildren center-pane))
  (.addAll (.getChildren center-pane) [frame-pane]))

(defn- update-stack-bar-pane [{:keys [stack-bar-pane vals-panes-stack] :as ctx}]
  (.clear (.getChildren stack-bar-pane))
  (.addAll (.getChildren stack-bar-pane)
           (mapv (fn [[short-txt frame-pane]]
                   (doto (button short-txt "stack-bar-btn")
                     (.setOnAction
                      (event-handler
                       [_]
                       (update-center-pane ctx frame-pane)
                       (swap! vals-panes-stack (fn [ss]
                                                 (reduce (fn [r [_ fr :as stack-entry]]
                                                           (if (not= fr frame-pane)
                                                             (conj r stack-entry)
                                                             (reduced (conj r stack-entry))))
                                                  []
                                                  ss)))
                       (update-stack-bar-pane ctx)))))
                 @vals-panes-stack)))

(defn- create-dig-node [ctx v]
  (if (or (local-imm-value? v) (remote-imm-value? v))
    (let [short-txt-len 20
          short-txt (-> v
                        (dbg-values/val-pprint {:print-level 2 :pprint? false :print-length 10})
                        (utils/elide-string short-txt-len))]
      (doto (label short-txt "link-lbl")
        (.setOnMouseClicked
         (event-handler
          [_]
          (let [new-frame (create-shallow-frame-pane ctx (dbg-values/shallow-val v))]
            (update-center-pane ctx new-frame)
            (swap! (:vals-panes-stack ctx) conj [short-txt new-frame])
            (update-stack-bar-pane ctx))))))

    (label (pr-str v))))

(defn- create-shallow-map-pane [ctx shallow-v]
  (table-view {:columns ["Key" "Value"]
               :cell-factory-fn (fn [item]
                                  (create-dig-node ctx item))
               :items (:val/map-entries shallow-v)}))

(defn- create-shallow-seq-pane [ctx shallow-v]
  (let [{:keys [list-view-pane add-all]} (ui-utils/list-view
                                          {:editable? false
                                           :cell-factory-fn (fn [list-cell elem]
                                                              (.setText list-cell nil)
                                                              (.setGraphic list-cell (create-dig-node ctx elem)))})
        header-lbl (label (format "Count : %s" (if-let [cnt (:total-count shallow-v)] cnt "unknown")))
        more-button (when-let [more-val (:val/more shallow-v)] (button "More.."))
        change-more-handler-for-shallow (fn change-more-handler-for-shallow [{:keys [val/more]}]
                                          (if more
                                            (doto more-button
                                              (.setOnAction (event-handler
                                                             [_]
                                                             (let [new-val (dbg-values/shallow-val more)]
                                                               (add-all (:val/page new-val))
                                                               (change-more-handler-for-shallow new-val)))))
                                            (doto more-button
                                              (.setDisable true)
                                              (.setOnAction (event-handler [_])))
                                            ))

        container (v-box (cond-> [header-lbl
                                  list-view-pane]
                           more-button (conj (change-more-handler-for-shallow shallow-v))))]
    (HBox/setHgrow container Priority/ALWAYS)
    (add-all (:val/page shallow-v))

    container))

(defn- create-shallow-frame-pane [ctx shallow-v]
  (let [def-btn (doto (button "def" "def-btn")
                  (.setOnAction
                   (event-handler
                    [_]
                    (def-val (:val/full shallow-v)))))
        frame-pane (border-pane {:top (border-pane {:left (label (format "Type: %s" (:val/type shallow-v)))
                                                    :right def-btn}
                                                   "value-inspector-header")
                                 :center (h-box [(case (:val/kind shallow-v)
                                                   :simple (h-box [(label (:val/str shallow-v))])
                                                   :map (create-shallow-map-pane ctx shallow-v)
                                                   :seq (create-shallow-seq-pane ctx shallow-v))])})]
    (HBox/setHgrow frame-pane Priority/ALWAYS)
    frame-pane))

(defn- create-inspector-pane [v]
  (let [*vals-panes-stack (atom [])
        stack-bar-pane (doto (h-box [] "value-inspector-stack-pane")
                         (.setSpacing 5))
        center-pane (h-box [])
        ctx {:stack-bar-pane stack-bar-pane
             :center-pane center-pane
             :vals-panes-stack *vals-panes-stack}
        first-frame (create-shallow-frame-pane ctx
                                              (dbg-values/shallow-val v))
        mp (border-pane {:top stack-bar-pane
                         :center center-pane}
                        "value-inspector-main-pane")]

    (swap! *vals-panes-stack conj ["/" first-frame])
    (update-center-pane ctx first-frame)
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

(comment

  (do
    (def ev (->LocalImmValue {:a 100
                              "other" {1 {1 2
                                          2 4}
                                       2 {:hello :world}}
                              :b "hello"
                              :c [1 2 3 {1 2}]
                              :d #{:a :b :c}}))

    (ui-utils/run-now (create-inspector ev)))

  (do
    (def ev (->LocalImmValue {:a (range)}))

    (ui-utils/run-now (create-inspector ev)))

  )
