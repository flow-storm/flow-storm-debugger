(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]))

(defn start-debugger []
  (dbg-state/init-state!)
  (ui-vars/reset-state!)
  (ui-main/start-ui))
