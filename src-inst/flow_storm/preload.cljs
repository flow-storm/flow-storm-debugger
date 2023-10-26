(ns flow-storm.preload
  (:require [flow-storm.runtime.debuggers-api :as dbg-api]))

(dbg-api/setup-runtime)
;; automatically try to make a "remote" connection
;; to localhost:7722 (the default)
;; Keep this is for the case where no repl is going to be available
;; so we fire remote connect con preload
(dbg-api/remote-connect {})
