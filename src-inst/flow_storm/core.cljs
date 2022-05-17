(ns flow-storm.core
  (:require [flow-storm.utils :refer [log] :as utils]
            [flow-storm.tracer :as tracer]
            [flow-storm.instrument.trace-types :as trace-types]
            [clojure.pprint :as pp]))

(defn instrument-var
  ([var-symb] (instrument-var var-symb {}))
  ([var-symb config]
   ))

(defn uninstrument-var [var-symb]
  )

(defn- instrument-fn-command [{:keys [fn-symb]}]
  )

(defn uninstrument-fns-command [{:keys [vars-symbs]}]
  )

(defn eval-forms-command [{:keys [forms]}]
  )

(defn instrument-forms-command [{:keys [forms config]}]
  )

(defn re-run-flow-command [{:keys [flow-id execution-expr]}]
  )

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
             :instrument-fn        instrument-fn-command
             :uninstrument-fns     uninstrument-fns-command
             :eval-forms           eval-forms-command
             :instrument-forms     instrument-forms-command
             :re-run-flow          re-run-flow-command
             :get-remote-value     get-remote-value-command)]
      (f args-map))
    (catch js/Error e (js/console.error (utils/format "Error running command %s %s" method args-map) e))))
