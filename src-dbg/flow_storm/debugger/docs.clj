(ns flow-storm.debugger.docs
  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.fn-sampler.docs :as docs]))

(declare start)
(declare stop)
(declare fn-docs)

(defstate fn-docs
  :start (fn [_] (start))
  :stop (fn [] (stop)))

(defn start []
  (-> (docs/read-classpath-docs)
      :functions/data))

(defn stop []
  nil)
