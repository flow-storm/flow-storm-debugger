(ns flow-storm.core-multi
  (:require [flow-storm.runtime.values :as rt-values]))


(defn remote-val-pprint-command [{:keys [vid] :as params}]
  (let [v (rt-values/get-reference-value vid)]
    (rt-values/val-pprint v params)))

(defn remote-shallow-val-command [{:keys [vid]}]
  (let [v (rt-values/get-reference-value vid)]
    (rt-values/shallow-val v true)))
