(ns flow-storm.debugger.ui.taps.screen
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [button label list-view h-box v-box]]
            [flow-storm.debugger.state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.debugger.ui.flows.general :as ui-general])
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

(defn find-and-jump-tap-val [vref]
  (when-let [tentry (runtime-api/find-timeline-entry rt-api {:from-idx 0
                                                             :comp-fn-key :identity
                                                             :eq-val-ref vref})]
    (ui-general/select-main-tools-tab :flows)
    (flows-screen/goto-location tentry)))

(defn main-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell val]
                                       (let [val-list-text (:val-str (runtime-api/val-pprint rt-api val {:print-length 50
                                                                                                         :print-level 5
                                                                                                         :print-meta? false
                                                                                                         :pprint? false}))]
                                         (.setText list-cell nil)
                                         (.setGraphic list-cell (label val-list-text))))
                    :on-click (fn [mev sel-items {:keys [list-view]}]
                                (let [val (first sel-items)]
                                  (cond
                                    (and (= MouseButton/PRIMARY (.getButton mev))
                                         (= 2 (.getClickCount mev)))
                                    (value-inspector/create-inspector val {})

                                    (= MouseButton/SECONDARY (.getButton mev))
                                    (let [ctx-menu (ui-utils/make-context-menu [{:text "Search value on Flows"
                                                                                 :on-click (fn [] (find-and-jump-tap-val val))}])]
                                      (ui-utils/show-context-menu ctx-menu
                                                                  list-view
                                                                  (.getScreenX mev)
                                                                  (.getScreenY mev))))))
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
