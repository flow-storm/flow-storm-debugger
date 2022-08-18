(ns flow-storm.runtime.indexes.fn-call-stats-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]))

(defprotocol FnCallStatsP
  (all-stats [_]))

(defrecord FnCallStatsIndex [stats]

  index-protos/BuildIndexP

  (add-form-init [_ _]) ; don't do anything for form-init
  
  (add-fn-call [_ fn-call]    
    (let [call (select-keys fn-call [:flow-id :thread-id :form-id :fn-name :fn-ns])]
      (swap! stats update call (fnil inc 0))))
  
  (add-expr-exec [_ _]) ; don't do anything for expr-exec
  
  (add-bind [_ _]) ; don't do anything for bind

  FnCallStatsP

  (all-stats [_] @stats))

(defn make-index []
  (->FnCallStatsIndex (atom {})))

