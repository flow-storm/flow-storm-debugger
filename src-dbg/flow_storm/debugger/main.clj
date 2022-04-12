(ns flow-storm.debugger.main
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]))

(def orphan-flow-id -1)

(defn start-debugger []
  (dbg-state/init-state!)
  (ui-vars/reset-state!)
  (ui-main/start-ui)

  (dbg-state/create-flow dbg-state/dbg-state orphan-flow-id nil nil 0)
  (ui-utils/run-now (flows-screen/create-empty-flow orphan-flow-id)))
