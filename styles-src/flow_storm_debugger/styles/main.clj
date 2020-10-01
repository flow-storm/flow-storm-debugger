(ns flow-storm-debugger.styles.main)

(def theme
  {:code-font "#eaeaea"
   ;;:background "#292c34"
   :background "#1e1e1e"
   :background-contrast-1 "#424242"
   :background-contrast "#6f6e7d"
   :background-contrast-2 "#7e8f89"
   :current-expr "#902638"
   :button-background "#4b79b9"
   :link-color :pink})
(def ^{:garden {:output-to "resources/public/css/main.css"}}
  main
  (list
   [:html
    [:a {:color (:link-color theme)}]
    [:pre {:margin 0}]
    [:ul {:list-style-type :none
          :padding 0
          :margin 0}]
    [:button {:cursor :pointer
              :background-color (:button-background theme)
              :color (:code-font theme)
              :border :none
              :margin "2px"}]
    [:input {:background-color (:background-contrast theme)
             :color (:code-font theme)}]
    [:body {:height "98vh"
            :font-family "Consolas, \"Liberation Mono\", Courier, monospace"
            :padding "5px"
            :margin 0
            :background-color (:background theme)
            :color "#fcfcfc"}
     [:#app {:height "100%"}
      [:.main-screen {:height "90%"}

       [:.no-flows {:text-align :center
                    :margin "100px"}
        [:.load {:margin-top "20px"}]]
       [:.scrollable {:overflow :scroll}]
       [:.panel {:padding "10px"
                 :margin "5px"
                 :border "1px solid #aaa"
                 :background-color "#343434"}]

       [:.tab {:display :inline-block
               :cursor :pointer}
        [:&.active {:background-color (:background-contrast-1 theme)
                    }]
        [:.name {:font-size "11px"}]
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
                          :padding "5px"
                          :display :grid
                          :grid-template-rows "6% 46% 46%"
                          :grid-template-columns "50% 50%"
                          :background-color (:background-contrast-1 theme)}

         [:.controls-panel {:display :flex
                            :align-items :center
                            :grid-column-start 1
                            :grid-column-end 3
                            :justify-content :space-between}]
         [:.trace-count {:margin-left "10px"}]
         [:.hl {:background-color (:current-expr theme)
                :border-radius "5px"}]

         [:.code-panel {:grid-column-start 1
                        :grid-column-end 2
                        :grid-row-start 2
                        :grid-row-end 4}
          [:.code {:height "100%"}
           [:.form {:margin-bottom "20px"}]]]

         [:.result-panel {:grid-column-start 2
                          :grid-column-end 3
                          :grid-row-start 2
                          :grid-row-end 3}
          [:.result-tabs
           [:.tab {:font-size "12px"
                   :padding "7px"}]]

          [:.result-tab-content {:height "89%"
                                 :background-color (:background-contrast-1 theme)
                                 :padding-top "10px"}
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
           [:.tool {:height "92%"
                    :margin-left "10px"
                    :margin-right "10px"
                    :margin-bottom "10px"
                    :padding "10px"
                    :background-color "#343434"
                    :overflow :scroll
                    :font-family "monospace"}]]]

         [:.locals {:grid-column-start 2
                    :grid-column-end 3
                    :grid-row-start 3
                    :grid-row-end 4
                    :padding "10px"
                    :overflow-x :hidden
                    :overflow-y :scroll}
          [:li {:cursor :pointer
                :white-space :nowrap
                :padding "3px"
                :font-size "12px"
                }
           [:.symbol {:background-color (:background-contrast-2 theme)
                      :margin-right "10px"
                      :padding "0px 3px 0px 3px"
                      :border-radius "3px"}]]
          ["li:nth-child(odd)" {:background-color "#25282f"}]]

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
                         :font-family "monospace"
                         }
          [:.symbol {:text-align :center
                     :font-weight :bold
                     :background-color (:background-contrast-2 theme)}]
          [:.value {:height "92%"
                    :padding "10px"
                    :overflow :scroll}]]]]]]]]))
