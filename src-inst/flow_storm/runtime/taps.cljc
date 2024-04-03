(ns flow-storm.runtime.taps
  (:require [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]))

(defonce tap-fn (atom nil))

(defn setup-tap! []
  (when-not @tap-fn
    (let [tap-f (fn [v]                
                  (let [vref (rt-values/reference-value! v)]
                    (rt-events/publish-event! (rt-events/make-tap-event vref))))]
      (add-tap tap-f)
      (reset! tap-fn tap-f))))

(defn remove-tap! []
  (when-let [tfn @tap-fn]
    (remove-tap tfn)
    (reset! tap-fn nil)))

