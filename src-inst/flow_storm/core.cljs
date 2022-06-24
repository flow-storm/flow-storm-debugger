(ns flow-storm.core
  (:require [flow-storm.utils :as utils]
            [flow-storm.instrument.trace-types :as trace-types]
            [flow-storm.core-multi :refer [get-remote-value-command]]
            [goog.object :as gobj]))

(defn- def-remote-value-command [{:keys [vid val-name]}]
  (gobj/set (if (= *target* "nodejs") js/global js/window)
            val-name
            (trace-types/get-reference-value vid)))

(defn run-command [comm-id method args-map]
  (try
    (case method
      :instrument-fn        [:cmd-err "[WARNING] :instrument-fn isn't supported in ClojureScript yet"]
      :uninstrument-fns     [:cmd-err "[WARNING] :uninstrument-fns isn't supported in ClojureScript yet"]
      :eval-forms           [:cmd-err "[WARNING] :eval-forms isn't supported in ClojureScript yet"]
      :instrument-forms     [:cmd-err "[WARNING] :instrument-forms isn't supported in ClojureScript yet"]
      :re-run-flow          [:cmd-err "[WARNING] :re-run-flow isn't supported in ClojureScript yet"]
      :def-remote-value     [:cmd-ret [comm-id (def-remote-value-command args-map)]]
      :get-remote-value     [:cmd-ret [comm-id (get-remote-value-command args-map)]]
      :get-all-namespaces   [:cmd-err "[WARNING] :get-all-namespaces isn't supported in ClojureScript yet"])

    (catch js/Error e (js/console.error (utils/format "Error running command %s %s" method args-map) e))))
