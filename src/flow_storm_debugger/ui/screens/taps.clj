(ns flow-storm-debugger.ui.screens.taps
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.events :as ui.events]
            [flow-storm-debugger.ui.subs.taps :as subs.taps]
            [flow-storm-debugger.ui.screens.components :as components]
            [cljfx.ext.tab-pane :as fx.ext.tab-pane]
            [cljfx.ext.list-view :as fx.ext.list-view]
            [flow-storm-debugger.ui.events :as ui.events]))

(defn value-pane [{:keys [fx/context]}]
  {:fx/type components/result-pane
   :type-subs subs.taps/selected-tap-value-panel-type
   :result-subs subs.taps/selected-tap-value-panel-content
   :toggle-type-event ::ui.events/set-selected-tap-value-panel-type})

(defn selected-tap [{:keys [fx/context]}]
  (let [tap-values (fx/sub-ctx context subs.taps/selected-tap-values)
        selected-item (some #(when (:selected? %) %) tap-values)]
    {:fx/type :split-pane
    :orientation :horizontal
    :style-class ["border-pane" "horizontal-split-pane"]
    :items [{:fx/type fx.ext.list-view/with-selection-props
                :props {:selection-mode :single
                        :on-selected-item-changed {:event/type ::ui.events/set-current-tap-trace-idx}
                        :selected-item selected-item}
                :desc {:fx/type :list-view
                       :style-class ["list-view" "taps-list-view"]
                       :cell-factory {:fx/cell-type :list-cell
                                      :describe (fn [{:keys [value selected?]}]                                
                                                  {:text ""
                                                   :style (if selected?
                                                            {:-fx-background-color "#902638"}
                                                            {})
                                                   :graphic {:fx/type :label
                                                             :style-class ["label" "clickable"]
                                                            
                                                             :text value}})}
                       :items tap-values}}
               {:fx/type value-pane}]}))

(defn taps-tabs [{:keys [fx/context]}]
  (let [taps-tabs (fx/sub-ctx context subs.taps/taps-tabs)]
    {:fx/type fx.ext.tab-pane/with-selection-props
     :props {:on-selected-item-changed {:event/type ::ui.events/select-tap}}
     :desc {:fx/type :tab-pane
            :tabs (->> taps-tabs
                       (mapv (fn [[tap-id tap-name]]
                               {:fx/type :tab
                                :fx/key (str tap-id)
                                :style-class ["tab" "tap-tab"]
                                :on-closed {:event/type ::ui.events/remove-tap
                                            :tap-id tap-id}
                                :graphic {:fx/type :label :text tap-name}
                                :content {:fx/type selected-tap}
                                :id (str tap-id)
                                :closable true})))}}))

(defn no-taps [_]
  {:fx/type :anchor-pane
   :style-class ["no-taps"]
   :children [{:fx/type :v-box
               :pref-width 200
               :anchor-pane/left 100
               :anchor-pane/right 100
               :anchor-pane/top 100
               :alignment :center
               :spacing 20
               :children [{:fx/type :text-flow
                           :pref-width Double/MAX_VALUE
                           :text-alignment :center
                           :children [{:fx/type :label
                                       :text "No taps traced yet. Everything you tap> in your connected processes will be displayed here."}]}]}]})
