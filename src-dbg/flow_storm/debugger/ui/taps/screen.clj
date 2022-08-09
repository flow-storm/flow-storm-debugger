(ns flow-storm.debugger.ui.taps.screen
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler button label list-view h-box v-box]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.debugger.values :as dbg-values])
  (:import [javafx.scene.layout Priority VBox]
           [javafx.scene.input MouseButton]
           [javafx.geometry Pos]))

(defn add-tap-value [val]
  (ui-utils/run-later
   (let [[{:keys [add-all]}] (obj-lookup "taps-list-view-data")]
     (add-all [val]))))

(defn main-pane []
  (let [{:keys [list-view-pane clear] :as lv-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell val]
                                       (let [val-list-text (dbg-values/val-pprint val {:print-length 50
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
        clear-btn (doto (button "clear")
                    (.setOnAction (event-handler
                                   [_]
                                   ;; TODO: https://github.com/jpmonettas/flow-storm-debugger/issues/38
                                   ;; when vals are remote send a command to clear all (get-all-items)

                                   ;; clear the list
                                   (clear))))
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
