(ns flow-storm.runtime.objects-registry
  (:require [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]
            [amalloy.ring-buffer :refer [ring-buffer]]))

(def ring-size 100)
(def empty-registry {:buffer (ring-buffer ring-size)
                     :objects #{}})
(defonce *registry (atom empty-registry))
(defonce *objects-registry-enable (atom false))
(defonce *objects-registry-pred (atom #?(:clj (fn [o] (instance? java.io.Closeable o))
                                        :cljs (constantly false))))

(defn objects-registry-enable? []
  @*objects-registry-enable)

(defn turn-objects-registry [enable?]
  (reset! *objects-registry-enable enable?)
  (rt-events/publish-event! (rt-events/make-objects-registry-enable-update-event enable?)))

(defn set-objects-registry-pred [p]
  (reset! *objects-registry-pred p))

(defn pred-matches? [obj]
  (@*objects-registry-pred obj))

(defn- fire-update-registry-event []
  (let [registry-refs (->> @*registry
                           :buffer
                           (mapv rt-values/reference-value!))]    
    (rt-events/publish-event! (rt-events/make-objects-registry-update-event registry-refs))))

(defn clear-registry []
  (reset! *registry empty-registry)
  (fire-update-registry-event))

(defn store-object [o]
  (swap! *registry (fn [{:keys [buffer objects] :as reg}]
                     (if (contains? objects o)
                       reg ;; if we have the object, don't do anything

                       (if (<= (count buffer) ring-size)
                         ;; if the buf isn't full just add it
                         (-> reg
                             (update :buffer conj o)
                             (update :objects conj o))

                         ;; if it is already full, remove whatever is going out of
                         ;; the ring from the objects set
                         (let [leaving-obj (first buffer)]
                           (-> reg
                               (update :buffer conj o)
                               (update :objects conj o)
                               (update :objects disj leaving-obj)))))))
  (fire-update-registry-event))
