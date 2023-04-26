(ns flow-storm.debugger.ui.flows.general
  (:require [flow-storm.debugger.ui.state-vars :refer [obj-lookup] :as ui-vars]))

(defn select-thread-tool-tab [flow-id thread-id tool]
  (let [[thread-tools-tab-pane] (obj-lookup flow-id thread-id "thread_tool_tab_pane_id")
        sel-model (.getSelectionModel thread-tools-tab-pane)
        idx (case tool
              :call-tree 0
              :code 1
              :functions 2)]
    (.select sel-model idx)
    (.requestFocus thread-tools-tab-pane)))
