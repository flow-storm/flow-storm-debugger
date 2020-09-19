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
 ::selected-flow-traces
 :<- [::selected-flow]
 (fn [{:keys [traces]} _]
   traces))

(reg-sub
 ::selected-flow-trace-idx
 :<- [::selected-flow]
 (fn [{:keys [trace-idx]} _]
   trace-idx))

(reg-sub
 ::selected-flow-similar-traces
 :<- [::selected-flow-traces]
 :<- [::selected-flow-trace-idx]
 (fn [[traces trace-idx] _]
   (let [traces (mapv (fn [idx t] (assoc t :trace-idx idx)) (range) traces)
         {:keys [form-id coor]} (get traces trace-idx)
         current-coor (get-in traces [trace-idx :coor])
         similar-traces (->> traces
                             (filter (fn similar [t]
                                       (and (= (:form-id t) form-id)
                                            (= (:coor t)    coor)))))]
     similar-traces)))

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

(reg-sub
 ::selected-flow-bind-traces
 :<- [::selected-flow]
 (fn [{:keys [bind-traces]} _]
   bind-traces))

(defn coor-in-scope? [scope-coor current-coor]
  (if (empty? scope-coor)
    true
    (every? true? (map = scope-coor current-coor))))

(reg-sub
 ::selected-flow-current-locals
 :<- [::selected-flow-current-trace]
 :<- [::selected-flow-bind-traces]
 (fn [[{:keys [coor form-id timestamp] :as curr-trace} bind-traces]]
   (let [in-scope? (fn [bt]
                     (and (= form-id (:form-id bt))
                          (coor-in-scope? (:coor bt) coor)
                          (<= (:timestamp bt) timestamp)))]
     (println "BIND TRACES" bind-traces)
     (when-not (empty? coor)
       (->> bind-traces          ;;(filter in-scope? bind-traces)
            (reduce (fn [r {:keys [symbol value] :as bt}]
                      (if (in-scope? bt)
                        (assoc r symbol value)
                        r))
                    {})
            (into [])
            (sort-by first))))))

(reg-sub
 ::selected-result-panel
 (fn [{:keys [selected-result-panel] :as db} _]
   selected-result-panel))

(reg-sub
 ::current-flow-local-panel
 :<- [::selected-flow]
 (fn [flow _]
   (:local-panel flow)))
