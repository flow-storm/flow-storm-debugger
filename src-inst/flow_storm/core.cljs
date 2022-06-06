(ns flow-storm.core
  (:require [flow-storm.utils :as utils]
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

(defn run-command [comm-id method args-map]
  (try
    (case method
      :instrument-fn        [:cmd-err "[WARNING] :instrument-fn isn't supported yet"]
      :uninstrument-fns     [:cmd-err "[WARNING] :uninstrument-fns isn't supported yet"]
      :eval-forms           [:cmd-err "[WARNING] :eval-forms isn't supported yet"]
      :instrument-forms     [:cmd-err "[WARNING] :instrument-forms isn't supported yet"]
      :re-run-flow          [:cmd-err "[WARNING] :re-run-flow isn't supported yet"]
      :get-remote-value     [:cmd-ret [comm-id (get-remote-value-command args-map)]] )

    (catch js/Error e (js/console.error (utils/format "Error running command %s %s" method args-map) e))))
