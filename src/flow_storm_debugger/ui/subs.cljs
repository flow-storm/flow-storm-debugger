(ns flow-storm-debugger.ui.subs
  (:require [re-frame.core :refer [reg-sub]]
            [flow-storm-debugger.highlighter :refer [highlight-expr]]
            [flow-storm-debugger.ui.utils :as utils]))


(defn flow-name [forms traces]
  (let [form-id (-> traces
                    first
                    :form-id)
        form (get forms form-id)
        str-len (count form)]
    (cond-> form
      true (subs 0 (min 20 str-len))
      (> str-len 20) (str "..."))))

(reg-sub
 ::flows-tabs
 (fn [db _]
   (->> (:flows db)
        (map (fn [[flow-id {:keys [forms traces]}]]
               [flow-id (flow-name forms traces)])))))

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
     result)))

(reg-sub
 ::selected-flow-forms
 :<- [::selected-flow]
 (fn [{:keys [forms]} _]
   forms))

(reg-sub
 ::selected-flow-current-trace
 :<- [::selected-flow]
 (fn [{:keys [traces trace-idx]} _]
   (get traces trace-idx)))


(reg-sub
 ::selected-flow-forms-highlighted
 :<- [::selected-flow-forms]
 :<- [::selected-flow-current-trace]
 (fn [[forms current-trace] _]
   (->> forms
        (map (fn [[form-id form-str]]
               [form-id
                (if (= form-id (:form-id current-trace))
                  (highlight-expr form-str (:coor current-trace) "<b class=\"hl\">" "</b>")
                  form-str)])))))
