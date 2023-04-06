(ns flow-storm.runtime.indexes.fn-call-stats-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [mh-put make-mutable-hashmap mh-contains? mh-get mh->immutable-map]]
            [flow-storm.runtime.types.fn-call-trace :as fn-call-trace]))

(defrecord FnCallStatsIndex [stats]

  index-protos/BuildIndexP

  (add-form-init [_ _]) ; don't do anything for form-init
  
  (add-fn-call [_ fn-call]    
    (let [call {:form-id (fn-call-trace/get-form-id fn-call)
                :fn-name (fn-call-trace/get-fn-name fn-call)
                :fn-ns (fn-call-trace/get-fn-ns fn-call)}]
      (if (mh-contains? stats call)
        (let [cnt (mh-get stats call)]
          (mh-put stats call (inc cnt)))
        (mh-put stats call 1))))
  
  (add-expr-exec [_ _]) ; don't do anything for expr-exec
  
  (add-bind [_ _]) ; don't do anything for bind

  index-protos/FnCallStatsP

  (all-stats [_]    
    (mh->immutable-map stats)))

(defn make-index []
  (->FnCallStatsIndex (make-mutable-hashmap)))

