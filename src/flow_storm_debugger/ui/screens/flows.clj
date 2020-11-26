(ns flow-storm-debugger.ui.screens.flows
  (:require [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.events :as ui.events]
            [clojure.string :as str]
            [cljfx.prop :as fx.prop]
            [cljfx.api :as fx]
            [flow-storm-debugger.ui.screens.components :as components]
            [cljfx.ext.list-view :as fx.ext.list-view]
            [cljfx.ext.tab-pane :as fx.ext.tab-pane])
  (:import [javafx.scene.control DialogEvent Dialog]
           [javafx.geometry Insets]))

(defn remove-new-lines [s]
  (if s (str/replace s #"\n" " ") ""))

(defn code-browser [{:keys [fx/context]}]
  (let [hl-forms (fx/sub-ctx context subs.flows/selected-flow-forms-highlighted)
        {:keys [code-panel-styles]} (fx/sub-val context :styles)
        forms-html (->> hl-forms
                        (map (fn [[_ form-str]]
                               (str "<pre class=\"form\">" form-str "</pre>")))
                        (reduce str))
        html (str "<html>"
                  "<style>" code-panel-styles "</style>"
                  "<div class=\"forms\">"
                  forms-html
                  "</div>"
                  "<script>document.getElementById('expr').scrollIntoView()</script>"
                  "</html>")]
    {:fx/type components/ext-with-html
    :props {:html html}
    :desc {:fx/type :web-view}}))

(defn load-button [{:keys [icon?]}]
  (cond-> {:fx/type :button
           :text "Load"
           :style-class ["button" "load-button"]
           :on-action {:event/type ::ui.events/load-flow}}
    icon? (assoc :graphic {:fx/type components/font-icon})
    icon? (assoc :text "")))

(defn controls-pane [{:keys [fx/context]}]
  (let [{:keys [traces trace-idx]} (fx/sub-ctx context subs.flows/selected-flow)
        last-trace (dec (count traces))]
    {:fx/type :border-pane
     :style-class ["controls-pane"]    
     :left {:fx/type :h-box
            :spacing 5            
            :children [{:fx/type :button
                        :on-mouse-clicked {:event/type ::ui.events/set-current-flow-trace-idx :trace-idx 0}
                        :style-class ["button" "reset-button"]
                        :graphic {:fx/type components/font-icon}}
                       {:fx/type :button
                        :on-mouse-clicked {:event/type ::ui.events/selected-flow-prev}
                        :style-class ["button" "prev-button"]
                        :graphic {:fx/type components/font-icon}
                        :disable (zero? trace-idx)}
                       {:fx/type :button
                        :on-mouse-clicked {:event/type ::ui.events/selected-flow-next}
                        :style-class ["button" "next-button"]
                        :graphic {:fx/type components/font-icon}
                        :disable (>= trace-idx last-trace)}]}
     :center {:fx/type :label :text (str trace-idx "/" last-trace)}
     :right {:fx/type :h-box
             :spacing 5
             :children [{:fx/type load-button
                         :icon? true}
                        {:fx/type :button
                         :style-class ["button" "save-button"]
                         :graphic {:fx/type components/font-icon}
                         :on-mouse-clicked {:event/type ::ui.events/open-dialog
                                            :dialog :save-flow-dialog}}]}}))

(defn layers-pane [{:keys [fx/context]}]
  (let [layers (fx/sub-ctx context subs.flows/selected-flow-similar-traces)
        selected-item (some #(when (:selected? %) %) layers)]
    {:fx/type fx.ext.list-view/with-selection-props
     :props {:selection-mode :single
             :on-selected-item-changed  {:event/type ::ui.events/set-current-flow-layer}
             :selected-item selected-item}
     :desc {:fx/type :list-view
            :style-class ["list-view" "layers-view"]
            :cell-factory {:fx/cell-type :list-cell
                           :describe (fn [{:keys [result selected?]}]                                
                                       {:text ""
                                        :style (if selected?
                                                 {:-fx-background-color "#902638"}
                                                 {})
                                        :graphic {:fx/type :label
                                                  :style-class ["label" "clickable"]
                                                  
                                                  :text (remove-new-lines result)}})}
            :items layers}}))

(defn calls-tree [{:keys [fx/context fn-call-tree current-trace-idx]}]
  (when-not (empty? fn-call-tree)
    (let [{:keys [fn-name args-vec result childs call-trace-idx ret-trace-idx]} fn-call-tree]
      {:fx/type :v-box
       :style-class ["v-box" "calls-tree"]
       :children (-> [{:fx/type :h-box
                       :style-class ["h-box" "clickable"]
                       :on-mouse-clicked {:event/type ::ui.events/set-current-flow-trace-idx
                                          :trace-idx (inc call-trace-idx)}
                       :children [{:fx/type :label :text "("}
                                  {:fx/type :label :style {:-fx-font-weight :bold}
                                   :text (str fn-name " ")}
                                  {:fx/type :label :text (str args-vec)}
                                  {:fx/type :label :text ")"}]}]
                     (into (for [c childs]
                             {:fx/type calls-tree
                              :fn-call-tree c
                              :current-trace-idx current-trace-idx}))
                     (into [{:fx/type :h-box
                             :style-class ["h-box" "clickable"]
                             :on-mouse-clicked {:event/type ::ui.events/set-current-flow-trace-idx
                                              :trace-idx ret-trace-idx}
                             :children [{:fx/type :label :text (remove-new-lines result)}
                                        {:fx/type :label :text (str "<" fn-name ">")}]}]))})))

(defn calls-tree-pane [{:keys [fx/context]}]
  (let [fn-call-tree (fx/sub-ctx context subs.flows/fn-call-traces)
        current-trace-idx (fx/sub-ctx context subs.flows/selected-flow-trace-idx)]
   {:fx/type :pane
    :children (if fn-call-tree
                [{:fx/type calls-tree
                                  :fn-call-tree fn-call-tree
                  :current-trace-idx current-trace-idx}]
                [])}))

(defn locals-pane [{:keys [fx/context]}]
  (let [locals (fx/sub-ctx context subs.flows/selected-flow-current-locals)]
    {:fx/type fx.ext.list-view/with-selection-props
     :props {:selection-mode :single
             :on-selected-item-changed {:event/type ::ui.events/select-current-flow-local}}
     :desc {:fx/type :list-view
            :style-class ["list-view" "locals-view"]
            :cell-factory {:fx/cell-type :list-cell
                           :describe (fn [[lname lvalue result?]]                                
                                       {:text ""
                                        :graphic {:fx/type :h-box
                                                  :style-class ["h-box" "clickable"]
                                                  :children [(if result?
                                                               {:fx/type :label
                                                                :style-class ["label" "result-label"]
                                                                :graphic {:fx/type components/font-icon}}
                                                               {:fx/type :label
                                                                :style-class ["label" "local-name"]
                                                                :text lname})
                                                             {:fx/type :label
                                                              :style-class ["label" "local-val"]
                                                              :text (remove-new-lines lvalue)}]}})}
            :items locals}}))

(defn errors-pane [{:keys [fx/context error-traces]}]
  {:fx/type :border-pane
   :top {:fx/type :label :text "Errors:"}
   :center {:fx/type fx.ext.list-view/with-selection-props
            :props {:selection-mode :single}
            :desc {:fx/type :list-view
                   :style-class ["list-view" "errors-view"]
                   :cell-factory {:fx/cell-type :list-cell
                                  :describe (fn [{:keys [err trace-idx selected?]}]                                
                                              {:text ""
                                               :style (if selected? {:-fx-background-color "#902638"} {})
                                               :graphic {:fx/type :label
                                                         :style-class ["label" "clickable"]
                                                         :on-mouse-clicked {:event/type ::ui.events/set-current-flow-trace-idx
                                                                            :trace-idx trace-idx}
                                                         :text (:error/message err)}})}
                   :items error-traces}}})

(defn selected-flow [{:keys [fx/context]}]
  (let [error-traces (fx/sub-ctx context subs.flows/selected-flow-errors)
        left-pane-tabs {:fx/type :tab-pane
                        :tabs [{:fx/type :tab
                                :style-class ["tab" "panel-tab"]
                                :graphic {:fx/type :label :text "Code"}
                                :content {:fx/type code-browser}
                                :id "code"
                                :closable false}
                               {:fx/type :tab
                                :style-class ["tab" "panel-tab"]
                                :graphic {:fx/type :label :text "Layers"}
                                :content {:fx/type layers-pane}
                                :id "layers"
                                :closable false}
                               {:fx/type :tab
                                :style-class ["tab" "panel-tab"]
                                :graphic {:fx/type :label :text "Tree"}
                                :content {:fx/type calls-tree-pane}
                                :id "tree"
                                :closable false}]}]
   {:fx/type :border-pane
    :style {:-fx-padding 10}
    :style-class ["border-pane" "flow-tab-content"]
    :top {:fx/type controls-pane}
    :center {:fx/type :split-pane
             :style-class ["split-pane" "horizontal-split-pane"]
             :border-pane/margin (Insets. 10 0 0 0)
             :items [(if (seq error-traces)
                       {:fx/type :split-pane
                        :style-class ["split-pane" "vertical-split-pane"]
                        :orientation :vertical
                        :items [left-pane-tabs
                                {:fx/type errors-pane
                                 :error-traces error-traces}]}
                       
                       left-pane-tabs)
                    
                     {:fx/type :split-pane
                      :style-class ["split-pane" "vertical-split-pane"]
                      :orientation :vertical
                      :items [{:fx/type components/result-pane
                               :toggle-type-event ::ui.events/set-result-panel-type
                               :type-subs subs.flows/selected-flow-result-panel-type
                               :result-subs subs.flows/selected-flow-result-panel-content}
                              {:fx/type locals-pane}]}]}}))



(defn flow-tabs [{:keys [fx/context]}]
  (let [flows-tabs (fx/sub-ctx context subs.flows/flows-tabs)
        selected-flow-id (fx/sub-val context :selected-flow-id)
        selected-index (->> flows-tabs
                            (map-indexed vector)
                            (some (fn [[i [fid]]]
                                    (when (= fid selected-flow-id)
                                      i))))]
    
    {:fx/type  fx.ext.tab-pane/with-selection-props
     :props {:selected-index selected-index
             :on-selected-item-changed {:event/type ::ui.events/select-flow}}
     :desc {:fx/type :tab-pane
            :tabs (->> flows-tabs
                       (map (fn [[flow-id flow-name]]
                              {:fx/type :tab
                               :style-class ["tab" "flow-tab"]
                               :on-closed {:event/type ::ui.events/remove-flow
                                           :flow-id flow-id}
                               :graphic {:fx/type :label :text flow-name}
                               :content {:fx/type selected-flow} 
                               :id (str flow-id)
                               :closable true})))}}))

(defn save-flow-dialog [_]
  {:fx/type :text-input-dialog
   :showing true
   :header-text "Filename:"
   :on-hidden {:event/type ::ui.events/save-selected-flow}})

(defn no-flows [_]
  {:fx/type :anchor-pane
   :style-class ["no-flows"]
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
                                       :text "No flows traced yet. Trace some forms using"}
                                      {:fx/type :label
                                       :style-class ["label" "strong-text"]
                                       :text " flow-storm.api/trace "}
                                      {:fx/type :label
                                       :text "and you will see them displayed here."}]}
                          {:fx/type :h-box
                           :alignment :center
                           :spacing 10
                           :pref-width Double/MAX_VALUE
                           :children [{:fx/type :label
                                       :text "Or"}
                                      {:fx/type load-button}
                                      {:fx/type :label
                                       :text "some traces from your disk."}]}]}]})
