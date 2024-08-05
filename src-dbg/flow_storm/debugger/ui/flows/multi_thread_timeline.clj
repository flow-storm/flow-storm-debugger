(ns flow-storm.debugger.ui.flows.multi-thread-timeline
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.runtime-api :as rt-api :refer [rt-api]]
            [clojure.string :as str]
            [clojure.set :as set]
            [flow-storm.debugger.state :as dbg-state :refer [obj-lookup store-obj]]
            [flow-storm.debugger.events-queue :as events-queue])
  (:import [javafx.scene.layout Priority VBox]
           [javafx.scene.control TableRow Label]
           [javafx.scene Scene]
           [javafx.stage Stage]))


(def thread-possible-colors #{"#DAE8FC"
                              "#D5E8D4"
                              "#FFE6CC"
                              "#F8CECC"
                              "#E1D5E7"
                              "#60A917"
                              "#d45757"
                              "#30cfcf"
                              "#ed55e8"
                              "#d3d929"})

(defn clear-timeline [flow-id]
  (when-let [[{:keys [clear]}] (obj-lookup flow-id "total-order-table-data")]
    (clear)))

(defn- main-pane [flow-id]
  (let [{:keys [table-view-pane table-view add-all clear] :as table-data}
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
                                           (let [{:keys [thread-id thread-timeline-idx]} (meta row-vec)
                                                 goto-loc (requiring-resolve 'flow-storm.debugger.ui.flows.screen/goto-location)]
                                             (goto-loc {:flow-id flow-id
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

        only-functions-cb (ui/check-box :selected? false)
        refresh (fn []
                  (clear)
                  (let [thread-selected-colors (atom {})
                        params {:only-functions? (ui-utils/checkbox-checked? only-functions-cb)
                                :flow-id flow-id}
                        timeline-task-id (rt-api/total-order-timeline-task rt-api params)
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
        refresh-btn (ui/icon-button :icon-name "mdi-reload"
                                    :on-click refresh
                                    :tooltip "Refresh the content of the timeline")

        main-pane (ui/border-pane
                   :top (ui/v-box
                         :childs [(ui/label :text (format "Flow: %d" flow-id))
                                  (ui/h-box
                                   :childs [refresh-btn
                                            (ui/label :text "Only functions? :") only-functions-cb]
                                   :class "controls-box"
                                   :spacing 5)])
                   :center table-view-pane
                   :class "timeline-tool")]

    (store-obj flow-id "total-order-table-data" table-data)

    (VBox/setVgrow table-view Priority/ALWAYS)

    (refresh)

    main-pane))

(defn open-timeline-window [flow-id]
  (let [window-w 1000
        window-h 1000
        scene (Scene. (main-pane flow-id) window-w window-h)
        stage (doto (Stage.)
                (.setTitle "FlowStorm multi-thread timeline browser")
                (.setScene scene))]

    (.setOnCloseRequest stage (event-handler [_] (dbg-state/unregister-jfx-stage! stage)))
    (dbg-state/register-jfx-stage! stage)

    (let [{:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) window-w window-h)]
      (.setX stage x)
      (.setY stage y))

    (-> stage .show)))

(comment

  )
