(ns flow-storm-debugger.ui.subs.taps
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.utils :as utils]
            [taoensso.timbre :as log]))

(defn empty-taps? [context]
  (log/debug "[SUB] empty-taps? firing")
  (empty? (fx/sub-val context :taps)))

(defn taps [context]
  (log/debug "[SUB] taps firing")
  (fx/sub-val context :taps))

(defn selected-tap [context]
  (log/debug "[SUB] selected-tap firing")
  (let [selected-tap-id (fx/sub-val context :selected-tap-id)
        taps (fx/sub-ctx context taps)]
    (get taps selected-tap-id)))

(defn selected-tap-value-panel-type [context]
  (log/debug "[SUB] selected-tap-value-panel-type firing")
  (or (:value-panel-type (fx/sub-ctx context selected-tap))
      :pprint))

(defn selected-tap-value [context]
  (log/debug "[SUB] selected-tap-value firing")
  (let [{:keys [tap-trace-idx tap-values]} (fx/sub-ctx context selected-tap)]
    (:value (get tap-values tap-trace-idx))))

(defn selected-tap-value-panel-content [context pprint?]
  (log/debug "[SUB] selected-tap-value-panel-content firing")
  (let [current-val (fx/sub-ctx context selected-tap-value)]
    {:val (if pprint?
            (utils/pprint-form-str current-val)

            (utils/read-form current-val))}))

(defn taps-tabs [context]
  (log/debug "[SUB] taps-tabs firing")
  (let [taps (fx/sub-ctx context taps)]
    (->> taps
         (map (fn [[tap-id tap]]
                [tap-id (:tap-name tap)])))))

(defn selected-tap-values [context]
  (log/debug "[SUB] selected-tap-values firing")
  (let [{:keys [tap-values tap-trace-idx] :as t} (fx/sub-ctx context selected-tap)]
    (assoc-in tap-values [tap-trace-idx :selected?] true)))
