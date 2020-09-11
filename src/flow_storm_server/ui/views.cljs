(ns flow-storm-server.ui.views
  (:require [reagent.core :as r]
            [flow-storm-server.highlighter :refer [highlight-expr]]
            [re-frame.core :refer [dispatch subscribe]]
            [zprint.core :as zp]
            [flow-storm-server.ui.events :as events]
            [flow-storm-server.ui.subs :as subs]))

(defn main-screen []
  (let [{:keys [form traces trace-idx] :as state} @(subscribe [::subs/state])
        last-trace (dec (count traces))
        coor (:coor (get traces trace-idx))
        form-str (zp/zprint-str form)
        result  (-> (get traces trace-idx)
                    :result
                    zp/zprint-str)
        hl-expr (highlight-expr form-str coor "<b class=\"hl\">" "</b>")]
    [:div.screen
     [:div.controls.panel
      [:button {:on-click #(dispatch [::events/prev])
                :disabled (zero? trace-idx)} "<"]
      [:button {:on-click #(dispatch [::events/next])
                :disabled (>= trace-idx last-trace)}">"]
      [:span (str trace-idx "/" last-trace)]]

     [:div.code-result-cont
      [:div.code.panel
       [:pre {:dangerouslySetInnerHTML {:__html hl-expr}}]]

      [:div.result.panel
       [:pre {:dangerouslySetInnerHTML {:__html result}}]]]

     [:div.debug.panel
      [:div (str "State" state)]
      [:div (str "Current coor: " coor)]
      [:div (str "Form string " (pr-str form-str))]
        [:div (str "Trace: " traces)]]]))
