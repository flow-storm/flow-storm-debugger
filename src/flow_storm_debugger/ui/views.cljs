(ns flow-storm-debugger.ui.views
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe]]
            [flow-storm-debugger.ui.events :as events]
            [flow-storm-debugger.ui.subs :as subs]
            [cljs.tools.reader :as tools-reader]
            [goog.string :as gstring]))

(defn show-file-loader [] (.click (js/document.getElementById "file-loader")))

(defn controls-panel [traces trace-idx]
  (let [last-trace (dec (count traces))]
    [:div.panel.controls-panel
     [:div.time-controls
      [:button {:on-click #(dispatch [::events/set-current-flow-trace-idx 0])}
       "Reset"]
      [:button {:on-click #(dispatch [::events/selected-flow-prev])
                :disabled (zero? trace-idx)} "<"]
      [:button {:on-click #(dispatch [::events/selected-flow-next])
                :disabled (>= trace-idx last-trace)}">"]]

     (when (pos? last-trace)
        [:span.trace-count (str trace-idx "/" last-trace)])

     [:div.tools-controls
      [:button {:on-click #(dispatch [::events/open-save-panel])}
       "Save"]
      [:button {:on-click show-file-loader} "Load"]]]))

(defn save-flow-panel []
  (let [file-name (r/atom nil)]
    (fn []
      [:div.save-flow-panel.panel
       [:label "Save flow as:"]
       [:input {:type :text
                :on-change (fn [e] (reset! file-name (-> e .-target .-value)))
                :on-key-press (fn [e] (when (= (-> e .-key) "Enter")
                                        (dispatch [::events/save-selected-flow @file-name])))
                :value @file-name}]])))

(defn code-panel [traces trace-idx]
  (let [hl-forms @(subscribe [::subs/selected-flow-forms-highlighted])]
    [:div.panel.code-panel
     [:div.code.scrollable
      (for [[form-id hl-form-str] hl-forms]
        ^{:key form-id}
        [:pre.form {:dangerouslySetInnerHTML {:__html hl-form-str}}])]]))

(defn layers-panel []
  (let [similar-traces @(subscribe [::subs/selected-flow-similar-traces])
        trace-idx @(subscribe [::subs/selected-flow-trace-idx])]
    [:ul.tool.layers
     (for [t similar-traces]
       ^{:key (str (:trace-idx t))}
       [:li.layer {:class (when (= trace-idx (:trace-idx t)) "hl")
                    :on-click #(dispatch [::events/set-current-flow-trace-idx (:trace-idx t)])}
        (:result t)])]))

(defn pprint-result-panel []
  (let [selected-flow-result @(subscribe [::subs/selected-flow-result])]
    [:div.tool.pprint-result
     [:pre {:dangerouslySetInnerHTML {:__html selected-flow-result}}]]))

(defn calls-tree [{:keys [fn-name args-vec result childs call-trace-idx ret-trace-idx] :as fn-call-tree} current-trace-idx]
  (when-not (empty? fn-call-tree)
    [:div.indent
     [:div.call {:on-click #(dispatch [::events/set-current-flow-trace-idx call-trace-idx])
                 :class (when (= call-trace-idx current-trace-idx) "active")}
      [:span "("] [:span.fn-name fn-name] [:span.args-vec (str args-vec)] [:span ")"]]
     (for [c childs]
       ^{:key (or (:call-trace-idx c) (:ret-trace-idx c))}
       [calls-tree c current-trace-idx])
     [:div.return {:on-click #(dispatch [::events/set-current-flow-trace-idx ret-trace-idx])
                   :class (when (= ret-trace-idx current-trace-idx) "active")}
      [:span.fn-result result] [:span.fn-name (str "<" fn-name ">")]]]))

(defn calls-panel []
  (let [fn-call-tree @(subscribe [::subs/fn-call-traces])
        current-trace-idx @(subscribe [::subs/selected-flow-trace-idx])]
    [:div.tool.calls
     [calls-tree fn-call-tree current-trace-idx]]))

(defn result-panel []
  (let [selected-result-panel @(subscribe [::subs/selected-result-panel])
        tabs [:pprint :layers :calls]]
    [:div.panel.result-panel
     [:div.result-tabs
      (for [t tabs]
        ^{:key (name t)}
        [:div.tab  {:on-click #(dispatch [::events/select-result-panel t])
                    :class (when (= selected-result-panel t) "active")}
         (name t)])]
     [:div.result-tab-content
      (case selected-result-panel
        :pprint [pprint-result-panel]
        :layers [layers-panel]
        :calls  [calls-panel])]]))

(defn locals-panel []
  (let [locals @(subscribe [::subs/selected-flow-current-locals])]
   [:ul.locals.panel
    (for [[symbol value] locals]
      ^{:key symbol}
      [:li {:on-click #(dispatch [::events/show-local symbol value])}
       [:span.symbol symbol] [:span.value value]])]))

(defn local-panel [symbol value]
  [:div.local-panel.panel
   [:div.symbol symbol]
   [:pre.value value]])

(defn flow [{:keys [traces trace-idx]}]
  (let [[local-symb local-value] @(subscribe [::subs/current-flow-local-panel])
        save-flow-panel-open? @(subscribe [::subs/save-flow-panel-open?])]
   [:div.selected-flow

    ;; Modals

    (when (or save-flow-panel-open?
              local-symb)
      [:div.modal-overlay {:on-click #(dispatch [::events/hide-modals])}])

    (when local-symb
      [local-panel local-symb local-value])

    (when save-flow-panel-open? [save-flow-panel])

    [controls-panel traces trace-idx]

    [code-panel traces trace-idx]

    [result-panel]

    [locals-panel]



    #_(let [{:keys [coor form-id] :as trace} (get traces trace-idx)]
        [:div.debug.panel
         [:div (str "Current coor: " coor)]
         [:div (str "Form id " form-id)]
         [:div (str "Trace " (str trace))]])]))

(defn main-screen []
  (let [selected-flow @(subscribe [::subs/selected-flow])
        flows-tabs @(subscribe [::subs/flows-tabs])]

    [:div.main-screen

     [:div.flows

      [:div.top-bar

       [:div.flows-tabs
        (for [[flow-id flow-name] flows-tabs]
          ^{:key flow-id}
          [:div.tab {:on-click #(dispatch [::events/select-flow flow-id])
                     :class (when (= flow-id (:id selected-flow)) "active")}
           [:span.name flow-name]
           [:span.close {:on-click (fn [evt]
                                     (.stopPropagation evt)
                                     (dispatch [::events/remove-flow flow-id]))}"X"]])]
       [:div.load-flow
        [:input {:type :file
                 :id "file-loader"
                 :style {:display :none}
                 :on-change (fn [e] (let [file (-> e .-target .-files (aget 0))]
                                      (.then (.text file) (fn [file-text]
                                                            (dispatch [::events/load-flow (tools-reader/read-string file-text)])))))}]]]

      (if (zero? (count flows-tabs))
        [:div.no-flows
         [:div "No flows traced yet. Trace some forms using "
          [:a {:href "http://github.com/jpmonettas/flow-storm"} "flow-storm.api/trace"]
          " and you will see them displayed here."]
         [:div.load "Or " [:a {:href "#" :on-click show-file-loader} "click here"] " to load some traces from your disk."]]

        [flow selected-flow])]]))
