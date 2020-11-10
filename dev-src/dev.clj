(ns dev
  (:require [flow-storm-debugger.server :as server]
            [flow-storm-debugger.ui.main-screen :as main-screen]
            [flow-storm-debugger.ui.subs :as ui.subs]
            [flow-storm-debugger.ui.db :as db]
            [cljfx.api :as fx]
            [flow-storm-debugger.ui.styles :as styles]
            [clojure.pprint :as pp]))


(comment

  db/*state
  (def s (:cljfx.context/m @db/*state))

  (server/-main)
  
  (main-screen/renderer)
  

  (ui.subs/selected-flow-errors @db/*state)

  (add-watch #'styles/style :refresh-app
             (fn [_ _ _ _]
               (swap! db/*state fx/swap-context assoc-in [:styles :app-styles] (:cljfx.css/url styles/style))))
  )

