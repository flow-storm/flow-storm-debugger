(ns flow-storm.storm-preload
  (:require [cljs.storm.tracer]
            [flow-storm.tracer :as tracer]
            [flow-storm.runtime.debuggers-api :as dbg-api]))

;; setup storm callback functions
(tracer/hook-clojurescript-storm)

(dbg-api/start-runtime)
(dbg-api/remote-connect {})
