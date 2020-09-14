(ns flow-storm-debugger.ui.views
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe]]
            [flow-storm-debugger.ui.events :as events]
            [flow-storm-debugger.ui.subs :as subs]))

(defn flow [{:keys [traces trace-idx]}]
  (let [hl-forms @(subscribe [::subs/selected-flow-forms-highlighted])
        selected-flow-result @(subscribe [::subs/selected-flow-result])
        last-trace (dec (count traces))]
    [:div.selected-flow

    [:div.controls.panel
     [:button {:on-click #(dispatch [::events/selected-flow-prev])
               :disabled (zero? trace-idx)} "<"]
     [:button {:on-click #(dispatch [::events/selected-flow-next])
               :disabled (>= trace-idx last-trace)}">"]
     (when (pos? last-trace)
       [:span.trace-count (str trace-idx "/" last-trace)])]

    [:div.flow-code-result
     [:div.code.panel
      (for [[form-id hl-form-str] hl-forms]
        ^{:key form-id}
        [:pre.form {:dangerouslySetInnerHTML {:__html hl-form-str}}])]

     [:div.result.panel
      [:pre {:dangerouslySetInnerHTML {:__html selected-flow-result}}]]]

     (let [{:keys [coor form-id] :as trace} (get traces trace-idx)]
       [:div.debug.panel
        [:div (str "Current coor: " coor)]
        [:div (str "Form id " form-id)]
        [:div (str "Trace " (str trace))]])]))

(defn main-screen []
  (let [selected-flow @(subscribe [::subs/selected-flow])
        flows-ids @(subscribe [::subs/flows-ids])]

    [:div.main-screen

     (if (zero? (count flows-ids))

       [:div.no-flows
        "No flows traced yet. Trace some forms using "
        [:a {:href "http://github.com/jpmonettas/flow-storm"} "flow-storm.api/trace"]
        " and you will see them displayed here."]

       [:div.flows

        [:div.flows-tabs
         (for [flow-id flows-ids]
           ^{:key flow-id}
           [:div.tab {:on-click #(dispatch [::events/select-flow flow-id])
                      :class (when (= flow-id (:id selected-flow)) "active")}

            [:span.close {:on-click (fn [evt]
                                      (.stopPropagation evt)
                                      (dispatch [::events/remove-flow flow-id]))}"X"]])]

        [flow selected-flow]])]))
