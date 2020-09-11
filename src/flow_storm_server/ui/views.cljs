(ns flow-storm-server.ui.views
  (:require [reagent.core :as r]
            [flow-storm-server.highlighter :refer [highlight-expr]]
            [zprint.core :as zp]
            [cljs.tools.reader :as tools-reader]
            [re-frame.core :refer [dispatch subscribe]]
            [flow-storm-server.ui.events :as events]
            [flow-storm-server.ui.subs :as subs]))

(defn main-screen []
  (let [{:keys [form trace trace-idx]} @(subscribe [::subs/state])
        coor (:coor (get trace trace-idx))
        form-str (zp/zprint-str form)
        result  (try
                  (-> (:result (get trace trace-idx))
                      (tools-reader/read-string)
                      zp/zprint-str)
                  (catch js/Error e
                    (:result (get trace trace-idx))))
        hl-expr (highlight-expr form-str coor "<b class=\"hl\">" "</b>")]
    [:div.screen
     [:div.controls.panel
      [:button {:on-click #(dispatch [::events/prev])
                :disabled (zero? trace-idx)}"<"]
      [:button {:on-click #(dispatch [::events/next])
                :disabled (>= trace-idx (count trace))}">"]
      [:span (str trace-idx "/" (count trace))]]

     [:div.code-result-cont
      [:div.code.panel
       [:pre {:dangerouslySetInnerHTML {:__html hl-expr}}]]

      [:div.result.panel
       [:pre {:dangerouslySetInnerHTML {:__html result}}]]]

     #_[:div.debug.panel
        [:div (str "Current coor: " coor)]
        [:div (str "Trace: " trace)]]]))
