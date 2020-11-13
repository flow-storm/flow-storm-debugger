(ns flow-storm-debugger.ui.subs.flows
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.highlighter :refer [highlight-expr]]
            [flow-storm-debugger.ui.utils :as utils]))

(defn flows [context]
  (fx/sub-val context :flows))

(defn selected-flow [context]
  (let [selected-flow-id (fx/sub-val context :selected-flow-id)
        flows (fx/sub-ctx context flows)]
    (-> (get flows selected-flow-id)
        (assoc :id selected-flow-id))))

(defn selected-flow-current-trace [context]
  (let [{:keys [traces trace-idx]} (fx/sub-ctx context selected-flow)]
    (get traces trace-idx)))

(defn selected-flow-forms [context]
  (let [selected-flow (fx/sub-ctx context selected-flow)]
    (:forms selected-flow)))

(defn selected-flow-forms-highlighted [context]
  (let [forms (fx/sub-ctx context selected-flow-forms)
        current-trace (fx/sub-ctx context selected-flow-current-trace)]
   (->> forms
        (mapv (fn [[form-id form-str]]
                (let [h-form-str (cond-> form-str

                                   ;; if it is the current-one, highlight it
                                   (= form-id (:form-id current-trace))
                                   (highlight-expr (:coor current-trace) "<b id=\"expr\" class=\"hl\">" "</b>"))]
                  
                 [form-id h-form-str]))))))

(defn flow-comparator [f1 f2]
  (compare (:timestamp f1) (:timestamp f2)))

(defn flows-tabs [context]
  (let [flows (fx/sub-ctx context flows)]
    (->> flows
         (sort-by second flow-comparator)
         (map (fn [[flow-id flow]]
                [flow-id (:flow-name flow)])))))

(defn selected-flow-result-panel-content [context pprint?]
  (let [content (:result-panel-content (fx/sub-ctx context selected-flow))]
    (if pprint?
      (utils/pprint-form-str content)
      (-> content
          utils/read-form))))

(defn selected-flow-result-panel-type [context]
  (or (:result-panel-type (fx/sub-ctx context selected-flow))
      :pprint))

(defn coor-in-scope? [scope-coor current-coor]
  (if (empty? scope-coor)
    true
    (every? true? (map = scope-coor current-coor))))

(defn trace-locals [{:keys [coor form-id timestamp]} bind-traces]
  (let [in-scope? (fn [bt]
                    (and (= form-id (:form-id bt))
                         (coor-in-scope? (:coor bt) coor)
                         (<= (:timestamp bt) timestamp)))]
    (when-not (empty? coor)
      (->> bind-traces
           (reduce (fn [r {:keys [symbol value] :as bt}]
                     (if (in-scope? bt)
                       (assoc r symbol value)
                       r))
                   {})))))

(defn selected-flow-bind-traces [context]
  (let [sel-flow (fx/sub-ctx context selected-flow)]
    (:bind-traces sel-flow)))

(defn selected-flow-current-locals [context]
  (let [{:keys [result] :as curr-trace} (fx/sub-ctx context selected-flow-current-trace)
        bind-traces (fx/sub-ctx context selected-flow-bind-traces)
        locals-map (trace-locals curr-trace bind-traces)]
    (->> locals-map
         (into [])
         (sort-by first)
         (into [["=>" result true]]))))

(defn selected-flow-similar-traces [context]
  (let [{:keys [traces trace-idx]} (fx/sub-ctx context selected-flow)
        traces (mapv (fn [idx t] (assoc t :trace-idx idx :selected? (= idx trace-idx))) (range) traces)
        {:keys [form-id coor]} (get traces trace-idx)
        current-coor (get-in traces [trace-idx :coor])
        similar-traces (->> traces
                            (filter (fn similar [t]
                                      (and (= (:form-id t) form-id)
                                           (= (:coor t)    coor)
                                           (:result t)))))]
    similar-traces))

(defn empty-flows? [context]
  (empty? (fx/sub-val context :flows)))

(defn fn-call-trace? [trace]
  (:args-vec trace))

(defn ret-trace? [trace]
  (and (:result trace)
       (:outer-form? trace)))

(defn build-tree-from-traces [traces]
  (loop [[t & r] (rest traces)
         tree (-> (first traces)
                  (assoc :childs []))
         path [:childs]]
    (let [last-child-path (into path [(count (get-in tree path))])]
      (cond
        (nil? t) tree
        (fn-call-trace? t) (recur r
                                  (update-in tree last-child-path #(merge % (assoc t :childs [])))
                                  (into last-child-path [:childs]))
        (ret-trace? t) (let [ret-pointer (vec (butlast path))]
                         (recur r
                                (if (empty? ret-pointer)
                                  (merge tree t)
                                  (update-in tree ret-pointer merge t ))
                                (vec (butlast (butlast path)))))))))

(defn selected-flow-traces [context]
  (:traces (fx/sub-ctx context selected-flow)))

(defn selected-flow-errors [context]
  (let [{:keys [traces trace-idx]} (fx/sub-ctx context selected-flow)]
    (->> (filter :err traces)
         (reduce (fn [r {:keys [coor form-id] :as t}]
                   ;; if it is a exception that is bubling up, discard it
                   ;; only keep exception origin traces
                   (if (some #(and (= form-id (:form-id t))
                                   (utils/parent-coor? coor (:coor %))) r)
                     r
                     (conj r (cond-> t
                               (= (:trace-idx t) trace-idx) (assoc :selected? true)))))
                 []))))

(defn selected-flow-trace-idx [context]
  (:trace-idx (fx/sub-ctx context selected-flow)))

(defn selected-flow-forms [context]
  (:forms (fx/sub-ctx context selected-flow)))

(defn fn-call-traces [context]
  (let [traces  (fx/sub-ctx context selected-flow-traces)
        forms (fx/sub-ctx context selected-flow-forms)
        call-traces (->> traces
                         (map-indexed (fn [idx t]
                                        (if (fn-call-trace? t)
                                          (assoc t :call-trace-idx idx)
                                          (assoc t :ret-trace-idx idx))))
                         (filter (fn [t] (or (fn-call-trace? t)
                                             (ret-trace? t)))))]
    (when (some #(:fn-name %) call-traces)
      (build-tree-from-traces call-traces))))

