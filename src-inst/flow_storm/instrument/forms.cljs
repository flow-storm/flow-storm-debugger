(ns flow-storm.instrument.forms)

(def ^:dynamic *runtime-ctx* nil)

(defn build-runtime-ctx [{:keys [flow-id tracing-disabled?]}]
  {:flow-id flow-id
   :tracing-disabled? tracing-disabled?})
