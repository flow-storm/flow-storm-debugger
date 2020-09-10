(ns flow-storm-server.styles.main)

(def ^{:garden {:output-to "resources/public/css/main.css"}}
  main
  (list
   [:html
    [:body {:height "100vh"
            :padding 0
            :margin 0}
     [:#app {:height "100%"}
      [:.screen {:height "90%"}

       [:.panel {;;:margin "5px"
                 :padding "10px"
                 :overflow :scroll
                 :border "1px solid #aaa"}]
       [:.code-result-cont {:height "75%"
                            :display :flex}

        [:.code {:width "50%"
                 :display :inline-block
                 :min-width "450px"
                 :height "90%"}
         [:.hl {:background-color :pink}]]

        [:.result {:width "50%"
                   :display :inline-block
                   :min-width "450px"
                   :height "90%"}]]
       ]]]]))
