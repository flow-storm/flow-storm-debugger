(ns flow-storm.debugger.user-guide
  (:require [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.utils :as ui-utils]))

(defn show-user-guide []
  (let [window-w 1200
        window-h 800
        {:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) window-w window-h)
        {:keys [web-view load-url]} (ui/web-view)]
    (load-url "https://flow-storm.github.io/flow-storm-debugger/user_guide.html")
    (ui/stage :scene (ui/scene :root web-view
                               :window-width  window-w
                               :window-height window-h)
              :title "FlowStorm basics tutorial"
              :x x
              :y y
              :show? true)))
