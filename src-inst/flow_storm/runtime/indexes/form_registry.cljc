(ns flow-storm.runtime.indexes.form-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]))

(defrecord FormRegistry [*registry]

  index-protos/FormRegistryP

  (register-form [_ form-id form]
    (swap! *registry assoc form-id form))

  (all-forms [_]
    (vals @*registry))

  (get-form [_ form-id]
    (get @*registry form-id))

  (start-form-registry [this] this)
  (stop-form-registry [_]))

(defn make-form-registry []
  (->FormRegistry (atom {})))
