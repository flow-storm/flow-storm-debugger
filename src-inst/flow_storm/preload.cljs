(ns flow-storm.preload
  (:require [flow-storm.runtime.debuggers-api :as dbg-api]))

(dbg-api/start-runtime)
(dbg-api/remote-connect {})
