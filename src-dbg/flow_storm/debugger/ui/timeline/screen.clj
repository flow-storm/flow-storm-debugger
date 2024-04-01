(ns flow-storm.debugger.ui.timeline.screen
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.runtime-api :as rt-api :refer [rt-api]]
            [clojure.string :as str]
            [clojure.set :as set]
            [flow-storm.debugger.state :as dbg-state :refer [obj-lookup store-obj]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.events-queue :as events-queue])
  (:import [javafx.scene.layout Priority VBox]
           [javafx.scene.control TableRow Label CheckBox]))

(set! *warn-on-reflection* true)

(def thread-possible-colors #{"#DAE8FC" "#D5E8D4" "#FFE6CC" "#F8CECC" "#E1D5E7" "#60A917" "#4C0099" "#CC00CC"})

(defn set-recording-check [recording?]
  (let [[^CheckBox record-btn] (obj-lookup "total-order-record-btn")]
    (.setSelected record-btn recording?)))

(defn clear-timeline []
  (let [[{:keys [clear]}] (obj-lookup "total-order-table-data")]
    (clear)))

(defn main-pane []
  (let [{:keys [table-view-pane table-view add-all] :as table-data}
        (ui/table-view :columns ["Thread" "Thread Idx" "Function" "Expression" "Value" "Value type"]
                       :resize-policy :constrained
                       :cell-factory (fn [_ cell-val]
                                       (doto ^Label (ui/label :text (str cell-val))
                                         (.setStyle "-fx-text-fill: #333")))
                       :row-update (fn [^TableRow trow row-vec]
                                     (doto trow
                                       (.setStyle (format "-fx-background-color: %s" (-> row-vec meta :color)))
                                       (.setOnMouseClicked
                                        (event-handler
                                            [mev]
                                          (when (and (ui-utils/mouse-primary? mev)
                                                     (ui-utils/double-click? mev))
                                            (let [{:keys [flow-id thread-id thread-timeline-idx]} (meta row-vec)]
                                              (flows-screen/goto-location {:flow-id flow-id
                                                                           :thread-id thread-id
                                                                           :idx thread-timeline-idx})))))))
                       :search-predicate (fn [[thread-name _ function expr-str expr-val expr-type] search-str]
                                           (boolean
                                            (or (str/includes? thread-name search-str)
                                                (str/includes? function search-str)
                                                (str/includes? expr-str search-str)
                                                (and expr-val (str/includes? expr-val search-str))
                                                (and expr-type (str/includes? expr-type search-str)))))
                       :items [])
        record-btn (ui/check-box :selected? false)

        _ (ui-utils/set-button-action
           record-btn
           (fn [] (rt-api/set-total-order-recording rt-api (ui-utils/checkbox-checked? record-btn))))
        refresh-btn (ui/icon-button :icon-name "mdi-reload"
                                    :on-click (fn []
                                                (let [thread-selected-colors (atom {})
                                                      timeline-task-id (rt-api/total-order-timeline-task rt-api)
                                                      thread-color (fn [thread-id]
                                                                     (if-let [color (get @thread-selected-colors thread-id)]
                                                                       color
                                                                       (let [new-color (first (set/difference thread-possible-colors
                                                                                                              (into #{} (vals @thread-selected-colors))))]
                                                                         (swap! thread-selected-colors assoc thread-id new-color)
                                                                         new-color)))]
                                                  (events-queue/add-dispatch-fn
                                                   :tote-timeline
                                                   (fn [[ev-type {:keys [task-id batch]}]]
                                                     (when (= timeline-task-id task-id)
                                                       (case ev-type
                                                         :task-progress (ui-utils/run-later
                                                                          (->> batch
                                                                               (mapv (fn [{:keys [thread-timeline-idx type thread-id fn-ns fn-name expr-str expr-type expr-val-str] :as tl-entry}]
                                                                                       (let [{:keys [thread/name]} (dbg-state/get-thread-info thread-id)
                                                                                             idx thread-timeline-idx]
                                                                                         (with-meta
                                                                                           (case type
                                                                                             :fn-call   [(ui/thread-label thread-id name) idx  (format "%s/%s" fn-ns fn-name)  ""       ""           ""]
                                                                                             :fn-return [(ui/thread-label thread-id name) idx "RETURN"                         ""       expr-val-str expr-type]
                                                                                             :fn-unwind [(ui/thread-label thread-id name) idx "UNWIND"                         ""       "" expr-type]
                                                                                             :expr-exec [(ui/thread-label thread-id name) idx ""                               expr-str expr-val-str expr-type])
                                                                                           (assoc tl-entry :color (thread-color thread-id))))))
                                                                               add-all))
                                                         :task-finished (events-queue/rm-dispatch-fn :tote-timeline)
                                                         nil))))
                                                  (rt-api/start-task rt-api timeline-task-id)))
                                    :tooltip "Refresh the content of the timeline")
        main-pane (ui/border-pane
                   :top (ui/h-box
                         :childs [refresh-btn (ui/label :text "Enable:") record-btn]
                         :class "controls-box"
                         :spacing 5)

                   :center table-view-pane
                   :class "timeline-tool")]

    (store-obj "total-order-record-btn" record-btn)
    (store-obj "total-order-table-data" table-data)

    (VBox/setVgrow table-view Priority/ALWAYS)

    main-pane))

(comment

  )
