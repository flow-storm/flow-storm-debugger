(ns flow-storm.debugger.ui.flows.diff-timelines
  (:require [flow-storm.debugger.runtime-api :as rt-api :refer [rt-api]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler]]
            [flow-storm.debugger.ui.flows.general :refer [show-message]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :as dbg-state])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.control Label]
           [javafx.scene.layout VBox Priority]))

(defn- diff-lines-from-deltas [deltas {:keys [src-flow-id src-thread-id target-flow-id target-thread-id]}]
  (reduce (fn [lines {:keys [delta/type delta/src-chunk delta/target-chunk]}]
            (let [src-chunk-lines (->> src-chunk
                                       (mapv (fn [l]
                                               (assoc l
                                                      :src? true
                                                      :flow-id src-flow-id
                                                      :thread-id src-thread-id
                                                      :delta-type type))))
                  target-chunk-lines (->> target-chunk
                                          (mapv (fn [l]
                                                  (assoc l
                                                         :flow-id target-flow-id
                                                         :thread-id target-thread-id
                                                         :delta-type type))))]
              (-> lines
                  (into src-chunk-lines)
                  (into target-chunk-lines)
                  (into [{:delta-separator? true}]))))
   []
   deltas))

(defn- threads-combo [flows-threads on-change]
  (let [flow-thread-lbl-factory (fn [_ {:keys [flow-id thread-id thread-name]}]
                                  (ui/label :text (format "Flow %d - %s"
                                                          flow-id
                                                          (ui/thread-label thread-id thread-name))))]
    (ui/combo-box :items flows-threads
                  :cell-factory flow-thread-lbl-factory
                  :button-factory flow-thread-lbl-factory
                  :on-change (fn [_ {:keys [flow-id thread-id]}]
                               (on-change flow-id thread-id)))))

(defn create-diff-timelines-pane []
  (let [flows-threads (->> (rt-api/all-flows-threads rt-api)
                           (keep (fn [[fid tid]]
                                   {:flow-id fid
                                    :thread-id tid
                                    :thread-name (:thread/name (dbg-state/get-thread-info fid tid))})))
        {:keys [flow-id thread-id]} (first flows-threads)
        *threads-selection (atom {:src-flow-id flow-id
                                  :src-thread-id thread-id
                                  :target-flow-id flow-id
                                  :target-thread-id thread-id})
        src-combo (threads-combo flows-threads
                                 (fn [flow-id thread-id]
                                   (swap! *threads-selection assoc :src-flow-id flow-id :src-thread-id thread-id)))
        target-combo (threads-combo flows-threads
                                    (fn [flow-id thread-id]
                                      (swap! *threads-selection assoc :target-flow-id flow-id :target-thread-id thread-id)))

        {:keys [list-view-pane clear add-all] :as lv-data}
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell {:keys [form-preview val-preview src? delta-type delta-separator?]}]
                                      (let [color-class (if src? "diff-delete" "diff-add")]
                                        (-> list-cell
                                            (ui-utils/set-text  nil)
                                            (ui-utils/set-graphic (if delta-separator?
                                                                    (ui/label :text "...")
                                                                    (ui/h-box
                                                                     :spacing 5
                                                                     :class color-class
                                                                     :childs (if val-preview
                                                                               [(ui/label :text form-preview)
                                                                                (ui/label :text "=>")
                                                                                (ui/label :text val-preview)]

                                                                               [(ui/label :text form-preview)])))))))
                      :on-click (fn [mev sel-items {:keys [list-view-pane]}]
                                  (let [{:keys [flow-id thread-id idx]} (first sel-items)]
                                    (when (and (ui-utils/double-click? mev)
                                               flow-id thread-id idx)
                                      (flows-screen/goto-location {:flow-id flow-id
                                                                   :thread-id thread-id
                                                                   :idx idx}))))
                      :selection-mode :single)
        diff-button (ui/button :label "Diff threads"
                               :on-click (fn []
                                           (let [{:keys [src-flow-id src-thread-id target-flow-id target-thread-id] :as ths-sel} @*threads-selection]
                                             (if (and (= src-flow-id target-flow-id)
                                                      (= src-thread-id target-thread-id))
                                               (show-message "Both source and target threads are the same. Please select different ones for diffing" :info)
                                               (let [deltas (rt-api/diff-timelines rt-api src-flow-id src-thread-id target-flow-id target-thread-id)
                                                     lines (diff-lines-from-deltas deltas ths-sel)]
                                                 (clear)
                                                 (add-all lines))))))
        controls-box (ui/h-box :childs [(ui/v-box :childs [(ui/h-box :childs [(ui/label :text "Source thread :") src-combo] :spacing 5)
                                                           (ui/h-box :childs [(ui/label :text "Target thread :") target-combo] :spacing 5)]
                                                  :spacing 5)
                                        diff-button]
                               :spacing 5
                               :align :center-left
                               :paddings [10 10 10 0])]

    (ui/border-pane :top controls-box
                    :center list-view-pane
                    :paddings [10 10 10 10])))

(defn diff-timelines-window []
  (let [window-w 1300
        window-h 900
        scene (Scene. (create-diff-timelines-pane) window-w window-h)
        stage (doto (Stage.)
                (.setTitle "FlowStorm diff threads")
                (.setScene scene))]

    (.setOnCloseRequest stage (event-handler [_] (dbg-state/unregister-jfx-stage! stage)))
    (dbg-state/register-jfx-stage! stage)

    (let [{:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) window-w window-h)]
      (.setX stage x)
      (.setY stage y))

    (-> stage .show)))


(comment
  (diff-timelines-window)
  )
