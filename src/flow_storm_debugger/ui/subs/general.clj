(ns flow-storm-debugger.ui.subs.general
  (:require [cljfx.api :as fx]
            [taoensso.timbre :as log]))

(defn stats [context]
  (log/debug "[SUB] stats firing")
  (fx/sub-val context :stats))

(defn selected-tool-idx [context]
  (log/debug "[SUB] selected-tool-idx firing")
  (fx/sub-val context :selected-tool-idx))
