(ns flow-storm-debugger.ui.subs.general
  (:require [cljfx.api :as fx]))

(defn stats [context]
  (fx/sub-val context :stats))

(defn selected-tool-idx [context]
  (fx/sub-val context :selected-tool-idx))
