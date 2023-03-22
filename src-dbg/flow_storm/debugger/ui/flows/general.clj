(ns flow-storm.debugger.ui.flows.general
  (:require [flow-storm.debugger.ui.state-vars :refer [obj-lookup] :as ui-vars]))

(defn select-tool-tab [flow-id thread-id tool]
  (let [[thread-tools-tab-pane] (obj-lookup flow-id (ui-vars/thread-tool-tab-pane-id thread-id))
        sel-model (.getSelectionModel thread-tools-tab-pane)]
    (case tool
      :call-tree (.select sel-model 0)
      :code (.select sel-model 1)
      :functions (.select sel-model 2))))
