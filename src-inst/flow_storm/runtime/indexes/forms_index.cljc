(ns flow-storm.runtime.indexes.forms-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]))

(defprotocol FormStoreP
  (get-form [_ form-id])
  (all-forms [_]))

(defrecord FormsIndex [forms]
  
  index-protos/BuildIndexP

  (add-form-init [_ {:keys [flow-id thread-id form-id ns form def-kind mm-dispatch-val]}]
    (let [form-data (cond-> {:form/id form-id
                             :form/flow-id flow-id
                             :form/ns ns
                             :form/form form
                             :form/def-kind def-kind}
                      (= def-kind :defmethod)
                      (assoc :multimethod/dispatch-val mm-dispatch-val))]
      (swap! forms assoc form-id form-data))) 
  
  (add-fn-call [_ _]) ; don't do anything for fn-call
  
  (add-expr-exec [_ _]) ; don't do anything for expr-exec
  
  (add-bind [_ _]) ; don't do anything for bind

  FormStoreP
  
  (get-form [_ form-id] (get @forms form-id))

  (all-forms [_] (vals @forms)))

(defn make-index []
  (->FormsIndex (atom {})))
