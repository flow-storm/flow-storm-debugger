(ns flow-storm-debugger.ui.subs.general
  (:require [cljfx.api :as fx]))

(defn stats [context]
  (fx/sub-val context :stats))
