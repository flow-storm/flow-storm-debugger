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
           [javafx.scene.control TextInputDialog]))

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
    (runtime-api/def-value rt-api val-name val)))

(defn- update-center-pane [{:keys [center-pane]} frame-pane]
  (.clear (.getChildren center-pane))
  (.addAll (.getChildren center-pane) [frame-pane]))

(defn- update-stack-bar-pane [{:keys [stack-bar-pane vals-panes-stack] :as ctx}]
  (.clear (.getChildren stack-bar-pane))
  (.addAll (.getChildren stack-bar-pane)
           (mapv (fn [[short-txt frame-pane]]
                   (doto (button :label short-txt :class "stack-bar-btn")
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

(defn dig-node? [x]
  (and (vector? x)
       (= :val/dig-node (first x))))

(defn dig-node-val [v]
  (when (dig-node? v)
    (second v)))

(defn- create-dig-node [ctx k v]
  (if-let [v (dig-node-val v)]
    (let [stack-txt-len 20
          val-txt (runtime-api/val-pprint rt-api v {:print-level 4 :pprint? false :print-length 20})
          stack-txt (utils/elide-string (if (dig-node? k)
                                          val-txt
                                          (str k))
                                        stack-txt-len)
          click-handler (event-handler
                         [_]
                         (let [new-frame (create-shallow-frame-pane ctx (runtime-api/shallow-val rt-api v))]
                           (update-center-pane ctx new-frame)
                           (swap! (:vals-panes-stack ctx) conj [stack-txt new-frame])
                           (update-stack-bar-pane ctx)))
          lbl (doto (label val-txt "link-lbl")
                (.setOnMouseClicked click-handler))]
      [lbl click-handler])

    [(label (pr-str v)) nil]))

(defn- create-shallow-map-pane [ctx shallow-v]
  (let [item->key (reduce (fn [r [k v]]
                            (assoc r v k))
                          {}
                          (:val/map-entries shallow-v))]
    (table-view {:columns ["Key" "Value"]
                 :cell-factory-fn (fn [item]
                                    (first (create-dig-node ctx (item->key item) item)))
                 :items (:val/map-entries shallow-v)})))

(defn- create-shallow-seq-pane [ctx shallow-v]
  (let [{:keys [list-view-pane add-all]} (ui-utils/list-view
                                          {:editable? false
                                           :cell-factory-fn (fn [list-cell {:keys [val idx]}]
                                                              (let [[dnode dhandler] (create-dig-node ctx idx val)]
                                                                (.setText list-cell nil)
                                                                (.setGraphic list-cell dnode)
                                                                ;; the dnode will already handle the click
                                                                ;; but we also add the handler to the list-cell
                                                                ;; so one single click executes the action instead of
                                                                ;; selecting the cell
                                                                (.setOnMouseClicked list-cell dhandler)))})
        add-shallow-page-to-list (fn [{:keys [val/page page/offset]}]
                                   (add-all (->> page
                                                 (map-indexed (fn [i v] {:val v :idx (+ offset i)})))))
        header-lbl (label (format "Count : %s" (if-let [cnt (:total-count shallow-v)] cnt "unknown")))
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
                                              (.setOnAction (event-handler [_])))
                                            ))

        container (v-box (cond-> [header-lbl
                                  list-view-pane]
                           more-button (conj (change-more-handler-for-shallow shallow-v))))]
    (VBox/setVgrow list-view-pane Priority/ALWAYS)
    (HBox/setHgrow container Priority/ALWAYS)
    (add-shallow-page-to-list shallow-v)

    container))

(defn- create-shallow-frame-pane [ctx shallow-v]
  (let [def-btn (button :label "def"
                        :class "def-btn"
                        :on-click (fn [] (def-val (:val/full shallow-v))))
        tap-btn (button :label "tap"
                        :class "def-btn"
                        :on-click (fn [] (runtime-api/tap-value rt-api (:val/full shallow-v))))
        buttons-box (doto (h-box [def-btn tap-btn])
                      (.setSpacing 5))
        frame-pane (border-pane {:top (border-pane {:left (label (format "Type: %s" (:val/type shallow-v)))
                                                    :right buttons-box}
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
                                              (runtime-api/shallow-val rt-api v))
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
