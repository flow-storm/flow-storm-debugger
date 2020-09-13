(ns flow-storm-debugger.ui.subs
  (:require [re-frame.core :refer [reg-sub]]
            [zprint.core :as zp]
            [flow-storm-debugger.highlighter :refer [highlight-expr]]))


(reg-sub
 ::flows-ids
 (fn [db _]
   (keys (:flows db))))

(reg-sub
 ::selected-flow
 (fn [{:keys [selected-flow-id] :as db} _]
   (-> db
       (get-in [:flows selected-flow-id])
       (assoc :id selected-flow-id))))

(reg-sub
 ::selected-flow-result
 (fn [{:keys [selected-flow-id] :as db} _]
   (let [{:keys [traces trace-idx]} (get-in db [:flows selected-flow-id])
         {:keys [result]} (get traces trace-idx)]
    (zp/zprint-str result))))

(reg-sub
 ::selected-flow-forms-highlighted
 (fn [{:keys [selected-flow-id] :as db} _]
   (let [{:keys [forms traces trace-idx]} (get-in db [:flows selected-flow-id])
         current-trace (get traces trace-idx)]
     (->> forms
          (map (fn [[form-id form-expr]]
                 (let [form-str (zp/zprint-str form-expr)]
                   [form-id
                    (if (= form-id (:form-id current-trace))
                      (highlight-expr form-str (:coor current-trace) "<b class=\"hl\">" "</b>")
                      form-str)])))))))
