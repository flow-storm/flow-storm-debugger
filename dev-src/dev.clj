(ns dev
  (:require [flow-storm-debugger.server :as server]
            [flow-storm-debugger.ui.main-screen :as main-screen]
            [flow-storm-debugger.ui.subs :as ui.subs]
            [flow-storm-debugger.ui.db :as db]
            [cljfx.api :as fx]
            [clojure.pprint :as pp]))


(comment

  (def s (:cljfx.context/m @db/*state))

  (server/-main)
  
  (main-screen/renderer)
  

  (ui.subs/selected-flow-errors @db/*state)
  )

