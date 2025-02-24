(ns flow-storm.plugins.timelines-counters.runtime
  (:require [flow-storm.runtime.indexes.api :as ia]
            [flow-storm.runtime.debuggers-api :as dbg-api]))

(defn timelines-counts [flow-id]
  (reduce (fn [acc [fid tid]]
            (if (= flow-id fid)
              (let [timeline (ia/get-timeline flow-id tid)]
                (assoc acc tid (count timeline)))
              acc))
          {}
          (ia/all-threads)))

(dbg-api/register-api-function :plugins.timelines-counters/timelines-counts timelines-counts)
