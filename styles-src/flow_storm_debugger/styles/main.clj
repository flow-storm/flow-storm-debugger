(ns flow-storm-debugger.styles.main)

(def theme
  {:code-font "#eaeaea"
   :background "#292c34"
   :current-expr "#902638"
   :tab-background "#394e68"
   :link-color :pink})
(def ^{:garden {:output-to "resources/public/css/main.css"}}
  main
  (list
   [:html
    [:a {:color (:link-color theme)}]
    [:button {:cursor :pointer}]
    [:body {:height "100vh"
            :padding 0
            :margin 0
            :background-color (:background theme)
            :color "#fcfcfc"}
     [:#app {:height "100%"}
      [:.main-screen {:height "90%"}

       [:.no-flows {:text-align :center
                    :margin "100px"}]
       [:.scrollable {:overflow :scroll}]
       [:.panel {:padding "10px"
                 :margin "5px"
                 :border "1px solid #aaa"}
        [:&.controls {:overflow :hidden}]]

       [:.tab {:border "1px solid #aaa"
               :display :inline-block
               :cursor :pointer}
        [:&.active {:background-color (:tab-background theme)}]
        [:.name {:font-size "12px"}]
        [:.close {:font-size "10px"
                  :margin-left "4px"
                  :padding-left "4px"
                  :padding-right "4px"
                  :border "1px solid #aaa"
                  :border-radius "5px"}]]

       [:.flows {:height "100%"}

        [:.flows-tabs
         [:.tab {:min-width "50px"
                 :padding "5px"}]]

        [:.selected-flow {:height "100%"
                          :padding "5px"}
         [:.trace-count {:margin-left "10px"}]
         [:.hl {:background-color (:current-expr theme)
                :border-radius "5px"}]
         [:.flow-code-result {:height "95%"
                              :display :flex}

          [:.code-panel {:width "50%"
                         :display :inline-block
                         :min-width "450px"}
           [:.code {:height "100%"}]]

          [:.traces {:width "1000px"}
           [:.trace {:cursor :pointer}]]

          [:.result-panel {:width "50%"
                           :display :inline-block
                           :min-width "450px"}
           [:.result-tabs
            [:.tab {:font-size "12px"
                    :padding-left "4px"
                    :padding-right "4px"}]]
           [:.result {:height "97%"
                      :padding-top "10px"}]]]]]]]]]))
