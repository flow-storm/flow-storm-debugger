(ns flow-storm-debugger.styles.main)

(def ^{:garden {:output-to "resources/public/css/main.css"}}
  main
  (list
   [:html
    [:body {:height "100vh"
            :padding 0
            :margin 0}
     [:#app {:height "100%"}
      [:.main-screen {:height "90%"}

       [:.no-flows {:text-align :center
                    :margin "100px"}]

       [:.panel {:padding "10px"
                 :overflow :scroll
                 :border "1px solid #aaa"}
        [:&.controls {:overflow :hidden}]]

       [:.tab {:padding "5px"
               :border "1px solid #aaa"
               :display :inline-block
               :text-align :right
               :min-width "50px"
               :border-radius "0px 5px 0px 0px"}
        [:&.active {:background-color "#bbb"}]
        [:.close {:font-size "10px"
                  :margin-left "4px"
                  :padding-left "4px"
                  :padding-right "4px"
                  :border "1px solid #aaa"
                  :border-radius "5px"}]]

       [:.flows {:height "100%"}
        [:.selected-flow {:height "100%"
                          :padding "5px"}
         [:.trace-count {:margin-left "10px"}]
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
