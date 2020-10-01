(ns flow-storm-debugger.styles.main)

(def theme
  {:code-font "#eaeaea"
   :background "#292c34"
   :background-contrast "#6f6e7d"
   :background-contrast-2 "#7e8f89"
   :current-expr "#902638"
   :tab-background "#394e68"
   :link-color :pink})
(def ^{:garden {:output-to "resources/public/css/main.css"}}
  main
  (list
   [:html
    [:a {:color (:link-color theme)}]
    [:ul {:list-style-type :none
          :padding 0
          :margin 0}]
    [:button {:cursor :pointer}]
    [:input {:background-color (:background-contrast theme)
             :color (:code-font theme)}]
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

       [:.controls {:display :flex
                    :justify-content :space-between}]

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
        [:.top-bar {:display :flex
                    :justify-content :space-between}
         [:.load-flow {:display :inline-block}]

         [:.flows-tabs {:display :inline-block}
          [:.tab {:min-width "50px"
                  :padding "5px"}]]]

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

          [:.layers {:padding 0
                     :margin 0}
           [:.layer {:cursor :pointer
                     :whitespace :no
                     :white-space :nowrap}]]

          [:.calls
           [:.indent {:padding-left "10px"
                      :border-left "1px solid grey"
                      :width "100%"}
            [:div {:margin-bottom "3px"
                   :white-space :nowrap
                   :cursor :pointer}
             [:.return
              [:.fn-name {:opacity 0.4
                          :margin-left "5px"
                          :font-size "13px"}]]]]]

          [:.result-panel {:width "50%"
                           :display :inline-block
                           :min-width "450px"}
           [:.result-tabs
            [:.tab {:font-size "12px"
                    :padding-left "4px"
                    :padding-right "4px"}]]
           [:.tool {:height "60%"
                    :padding-top "10px"}]

           [:.locals {:padding 0
                      :overflow-x :hidden
                      :overflow-y :scroll
                      :height "33%"}
            [:li {:cursor :pointer
                  :white-space :nowrap
                  ;; :width "1000000px"
                  :padding "3px"
                  :font-size "13px"}
             [:.symbol {:background-color (:background-contrast-2 theme)
                        :margin-right "10px"
                        :padding "0px 3px 0px 3px"
                        :border-radius "3px"}]]
            ["li:nth-child(odd)" {:background-color "#25282f"}]

            ]]]

         [:.modal-overlay {:position :absolute
                                 :z-index 1
                                 :top 0
                                 :left 0
                                 :width "100%"
                                 :height "100%"
                                 :opacity 0.7
                           :background-color (:background theme)}]

         [:.save-flow-panel
          {:position :absolute
           :width "30%"
           :height "5%"
           :top "35%"
           :left "35%"
           :display :flex
           :justify-content :space-around
           :align-items :center
           :background-color (:background theme)
           :z-index 10}]
         [:.local-panel {:position :absolute
                         :width "50%"
                         :height "50%"
                         :top "25%"
                         :left "25%"
                         :background-color (:background theme)
                         :z-index 10
                         }
          [:.symbol {:text-align :center
                     :font-weight :bold
                     :background-color (:background-contrast-2 theme)}]
          [:.value {:height "92%"
                    :padding "10px"
                    :overflow :scroll}]]]]]]]]))
