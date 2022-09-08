(ns flow-storm.debugger.ui.taps.screen
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [button label list-view h-box v-box]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.value-inspector :as value-inspector])
  (:import [javafx.scene.layout Priority VBox]
           [javafx.scene.input MouseButton]
           [javafx.geometry Pos]))

(defn add-tap-value [val]
  (ui-utils/run-later
   (let [[{:keys [add-all]}] (obj-lookup "taps-list-view-data")]
     (add-all [val]))))

(defn clear-all-taps []
  (ui-utils/run-later
   (let [[{:keys [clear]}] (obj-lookup "taps-list-view-data")]
     (clear))))

(defn main-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell val]
                                       (let [val-list-text (runtime-api/val-pprint rt-api val {:print-length 50
                                                                                               :print-level 5
                                                                                               :print-meta? false
                                                                                               :pprint? false})]
                                         (.setText list-cell nil)
                                         (.setGraphic list-cell (label val-list-text))))
                    :on-click (fn [mev sel-items _]
                                (when (and (= MouseButton/PRIMARY (.getButton mev))
                                           (= 2 (.getClickCount mev)))
                                  (let [val (first sel-items)]
                                    (value-inspector/create-inspector val))))
                    :selection-mode :single})
        clear-btn (button :label "clear"
                          :on-click (fn [] (clear-all-taps)))
        header-pane (doto (h-box [clear-btn])
                      (.setAlignment Pos/CENTER_RIGHT))
        mp (v-box [header-pane list-view-pane])]

    (VBox/setVgrow list-view-pane Priority/ALWAYS)
    (VBox/setVgrow mp Priority/ALWAYS)

    (store-obj "taps-list-view-data" lv-data)
    mp))

(comment
  (add-tap-value {:a 1 :b 2})
  (add-tap-value {:a 1 :b 3})
  )
