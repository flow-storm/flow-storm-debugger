(ns dev
  (:require [flow-storm-debugger.server :as server]
            [flow-storm-debugger.ui.screens.main :as screens.main]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.db :as db]
            [cljfx.api :as fx]
            [flow-storm-debugger.ui.styles :as styles]
            [clojure.pprint :as pp]))


(comment

  db/*state
  (def s (:cljfx.context/m @db/*state))

  (server/-main)
  
  (screens.main/renderer)
  

  (subs.flows/selected-flow-errors @db/*state)

  (add-watch #'styles/style :refresh-app
             (fn [_ _ _ _]
               (swap! db/*state fx/swap-context assoc-in [:styles :app-styles] (:cljfx.css/url styles/style))))
  )

