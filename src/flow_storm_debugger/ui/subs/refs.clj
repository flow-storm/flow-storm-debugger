(ns flow-storm-debugger.ui.subs.refs
  (:require [cljfx.api :as fx]))

(defn empty-refs? [context]
  (empty? (fx/sub-val context :refs)))
