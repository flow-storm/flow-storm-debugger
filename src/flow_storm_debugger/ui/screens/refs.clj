(ns flow-storm-debugger.ui.screens.refs
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.events :as ui.events]
            [flow-storm-debugger.ui.subs.refs :as subs.refs]
            [flow-storm-debugger.ui.screens.components :as components]
            [cljfx.ext.tab-pane :as fx.ext.tab-pane]))

(defn controls-pane [{:keys [fx/context]}]
  (let [{:keys [first? last? n of]} (fx/sub-ctx context subs.refs/selected-ref-controls-position)]
    {:fx/type :h-box
     :alignment :center-left
     :style-class ["controls-pane"]    
     :spacing 5
     :children [{:fx/type :button
                 :on-mouse-clicked {:event/type ::ui.events/selected-ref-first}
                 :style-class ["button" "first-button"]
                 :graphic {:fx/type components/font-icon}
                 :disable first?}
                {:fx/type :button
                 :on-mouse-clicked {:event/type ::ui.events/selected-ref-prev}
                 :style-class ["button" "prev-button"]
                 :graphic {:fx/type components/font-icon}
                 :disable first?}
                {:fx/type :label
                 :text (format "%d / %d" n of)}
                {:fx/type :button
                 :on-mouse-clicked {:event/type ::ui.events/selected-ref-next}
                 :style-class ["button" "next-button"]
                 :graphic {:fx/type components/font-icon}
                 :disable last?}
                {:fx/type :button
                 :on-mouse-clicked {:event/type ::ui.events/selected-ref-last}
                 :style-class ["button" "last-button"]
                 :graphic {:fx/type components/font-icon}
                 :disable last?}]}))

(defn value-pane [{:keys [fx/context]}]
  {:fx/type components/result-pane
   :type-subs subs.refs/selected-ref-value-panel-type
   :result-subs subs.refs/selected-ref-value-panel-content
   :toggle-type-event ::ui.events/set-selected-ref-value-panel-type})

(defn selected-ref [{:keys [fx/context]}]
  {:fx/type :border-pane
   :style-class ["border-pane" "ref-tab-content"]
   :top {:fx/type controls-pane}
   :center {:fx/type value-pane}})

(defn refs-tabs [{:keys [fx/context]}]
  (let [refs-tabs (fx/sub-ctx context subs.refs/refs-tabs)]
    {:fx/type fx.ext.tab-pane/with-selection-props
     :props {:on-selected-item-changed (fn [tab]
                                         (ui.events/event-handler {:event/type ::ui.events/select-ref
                                                                   :ref-id (Integer/parseInt (.getId tab))}))}
     :desc {:fx/type :tab-pane
            :tabs (->> refs-tabs
                       (mapv (fn [[ref-id ref-name]]
                               {:fx/type :tab
                                :fx/key (str ref-id)
                                :style-class ["tab" "ref-tab"]
                                :on-closed {:event/type ::ui.events/remove-ref
                                            :ref-id ref-id}
                                :graphic {:fx/type :label :text ref-name}
                                :content {:fx/type selected-ref}
                                :id (str ref-id)
                                :closable true})))}}))

(defn no-refs [_]
  {:fx/type :anchor-pane
   :style-class ["no-refs"]
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
                                       :text "No refs traced yet. Trace some refs using"}
                                      {:fx/type :label
                                       :style-class ["label" "strong-text"]
                                       :text " flow-storm.api/trace-ref "}
                                      {:fx/type :label
                                       :text "and you will see them displayed here."}]}]}]})
