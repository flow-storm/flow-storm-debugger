(ns flow-storm-debugger.ui.screens.main
  (:require [cljfx.api :as fx]            
            [flow-storm-debugger.ui.events :as ui.events]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.subs.refs :as subs.refs]
            [flow-storm-debugger.ui.subs.taps :as subs.taps]
            [cljfx.ext.tab-pane :as fx.ext.tab-pane]
            [flow-storm-debugger.ui.subs.general :as subs.general]
            [flow-storm-debugger.ui.screens.flows :as screens.flows]
            [flow-storm-debugger.ui.screens.refs :as screens.refs]
            [flow-storm-debugger.ui.screens.taps :as screens.taps]
            [flow-storm-debugger.ui.screens.timeline :as screens.timeline]
            ))

(defn bottom-bar [{:keys [fx/context]}]
  (let [{:keys [received-traces-count connected-clients]} (fx/sub-ctx context subs.general/stats)]
    {:fx/type :border-pane
     ;;:pref-height 50
     :right {:fx/type :label
             :text (format "Connected clients: %d Received traces: %d" connected-clients received-traces-count)}
     :style-class ["bar"]}))

(defn flows-tab [{:keys [fx/context]}]
  (let [no-flows? (fx/sub-ctx context subs.flows/empty-flows?)]
    {:fx/type :tab
     :id "flows"
     :style-class ["tab" "tool-tab"]
     :closable false
     :graphic {:fx/type :label
               :text "Flows"}
     :content (if no-flows?
                {:fx/type screens.flows/no-flows}
                {:fx/type screens.flows/flow-tabs})}))


(defn refs-tab [{:keys [fx/context]}]
  (let [no-refs? (fx/sub-ctx context subs.refs/empty-refs?)]
    {:fx/type :tab
     :id "refs"
     :style-class ["tab" "tool-tab"]
     :closable false
     :graphic {:fx/type :label
               :text "Refs"}
     :content (if no-refs?
                {:fx/type screens.refs/no-refs}
                {:fx/type screens.refs/refs-tabs})}))

(defn taps-tab [{:keys [fx/context]}]
  (let [no-taps? (fx/sub-ctx context subs.taps/empty-taps?)]
    {:fx/type :tab
     :id "taps"
     :style-class ["tab" "tool-tab"]
     :closable false
     :graphic {:fx/type :label
               :text "Taps"}
     :content (if no-taps?
                {:fx/type screens.taps/no-taps}
                {:fx/type screens.taps/taps-tabs})}))

(defn timeline-tab [{:keys [fx/context]}]
  {:fx/type :tab
   :id "timeline"
   :style-class ["tab" "tool-tab"]
   :closable false
   :graphic {:fx/type :label
             :text "Timeline"}
   :content {:fx/type screens.timeline/timeline}})

(defn main-screen [{:keys [fx/context]}]
  (let [open-dialog (fx/sub-val context :open-dialog)
        selected-tool-idx (fx/sub-val context :selected-tool-idx) 
        {:keys [app-styles font-styles]} (fx/sub-val context :styles)
        ui-blocked? (fx/sub-val context :blocked?)
        main-screen {:fx/type :stage
                     :title "Flow Storm debugger"
                     :showing true
                     :on-close-request (fn [& _] (System/exit 0))
                     :width 1600
                     :height 900
                     :scene {:fx/type :scene
                             :on-key-pressed {:event/type ::ui.events/key-pressed}
                             :stylesheets [font-styles app-styles]
                             :root {:fx/type :border-pane
                                    :disable (or ui-blocked? false)
                                    :center {:fx/type  fx.ext.tab-pane/with-selection-props
                                             :props {:on-selected-index-changed {:event/type ::ui.events/select-tools-tab}
                                                     :selected-index selected-tool-idx}
                                             :desc {:fx/type :tab-pane
                                                    :side :left
                                                    :id "tools-tab-pane"
                                                    :style-class ["tab-pane" "tools-tab-pane"]
                                                    :rotate-graphic true
                                                    :tabs [{:fx/type flows-tab}
                                                           {:fx/type refs-tab}
                                                           {:fx/type taps-tab}
                                                           {:fx/type timeline-tab}]}}
                                    
                                    :bottom {:fx/type bottom-bar}}}}]
    {:fx/type fx/ext-many
     :desc (cond-> [main-screen]
             open-dialog (into [{:fx/type (case open-dialog
                                            :save-flow-dialog screens.flows/save-flow-dialog)}]))}))


