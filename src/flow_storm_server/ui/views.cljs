(ns flow-storm-server.ui.views
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe]]
            [flow-storm-server.ui.events :as events]
            [flow-storm-server.ui.subs :as subs]))

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
     [:span (str trace-idx "/" last-trace)]]

    [:div.flow-code-result
     [:div.code.panel
      (for [[form-id hl-form-str] hl-forms]
        ^{:key form-id}
        [:pre.form {:dangerouslySetInnerHTML {:__html hl-form-str}}])]

     [:div.result.panel
      [:pre {:dangerouslySetInnerHTML {:__html selected-flow-result}}]]]

     #_(let [{:keys [coor form-id]} (get traces trace-idx)]
      [:div.debug.panel
       [:div (str "Current coor: " coor)]
       [:div (str "Form id " form-id)]])]))

(defn main-screen []
  (let [selected-flow @(subscribe [::subs/selected-flow])
        flows-ids @(subscribe [::subs/flows-ids])]

    [:div.main-screen

     [:div.flows

      [:div.flows-tabs
       (for [fid flows-ids]
         ^{:key fid}
         [:div.tab {:on-click #(dispatch [::events/select-flow fid])
                    :class (when (= fid (:id selected-flow)) "active")}
          fid])]

      [flow selected-flow]]]))
