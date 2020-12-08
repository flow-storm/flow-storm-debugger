(ns flow-storm-debugger.ui.subs.refs
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.utils :as utils]
            [editscript.core :as edit.core]
            [editscript.edit :as edit.edit]
            [taoensso.timbre :as log]))

(defn apply-patches [init-val patches]
  (log/debug "[SUB] apply-patches firing")
  (->> patches
       (map :patch)
       (reduce (fn [r p]
                 (edit.core/patch r (edit.edit/edits->script p)))
               init-val)))

(defn empty-refs? [context]
  (log/debug "[SUB] empty-refs? firing")
  (empty? (fx/sub-val context :refs)))

(defn refs [context]
  (log/debug "[SUB] refs firing")
  (fx/sub-val context :refs))

(defn selected-ref [context]
  (log/debug "[SUB] selected-ref firing")
  (let [selected-ref-id (fx/sub-val context :selected-ref-id)
        refs (fx/sub-ctx context refs)]
    (get refs selected-ref-id)))

(defn selected-ref-value-panel-type [context]
  (log/debug "[SUB] selected-ref-value-panel-type firing")
  (or (:value-panel-type (fx/sub-ctx context selected-ref))
      :tree))

(defn selected-ref-value-panel-content [context pprint?]
  (log/debug "[SUB] selected-ref-value-panel-content firing")
  (let [{:keys [init-val patches patches-applied]} (fx/sub-ctx context selected-ref)
        val-at-idx (apply-patches init-val (take patches-applied patches))
        val (if (zero? patches-applied) init-val val-at-idx)
        ret {:val (if pprint?
                    (utils/pprint-form val)

                    val)}]

    (if (and (not-empty patches) (pos? patches-applied))

      (->> (nth patches (dec patches-applied))
           :patch
           (reduce (fn [r [coor & [op :as opcmd] :as patch-op]]
                     (cond
                       (#{:r :+} op) (update r :patch-map assoc coor opcmd)

                       (#{:-} op) (update r :removes conj patch-op)))                   
                   {:patch-map {}
                    :removes []})
           (merge ret))
      
      ret)))

(defn refs-tabs [context]
  (log/debug "[SUB] refs-tabs firing")
  (let [refs (fx/sub-ctx context refs)]
    (->> refs
         (map (fn [[ref-id ref]]
                [ref-id (:ref-name ref)])))))

(defn selected-ref-controls-position [context]
  (log/debug "[SUB] selected-ref-controls-position firing")
  (let [{:keys [patches-applied patches]} (fx/sub-ctx context selected-ref)]
    {:first? (zero? patches-applied)
     :last? (= patches-applied (count patches))
     :n (inc patches-applied)
     :of (inc (count patches))
     :squash? (> (count patches) 3)}))




