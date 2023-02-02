(ns flow-storm.runtime.indexes.form-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]))

(deftype FormRegistry [*registry]

  index-protos/FormRegistryP

  (register-form [_ form-id form]
    (swap! *registry assoc form-id form))

  (all-forms [_]
    (vals @*registry))

  (get-form [_ form-id]
    (get @*registry form-id)))

(defn make-form-registry []
  (->FormRegistry (atom {})))
