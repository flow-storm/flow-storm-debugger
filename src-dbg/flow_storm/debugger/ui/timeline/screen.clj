(ns flow-storm.debugger.ui.timeline.screen
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [label table-view h-box border-pane icon-button event-handler thread-label]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [clojure.string :as str]
            [flow-storm.debugger.state :as dbg-state :refer [obj-lookup store-obj]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen])
  (:import [javafx.scene.layout Priority VBox]
           [javafx.scene.control TableRow CheckBox]
           [javafx.scene.input MouseButton]))

(def thread-colors ["#DAE8FC" "#D5E8D4" "#FFE6CC" "#F8CECC" "#E1D5E7" "#60A917" "#4C0099" "#CC00CC"])

(defn set-recording-check [recording?]
  (let [[record-btn] (obj-lookup "total-order-record-btn")]
    (.setSelected record-btn recording?)))

(defn clear-timeline []
  (let [[{:keys [clear]}] (obj-lookup "total-order-table-data")]
    (clear)))

(defn main-pane []
  (let [{:keys [table-view-pane table-view add-all] :as table-data}
        (table-view {:columns ["Thread" "Thread Idx" "Function" "Expression" "Value" "Value type"]
                     :resize-policy :constrained
                     :cell-factory-fn (fn [_ cell-val]
                                        (doto (label (str cell-val))
                                          (.setStyle "-fx-text-fill: #333")))
                     :row-update-fn (fn [^TableRow trow row-vec]
                                      (doto trow
                                        (.setStyle (format "-fx-background-color: %s" (-> row-vec meta :color)))
                                        (.setOnMouseClicked
                                         (event-handler
                                          [mev]
                                          (when (and (= MouseButton/PRIMARY (.getButton mev))
                                                     (= 2 (.getClickCount mev)))
                                            (let [{:keys [flow-id thread-id thread-timeline-idx]} (meta row-vec)]
                                              (flows-screen/goto-location {:flow-id flow-id
                                                                           :thread-id thread-id
                                                                           :idx thread-timeline-idx})))))))
                     :search-predicate (fn [[_ thread-id _ function expression expr-val expr-type] search-str]
                                         (boolean
                                          (or (str/includes? (str thread-id) search-str)
                                              (str/includes? function search-str)
                                              (str/includes? expression search-str)
                                              (and expr-val (str/includes? expr-val search-str))
                                              (and expr-type (str/includes? expr-type search-str)))))
                     :items []})
        record-btn (doto (CheckBox.)
                     (.setSelected false))
        _ (.setOnAction record-btn
           (event-handler [_] (runtime-api/set-total-order-recording rt-api (.isSelected record-btn))))
        refresh-btn (icon-button :icon-name "mdi-reload"
                                 :on-click (fn []
                                             (let [timeline (runtime-api/total-order-timeline rt-api)
                                                   thread-ids (->> timeline
                                                                   (map :thread-id)
                                                                   (into #{}))
                                                   thread-color (zipmap thread-ids thread-colors)]
                                               (->> timeline
                                                    (mapv (fn [{:keys [thread-timeline-idx type thread-id fn-ns fn-name expr-str expr-type expr-val-str] :as tl-entry}]
                                                            (let [{:keys [thread/name]} (dbg-state/get-thread-info thread-id)
                                                                  idx thread-timeline-idx]
                                                              (with-meta
                                                                (case type
                                                                  :fn-call   [(thread-label thread-id name) idx  (format "%s/%s" fn-ns fn-name)  ""       ""           ""]
                                                                  :fn-return [(thread-label thread-id name) idx "RETURN"                         ""       expr-val-str expr-type]
                                                                  :fn-unwind [(thread-label thread-id name) idx "UNWIND"                         ""       "" expr-type]
                                                                  :expr-exec [(thread-label thread-id name) idx ""                               expr-str expr-val-str expr-type])
                                                                (assoc tl-entry :color (thread-color thread-id))))))
                                                    add-all)))
                                 :tooltip "Refresh the content of the timeline")
        main-pane (border-pane
                   {:top (doto (h-box [refresh-btn (label "Enable:") record-btn] "controls-box")
                           (.setSpacing 5))
                    :center table-view-pane}
                   "timeline-tool")]

    (store-obj "total-order-record-btn" record-btn)
    (store-obj "total-order-table-data" table-data)

    (VBox/setVgrow table-view Priority/ALWAYS)

    main-pane))

(comment

  )
