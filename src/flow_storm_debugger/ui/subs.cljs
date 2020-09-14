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
     (pprint-form-for-html result))))



(reg-sub
 ::selected-flow-forms-highlighted
 (fn [{:keys [selected-flow-id] :as db} _]
   (let [{:keys [forms traces trace-idx]} (get-in db [:flows selected-flow-id])
         current-trace (get traces trace-idx)]
     (->> forms
          (map (fn [[form-id form-str]]
                 (let [form-str (pprint-form-for-html form-str)]
                   [form-id
                    (if (= form-id (:form-id current-trace))
                      (highlight-expr form-str (:coor current-trace) "<b class=\"hl\">" "</b>")
                      form-str)])))))))
