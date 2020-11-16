(ns dev
  (:require [flow-storm-debugger.server :as server]
            [flow-storm-debugger.ui.screens.main :as screens.main]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.subs.general :as subs.general]
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
  
  (subs.general/selected-tool @db/*state)

  (add-watch #'styles/style :refresh-app
             (fn [_ _ _ _]
               (swap! db/*state fx/swap-context assoc-in [:styles :app-styles] (:cljfx.css/url styles/style))))

  (require '[flow-storm.api :as fsa])
  (fsa/connect)

  (def a (atom 0))

  (fsa/trace-ref "super-ref" a)

  (swap! a inc)

  (def b (atom {:ages [1 2 3 30]}))

  (swap! b assoc :new-field 42)

  (fsa/trace-ref "super-ref-map" b)


  ;; #trace (let [a {:a 10 :b [1 2 3]}
  ;;              b 100]
           ;; (+ (:a a) b))

  ;; #trace (+ 1 (:a {:a 1}))
  )

