(ns flow-storm.runtime.taps
  (:require [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]))

(def tap-fn (atom nil))

(defn setup-tap! []
  (let [tap-f (fn [v]                
                (let [vref (rt-values/reference-value! v)]
                  (rt-events/enqueue-event! (rt-events/make-tap-event vref))))]
    (add-tap tap-f)
    (reset! tap-fn tap-f)))

(defn remove-tap! []
  (remove-tap @tap-fn)
  (reset! tap-fn nil))

