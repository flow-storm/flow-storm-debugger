(ns flow-storm.debugger.config
  (:require [mount.core :refer [defstate] :as mount]))

(declare config)
(defstate config
  :start (mount/args)
  :stop nil)

(def debug-mode false)
