(ns flow-storm.runtime.objects-registry
  (:require [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [flow-storm.runtime.indexes.api :as index-api]
            [hansel.utils :as hansel-utils]
            [flow-storm.utils :as utils]))

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
                           (mapv (fn [obj-data]
                                   (update obj-data :obj-ref rt-values/reference-value!))))]
    (rt-events/publish-event! (rt-events/make-objects-registry-update-event registry-refs))))

(defn clear-registry []
  (reset! *registry empty-registry)
  (fire-update-registry-event))

(defn store-object [obj-ref form-id coord]
  (let [{:keys [updated?]}
        (swap! *registry (fn [{:keys [buffer objects] :as reg}]
                           (if (contains? objects obj-ref)
                             (assoc reg :updated? false) ;; if we have the object, don't do anything

                             (let [obj-form-data (index-api/get-form form-id)
                                   obj-form (:form/form obj-form-data)
                                   file (:form/file obj-form-data)
                                   obj-sub-form (hansel-utils/get-form-at-coord obj-form (utils/str-coord->vec coord))
                                   {:keys [line]} (meta obj-sub-form)
                                   obj-data {:obj-ref obj-ref
                                             :obj-sub-form obj-sub-form
                                             :file file
                                             :line line}]
                               (if (<= (count buffer) ring-size)
                                 ;; if the buf isn't full just add it
                                 (-> reg
                                     (update :buffer conj obj-data)
                                     (update :objects conj obj-ref)
                                     (assoc :updated? true))

                                 ;; if it is already full, remove whatever is going out of
                                 ;; the ring from the objects set
                                 (let [leaving-obj (first buffer)]
                                   (-> reg
                                       (update :buffer conj obj-data)
                                       (update :objects conj obj-ref)
                                       (update :objects disj (:obj-ref leaving-obj))
                                       (assoc :updated? true))))))))]
    (when updated?
      (fire-update-registry-event))))
