(ns dev
  (:require [flow-storm.json-serializer :as ser]
            [cognitect.transit :as transit :refer [tagged-value]]
            [flow-storm.trace-types :as tt]))

(comment
  (def t (tt/->FnCallTrace 0 1 "foo" "foo-ns" 11 [42 41] 0))

  (ser/serialize t)
  (ser/deserialize (ser/serialize t))
  (ser/deserialize (ser/serialize #"h.*"))
  )
