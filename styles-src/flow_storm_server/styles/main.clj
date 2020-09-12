(ns flow-storm-server.styles.main)

(def ^{:garden {:output-to "resources/public/css/main.css"}}
  main
  (list
   [:html
    [:body {:height "100vh"
            :padding 0
            :margin 0}
     [:#app {:height "100%"}
      [:.main-screen {:height "90%"}

       [:.panel {:padding "10px"
                 :overflow :scroll
                 :border "1px solid #aaa"}]

       [:.tab {:padding "5px"
               :border "1px solid #aaa"
               :display :inline-block}
        [:&.active {:background-color "#bbb"}]]

       [:.flows {:height "100%"}
        [:.selected-flow {:height "100%"}
         [:.flow-code-result {:height "75%"
                              :display :flex}

          [:.code {:width "50%"
                   :display :inline-block
                   :min-width "450px"
                   :height "90%"}
           [:.hl {:background-color :pink}]]

          [:.result {:width "50%"
                     :display :inline-block
                     :min-width "450px"
                     :height "90%"}]]]]]]]]))
