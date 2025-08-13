(ns flow-storm.debugger.ui.data-windows.data-windows
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.utils :as utils :refer [log-error]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.commons :refer [copy-val def-val]]
            [flow-storm.debugger.ui.data-windows.visualizers :as visualizers])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.layout Priority VBox HBox]))

(defn data-window-pane [{:keys [data-window-id]}]
  (let [breadcrums-box (ui/h-box :childs []
                                 :spacing 10
                                 :class "breadcrums")
        visualizers-combo-box (ui/h-box :childs [])
        val-box   (ui/v-box :childs []
                            :spacing 10
                            :paddings [10 0 0 0])
        type-lbl (ui/label :text "")
        def-btn (ui/button :label "def"
                           :classes ["def-btn" "btn-sm"]
                           :tooltip "Define a reference to this value so it can be used from the repl.")
        copy-btn (ui/icon-button :icon-name "mdi-content-copy"
                                 :tooltip "Copy the pprint of the current value"
                                 :classes ["btn-sm"])
        val-pane  (ui/border-pane :top (ui/h-box :childs [visualizers-combo-box type-lbl def-btn copy-btn]
                                                 :align :center-left
                                                 :spacing 5)
                                  :center val-box)
        dw-box (ui/v-box
                 :childs [(ui/label :text (format "Data Window id: %s" data-window-id))
                          breadcrums-box
                          val-pane]
                 :class "data-window"
                 :spacing 10
                 :paddings [10 10 10 10])]

    (dbg-state/data-window-create data-window-id
                                  {:breadcrums-box breadcrums-box
                                   :visualizers-combo-box visualizers-combo-box
                                   :val-box val-box
                                   :type-lbl type-lbl
                                   :def-btn def-btn
                                   :copy-btn copy-btn})

    (VBox/setVgrow val-pane Priority/ALWAYS)
    (HBox/setHgrow val-pane Priority/ALWAYS)
    (VBox/setVgrow val-box Priority/ALWAYS)
    (HBox/setHgrow val-box Priority/ALWAYS)
    (VBox/setVgrow dw-box Priority/ALWAYS)
    (HBox/setHgrow dw-box Priority/ALWAYS)

    dw-box))

(defn- destroy-visualizers-for-frames [frames]
  (doseq [fr frames]
    (when-let [on-destroy (-> fr :visualizer :on-destroy)]
      (try
        (on-destroy (-> fr :visualizer-val-ctx))
        (catch Exception e
          (utils/log-error
           (str "Couldn't destroy visualizer " (:visualizer fr))
           e))))))

(defn- create-data-window [dw-id]
  (try
    (let [inspector-w 1000
          inspector-h 600
          stage (doto (Stage.)
                  (.setTitle "FlowStorm data window"))
          scene (Scene. (data-window-pane {:data-window-id dw-id})
                        inspector-w
                        inspector-h)]

      (.setScene stage scene)

      (.setOnCloseRequest stage (event-handler [_]
                                  (dbg-state/unregister-jfx-stage! stage)
                                  (destroy-visualizers-for-frames (:stack (dbg-state/data-window dw-id)))
                                  (dbg-state/data-window-remove dw-id)))
      (dbg-state/register-jfx-stage! stage)

      (let [{:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) inspector-w inspector-h)]
        (.setX stage x)
        (.setY stage y))

      (-> stage .show))

    (catch Exception e
      (log-error "UI Thread exception" e))))

(defn create-data-window-for-vref [vref]
  (let [dw-id (keyword (str "value-" (:vid vref)))]
    (create-data-window dw-id)
    (runtime-api/data-window-push-val-data rt-api dw-id vref {:stack-key "/" :dw-id dw-id})))

