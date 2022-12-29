(ns flow-storm.debugger.docs
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.fn-sampler.docs :as docs]
            [flow-storm.utils :refer [log]]))

(declare start)
(declare stop)
(declare fn-docs)

(defstate fn-docs
  :start (start)
  :stop (stop))

(defn start []
  (log "[Starting docs subsystem]")

  (-> (docs/read-classpath-docs)
      :functions/data))

(defn stop []
  (log "[Stopping docs subsystem]")
  nil)
