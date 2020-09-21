(ns flow-storm-debugger.ui.db)

(defn initial-db []
  #_{:flows {1 {:forms {
                      "f1" '(let [a 1] (+ a 1))
                      "f2" '(+ 1 2 3)
                      }
                :traces [{}]
                :bind-traces [{}]
                :trace-idx 0
                :local-panel nil
                :save-flow-panel-open? false}}}

  {:flows {}
   :selected-flow-id nil
   :selected-result-panel :pprint ;; :explorer :time

   })
