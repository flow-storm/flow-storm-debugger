(ns flow-storm.runtime.indexes.storm-form-registry
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.utils :refer [log-error]])
  (:import [clojure.storm FormRegistry]))

(defrecord StormFormRegistry []
  index-protos/FormRegistryP

  (all-forms [_]
    (FormRegistry/getAllForms))

  (get-form [_ form-id]
    (if form-id
      (FormRegistry/getForm form-id)
      (log-error "ERROR : can't get form for id null")))

  (start-form-registry [this] this)
  (stop-form-registry [_]))

(defn make-storm-form-registry []
  (->StormFormRegistry))
