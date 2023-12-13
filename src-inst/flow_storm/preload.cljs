(ns flow-storm.preload
  (:require [flow-storm.runtime.debuggers-api :as dbg-api]))

(dbg-api/setup-runtime)
(dbg-api/remote-connect {})
