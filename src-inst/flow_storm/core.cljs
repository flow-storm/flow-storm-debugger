(ns flow-storm.core
  (:require [flow-storm.utils :refer [log] :as utils]
            [flow-storm.tracer :as tracer]
            [flow-storm.instrument.trace-types :as trace-types]
            [clojure.pprint :as pp]))

(defn- get-remote-value-command [{:keys [vid print-length print-level pprint? nth-elem]}]
  (let [value (trace-types/get-reference-value vid)
        print-fn (if pprint? pp/pprint print)]
    (with-out-str
      (binding [*print-level* print-level
                *print-length* print-length]
        (print-fn (cond-> value
                    nth-elem (nth nth-elem)))))))

(defn run-command [method args-map]
  (try
    (let [f (case method
             :instrument-fn        (log "[WARNING] :instrument-fn isn't supported yet")
             :uninstrument-fns     (log "[WARNING] :uninstrument-fns isn't supported yet")
             :eval-forms           (log "[WARNING] :eval-forms isn't supported yet")
             :instrument-forms     (log "[WARNING] :instrument-forms isn't supported yet")
             :re-run-flow          (log "[WARNING] :re-run-flow isn't supported yet")
             :get-remote-value     get-remote-value-command)]
      (f args-map))
    (catch js/Error e (js/console.error (utils/format "Error running command %s %s" method args-map) e))))
