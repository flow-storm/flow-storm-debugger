(ns flow-storm.debugger.config
  (:require [flow-storm.state-management :refer [defstate] :as mount]))

(declare config)
(defstate config
  :start (fn [config] config)
  :stop (fn []))

(def debug-mode false)