(defn push-val
  ([dw-id val-data] (push-val dw-id val-data {}))
  ([dw-id val-data {:keys [root? visualizer preferred-size stack-key]}]
   (ui-utils/run-now

     ;; this is to allow a data-window to be created by a push from the runtime
     (when-not (dbg-state/data-window dw-id)
       (create-data-window dw-id))

     (let [{:keys [breadcrums-box visualizers-combo-box val-box type-lbl def-btn copy-btn]} (dbg-state/data-window dw-id)
           visualizers (visualizers/appliable-visualizers val-data)
           reset-val-box (fn []
                           (let [val-node (-> (dbg-state/data-window dw-id) :stack peek :visualizer-val-ctx :fx/node)
                                 {:flow-storm.runtime.values/keys [meta-ref type val-ref]} (-> (dbg-state/data-window dw-id) :stack peek :val-data)
                                 meta-preview (-> meta-ref meta :val-preview)]
                             (ui-utils/set-button-action def-btn (fn [] (def-val val-ref)))
                             (ui-utils/set-button-action copy-btn (fn [] (copy-val val-ref)))
                             (ui-utils/set-text type-lbl type)

                             (VBox/setVgrow val-node Priority/ALWAYS)
                             (HBox/setHgrow val-node Priority/ALWAYS)

                             (ui-utils/observable-clear (.getChildren val-box))
                             (ui-utils/observable-add-all
                              (.getChildren val-box)
                              (cond->> [val-node]
                                meta-ref (into [(ui/label :text (format "Meta: %s" meta-preview)
                                                          :class "link-lbl"
                                                          :on-click (fn [_]
                                                                      (let [extras {:dw-id dw-id
                                                                                    :stack-key "META"}]
                                                                        (runtime-api/data-window-push-val-data rt-api dw-id meta-ref extras))))])))))

           reset-viz-combo (fn []
                             (let [viz-combo (-> (dbg-state/data-window dw-id) :stack peek :visualizer-combo)]
                               (ui-utils/observable-clear (.getChildren visualizers-combo-box))
                               (ui-utils/observable-add-all (.getChildren visualizers-combo-box) [viz-combo])))

           default-viz (or (and visualizer (visualizers/visualizer visualizer))
                           (visualizers/default-visualizer val-data)
                           (first visualizers))

           create-viz (fn [{:keys [on-create]}]
                        (try
                          (on-create (cond-> val-data
                                       true     (assoc ::dw-id dw-id)
                                       preferred-size (assoc ::preferred-size preferred-size)))
                          (catch Exception e
                            {:fx/node (ui/text-area
                                       :text (pr-str e)
                                       :editable? false
                                       :class "monospaced")})))
           viz-combo (ui/combo-box :items visualizers
                                   :selected default-viz
                                   :cell-factory   (fn [_ {:keys [id]}] (ui/label :text (str id)))
                                   :button-factory (fn [_ {:keys [id]}] (ui/label :text (str id)))
                                   :on-change (fn [_ new-visualizer]
                                                (let [new-visualizer-val-ctx (create-viz new-visualizer)
                                                      old-frame (dbg-state/data-window-update-top-frame dw-id {:visualizer new-visualizer
                                                                                                               :visualizer-val-ctx new-visualizer-val-ctx})]
                                                  (destroy-visualizers-for-frames [old-frame])
                                                  (reset-val-box))))

           reset-breadcrums (fn reset-breadcrums []
                              (let [stack (:stack (dbg-state/data-window dw-id))
                                    bbox-childs (.getChildren breadcrums-box)]
                                (ui-utils/observable-clear bbox-childs)
                                (let [btns (->> stack
                                                (map-indexed (fn [idx frame]
                                                               (let [depth (- (count stack) idx)]
                                                                 (doto (ui/button :label (or (:stack-key frame) (format "unnamed-frame-<%d>" depth))
                                                                                  :on-click (fn []
                                                                                              (let [popped-frames (dbg-state/data-window-pop-stack-to-depth dw-id depth)]
                                                                                                (destroy-visualizers-for-frames popped-frames)
                                                                                                (reset-breadcrums)
                                                                                                (reset-viz-combo)
                                                                                                (reset-val-box))))
                                                                   (.setMaxWidth 120)))))
                                                reverse
                                                (into []))]
                                  (ui-utils/observable-add-all bbox-childs btns))))

           default-viz-val-ctx (create-viz default-viz)]

       (when root?
         (let [popped-frames (dbg-state/data-window-pop-stack-to-depth dw-id 0)]
           (destroy-visualizers-for-frames popped-frames)))

       (dbg-state/data-window-push-frame dw-id {:val-data val-data
                                                :stack-key stack-key
                                                :visualizer-combo viz-combo
                                                :visualizer default-viz
                                                :visualizer-val-ctx default-viz-val-ctx})

       (reset-breadcrums)
       (reset-viz-combo)
       (reset-val-box)))))

(defn update-val [dw-id update-data]
  (let [{:keys [stack]} (dbg-state/data-window dw-id)
        {:keys [val-data visualizer visualizer-val-ctx]} (peek stack)
        {:keys [on-update]} visualizer]
    (when on-update
      (ui-utils/run-later
        (on-update val-data visualizer-val-ctx update-data)))))


(comment
  (ui-utils/run-later (create-data-window :data-1))

  ;; stack
  (push-val :data-1
            {:flow-storm.runtime.values/kind :map
             :preview-str "{:a 10}"
             :keys-previews [":a"]
             :keys-refs [10]
             :vals-previews ["10"]
             :vals-refs [15]})

  (push-val :data-1
            {:flow-storm.runtime.values/kind :vec
             :stack-key ":hello"
             :preview-str "[1 2 3 4]"
             :vals-previews ["1" "2" "3" "4"]
             :vals-refs [20 21 22 23]})

  (push-val :data-1
            {:flow-storm.runtime.values/kind :string
             :preview-str "hello world"
             :val-ref 55})

  ;; lazy sequence

  (ui-utils/run-later (create-data-window :data-2))

  (push-val :data-2
            {:flow-storm.runtime.values/kind :lazy-seq
             :preview-str "(1 2 3 ...)"
             :page-previews ["1" "2" "3"]
             :page-refs [61 62 63]
             :more? true
             :next-ref 77
             })

  (update-val :data-2
              {:page-previews ["4" "5" "6"]
               :page-refs [64 65 66]
               :more? true
               :next-ref 88})

  (update-val :data-2
              {:page-previews ["7" "8" "9"]
               :page-refs [67 68 69]
               :more? false})

  ;; scope

  (ui-utils/run-later (create-data-window :data-3))

  (push-val :data-3
            {:flow-storm.runtime.values/kind :number
             :val 0})

  (dotimes [i 5]
    (update-val :data-3
                {:new-val (inc i)})
    (Thread/sleep 1000))

  (:stack (dbg-state/data-window :data-1))
  )
