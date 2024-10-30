(ns flow-storm.debugger.ui.taps.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.tasks :as tasks]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows])
  (:import [javafx.scene.layout Priority VBox]))


(defn add-tap-value [val]
  (ui-utils/run-later
    (let [[{:keys [add-all]}] (obj-lookup "taps-list-view-data")]
      (add-all [val]))))

(defn clear-all-taps []
  (ui-utils/run-later
    (let [[{:keys [clear]}] (obj-lookup "taps-list-view-data")]
      (clear))))

(defn find-and-jump-tap-val [vref]
  (tasks/submit-task runtime-api/find-expr-entry-task
                     [{:from-idx 0
                       :identity-val vref}]
                     {:on-finished (fn [{:keys [result]}]
                                     (when result
                                       (ui-general/select-main-tools-tab "tool-flows")
                                       (flows-screen/goto-location result)))}))

(defn main-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell val]
                                      (let [val-list-text (-> (runtime-api/val-pprint rt-api val {:print-length 50
                                                                                                  :print-level 5
                                                                                                  :print-meta? false
                                                                                                  :pprint? false})
                                                              :val-str
                                                              utils/remove-newlines)]
                                        (-> list-cell
                                            (ui-utils/set-text nil)
                                            (ui-utils/set-graphic (ui/label :text val-list-text)))))
                      :on-click (fn [mev sel-items {:keys [list-view]}]
                                  (let [val (first sel-items)]
                                    (cond
                                      (and (ui-utils/mouse-primary? mev)
                                           (ui-utils/double-click? mev))
                                      (data-windows/create-data-window-for-vref val)

                                      (ui-utils/mouse-secondary? mev)
                                      (let [ctx-menu (ui/context-menu
                                                      :items [{:text "Search value on Flows"
                                                               :on-click (fn [] (find-and-jump-tap-val val))}])]
                                        (ui-utils/show-context-menu :menu ctx-menu
                                                                    :parent list-view
                                                                    :mouse-ev mev)))))
                      :selection-mode :single)
        clear-btn (ui/button :label "clear"
                             :on-click (fn [] (clear-all-taps)))
        header-pane (ui/h-box :childs [clear-btn]
                              :align :center-right)

        mp (ui/v-box :childs [header-pane list-view-pane])]


    (VBox/setVgrow list-view-pane Priority/ALWAYS)
    (VBox/setVgrow mp Priority/ALWAYS)

    (store-obj "taps-list-view-data" lv-data)
    mp))

(comment
  (add-tap-value {:a 1 :b 2})
  (add-tap-value {:a 1 :b 3})
  )
