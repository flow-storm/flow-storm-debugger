(ns flow-storm-debugger.ui.subs
  (:require [re-frame.core :refer [reg-sub]]
            [zprint.core :as zp]
            [flow-storm-debugger.highlighter :refer [highlight-expr]]
            [clojure.string :as str]
            [cljs.tools.reader :as tools-reader]))


(defn escape-html [s]
  (str/escape s {\< "&lt;" \> "&gt;"}))

(defn pprint-form-for-html [s]
  (try
   (-> s
       tools-reader/read-string
       zp/zprint-str
       escape-html)
   (catch :default e
     (js/console.error "Couldn't pprint for html :" e)
     (js/console.error "String" s)
     s)))

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
     (pprint-form-for-html result))))

(reg-sub
 ::selected-flow-forms
 :<- [::selected-flow]
 (fn [{:keys [forms] :as flow} _]
   (->> forms
        (map (fn [[form-id form-str]]
               [form-id (pprint-form-for-html form-str)])))))

(reg-sub
 ::selected-flow-forms
 :<- [::selected-flow]
 (fn [{:keys [forms]} _]
   forms))

(reg-sub
 ::selected-flow-forms-pprinted
 :<- [::selected-flow-forms]
 (fn [forms _]
   (->> forms
        (map (fn [[form-id form-str]]
               [form-id (pprint-form-for-html form-str)])))))

(reg-sub
 ::selected-flow-current-trace
 :<- [::selected-flow]
 (fn [{:keys [traces trace-idx]} _]
   (get traces trace-idx)))


(reg-sub
 ::selected-flow-forms-highlighted
 :<- [::selected-flow-forms-pprinted]
 :<- [::selected-flow-current-trace]
 (fn [[forms current-trace] _]
   (->> forms
        (map (fn [[form-id form-str]]
               [form-id
                (if (= form-id (:form-id current-trace))
                  (highlight-expr form-str (:coor current-trace) "<b class=\"hl\">" "</b>")
                  form-str)])))))
