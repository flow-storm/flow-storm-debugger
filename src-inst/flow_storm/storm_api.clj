(ns flow-storm.storm-api
  (:require [flow-storm.api :as fs-api]
            [flow-storm.tracer :as tracer]
            [flow-storm.runtime.debuggers-api :as debuggers-api]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [flow-storm.runtime.indexes.frame-index :as frame-index]))

(defn start-recorder []
  (indexes-api/start))

(defn start-debugger []
  (let [config {:skip-index-start? true}]
    (fs-api/local-connect config)))

(def jump-to-last-exception debuggers-api/jump-to-last-exception)

(def jump-to-last-expression debuggers-api/jump-to-last-expression-in-this-thread)

(defn get-fn-expr-limit []
  frame-index/fn-expr-limit)

(defn get-tracing-enable []
  (tracer/recording?))

(defn set-tracing-enable [x]
  (tracer/set-recording x))
