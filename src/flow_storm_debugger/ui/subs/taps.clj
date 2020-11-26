(ns flow-storm-debugger.ui.subs.taps
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.utils :as utils]))

(defn empty-taps? [context]
  (empty? (fx/sub-val context :taps)))

(defn taps [context]
  (fx/sub-val context :taps))

(defn selected-tap [context]
  (let [selected-tap-id (fx/sub-val context :selected-tap-id)
        taps (fx/sub-ctx context taps)]
    (get taps selected-tap-id)))

(defn selected-tap-value-panel-type [context]
  (or (:value-panel-type (fx/sub-ctx context selected-tap))
      :pprint))

(defn selected-tap-value [context]
  (let [{:keys [tap-trace-idx tap-values]} (fx/sub-ctx context selected-tap)]
    (:value (get tap-values tap-trace-idx))))

(defn selected-tap-value-panel-content [context pprint?]
  (let [current-val (fx/sub-ctx context selected-tap-value)]
    {:val (if pprint?
            (utils/pprint-form-str current-val)

            (utils/read-form current-val))}))

(defn taps-tabs [context]
  (let [taps (fx/sub-ctx context taps)]
    (->> taps
         (map (fn [[tap-id tap]]
                [tap-id (:tap-name tap)])))))

(defn selected-tap-values [context]
  (let [{:keys [tap-values tap-trace-idx] :as t} (fx/sub-ctx context selected-tap)]
    (assoc-in tap-values [tap-trace-idx :selected?] true)))
