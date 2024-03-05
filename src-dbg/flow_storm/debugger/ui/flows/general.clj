(ns flow-storm.debugger.ui.flows.general
  (:require [flow-storm.debugger.state :as dbg-state :refer [obj-lookup]]
            [flow-storm.debugger.ui.utils :as ui-utils]))

(defn select-thread-tool-tab [flow-id thread-id tool]
  (let [[thread-tools-tab-pane] (obj-lookup flow-id thread-id "thread_tool_tab_pane_id")
        sel-model (.getSelectionModel thread-tools-tab-pane)
        idx (case tool
              :call-tree 0
              :code 1
              :functions 2)]
    (.select sel-model idx)
    (.requestFocus thread-tools-tab-pane)))

(defn select-main-tools-tab [tool]
  (let [[main-tools-tab] (obj-lookup "main-tools-tab")
        sel-model (.getSelectionModel main-tools-tab)]
    (case tool
      :flows (.select sel-model 0)
      :browser (.select sel-model 1)
      :taps (.select sel-model 2)
      :docs (.select sel-model 3))))

(defn show-message [msg msg-type]
  (try
    (ui-utils/run-later
     (let [dialog (ui-utils/alert-dialog {:type msg-type
                                          :message msg
                                          :buttons [:close]
                                          :center-on-stage (dbg-state/main-jfx-stage)})]
       (.show dialog)))
    (catch Exception _)))
