(ns flow-storm.preload
  (:require [flow-storm.api :as fs-api]))

(fs-api/setup-runtime)
;; automatically try to make a "remote" connection
;; to localhost:7722 (the default)
;; Keep this is for the case where no repl is going to be available
;; so we fire remote connect con preload
(fs-api/remote-connect {})
