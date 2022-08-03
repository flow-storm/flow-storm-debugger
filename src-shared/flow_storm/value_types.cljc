(ns flow-storm.value-types)

(defrecord LocalImmValue [val])

(defn local-imm-value? [x]
  (instance? LocalImmValue x))

(defrecord RemoteImmValue [vid])

(defn remote-imm-value? [x]
  (instance? RemoteImmValue x))
