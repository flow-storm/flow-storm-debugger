(ns flow-storm.runtime.outputs
  (:require [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]
            [amalloy.ring-buffer :refer [ring-buffer]]))

(defonce *last-evals-results (atom (ring-buffer 10)))

(defonce *tap-fn (atom nil))

(defn setup-tap! []
  (when-not @*tap-fn
    (let [tap-f (fn [v]                
                  (let [vref (rt-values/reference-value! v)]
                    (rt-events/publish-event! (rt-events/make-tap-event vref))))]
      (add-tap tap-f)
      (reset! *tap-fn tap-f))))

(defn remove-tap! []
  (when-let [tfn @*tap-fn]
    (remove-tap tfn)
    (reset! *tap-fn nil)))

(defn- fire-update-last-evals-event []
  (let [last-evals-refs (->> @*last-evals-results
                             (mapv rt-values/reference-value!))]    
    (rt-events/publish-event! (rt-events/make-last-evals-update-event last-evals-refs))))

(defn clear-outputs []
  (reset! *last-evals-results (ring-buffer 10))
  (fire-update-last-evals-event))

(defn handle-eval-result [v]
  (swap! *last-evals-results conj v)
  (fire-update-last-evals-event))

(defn handle-out-write [s]
  (rt-events/publish-event! (rt-events/make-out-write-event s)))

(defn handle-err-write [s]
  (rt-events/publish-event! (rt-events/make-err-write-event s)))
