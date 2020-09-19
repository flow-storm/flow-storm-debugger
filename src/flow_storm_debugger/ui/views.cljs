(ns flow-storm-debugger.ui.views
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe]]
            [flow-storm-debugger.ui.events :as events]
            [flow-storm-debugger.ui.subs :as subs]))

(defn controls-panel [traces trace-idx]
  (let [last-trace (dec (count traces))]
    [:div.panel
     [:div.controls
      [:button {:on-click #(dispatch [::events/selected-flow-prev])
                :disabled (zero? trace-idx)} "<"]
      [:button {:on-click #(dispatch [::events/selected-flow-next])
                :disabled (>= trace-idx last-trace)}">"]
      (when (pos? last-trace)
        [:span.trace-count (str trace-idx "/" last-trace)])]]))

(defn code-panel [traces trace-idx]
  (let [hl-forms @(subscribe [::subs/selected-flow-forms-highlighted])]
    [:div.panel.code-panel
     [:div.code.scrollable
      (for [[form-id hl-form-str] hl-forms]
        ^{:key form-id}
        [:pre.form {:dangerouslySetInnerHTML {:__html hl-form-str}}])]]))

(defn time-result-panel []
  (let [similar-traces @(subscribe [::subs/selected-flow-similar-traces])
        trace-idx @(subscribe [::subs/selected-flow-trace-idx])]
    [:ul.result.traces.scrollable
     (for [t similar-traces]
       ^{:key (str (:trace-idx t))}
       [:li.trace {:class (when (= trace-idx (:trace-idx t)) "hl")
                    :on-click #(dispatch [::events/set-current-flow-trace-idx (:trace-idx t)])}
        (:result t)])]))

(defn pprint-result-panel []
  (let [selected-flow-result @(subscribe [::subs/selected-flow-result])]
    [:div.result.pprint-result.scrollable
     [:pre {:dangerouslySetInnerHTML {:__html selected-flow-result}}]]))

(defn explore-result-panel []
  [:div.result.explore "Coming soon..."])

(defn result-panel []
  (let [selected-result-panel @(subscribe [::subs/selected-result-panel])
        locals @(subscribe [::subs/selected-flow-current-locals])
        tabs [:pprint :explorer :time]]
    [:div.panel.result-panel
     [:div.result-tabs
      (for [t tabs]
        [:div.tab  {:on-click #(dispatch [::events/select-result-panel t])
                    :class (when (= selected-result-panel t) "active")}
         (name t)])]
     (case selected-result-panel
       :pprint   [pprint-result-panel]
       :explorer [explore-result-panel]
       :time     [time-result-panel])

     [:ul.locals
      (for [[symbol value] locals]
        ^{:key symbol}
        [:li {:on-click #(dispatch [::events/show-local symbol value])}
         [:span.symbol symbol] [:span.value value]])]]))

(defn local-panel [symbol value]
  [:div
   [:div.local-panel-overlay {:on-click #(dispatch [::events/hide-local-panel])}]
   [:div.local-panel.panel
    [:div.symbol symbol]
    [:div.value value]]])

(defn flow [{:keys [traces trace-idx]}]
  (let [[local-symb local-value] @(subscribe [::subs/current-flow-local-panel])]
   [:div.selected-flow

    [controls-panel traces trace-idx]

    [:div.flow-code-result

     [code-panel traces trace-idx]

     [result-panel]

     (when local-symb
       [local-panel local-symb local-value])]

    #_(let [{:keys [coor form-id] :as trace} (get traces trace-idx)]
        [:div.debug.panel
         [:div (str "Current coor: " coor)]
         [:div (str "Form id " form-id)]
         [:div (str "Trace " (str trace))]])]))

(defn main-screen []
  (let [selected-flow @(subscribe [::subs/selected-flow])
        flows-tabs @(subscribe [::subs/flows-tabs])]

    [:div.main-screen

     (if (zero? (count flows-tabs))

       [:div.no-flows
        "No flows traced yet. Trace some forms using "
        [:a {:href "http://github.com/jpmonettas/flow-storm"} "flow-storm.api/trace"]
        " and you will see them displayed here."]

       [:div.flows

        [:div.flows-tabs
         (for [[flow-id flow-name] flows-tabs]
           ^{:key flow-id}
           [:div.tab {:on-click #(dispatch [::events/select-flow flow-id])
                      :class (when (= flow-id (:id selected-flow)) "active")}
            [:span.name flow-name]
            [:span.close {:on-click (fn [evt]
                                      (.stopPropagation evt)
                                      (dispatch [::events/remove-flow flow-id]))}"X"]])]

        [flow selected-flow]])]))
