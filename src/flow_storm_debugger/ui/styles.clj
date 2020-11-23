(ns flow-storm-debugger.ui.styles
  (:require [cljfx.css :as css]
            [cljfx.api :as fx]))

;; javafx styling reference
;; https://openjfx.io/javadoc/12/javafx.graphics/javafx/scene/doc-files/cssref.html

;; javafx CSS
;; https://gist.github.com/maxd/63691840fc372f22f470
(def style 
  (css/register
   ::style
   (let [background-color-2 "#292929"
         background-color "#424242"
         basic-font-color "#eaeaea"
         button-back "#dcdcdc" #_"#4b79b9"
         button-font-color :black
         locals-label-color :pink
         return-label-color "#00ffa5"
         expression-selected-color "#902638"
         font-family "'Roboto Medium'"
         icon (fn [i] {:-fx-icon-code i
                       :-fx-icon-color button-font-color
                       :-fx-icon-size 16})]
     {"#tools-tab-pane > .tab-header-area" {:-fx-padding [0 0 0 0]}
      ".root" {:-fx-background-color background-color
               " .text" {:-fx-font-family font-family}
               " .label" {:-fx-text-fill basic-font-color 
                          :-fx-font-size 13}
               " .button" {:-fx-background-color button-back
                           :-fx-text-fill button-font-color
                           :-fx-padding 3}
               " .vertical-tab" {:-fx-background-color background-color-2}
               " .tree-view" {:-fx-background-color :transparent
                              " .tree-cell" {:-fx-background-color :transparent
                                             :-fx-text-fill basic-font-color}
                              " .arrow" {:-fx-background-color basic-font-color}
                              " .tree-edit-update" {:-fx-background-color :green}}
               ;; styling scrollbars
               ;; https://guigarage.com/2015/11/styling-a-javafx-scrollbar/
               " .scroll-bar" {" .track-background" {:-fx-background-color background-color-2}
                               " .thumb" {:-fx-background-color background-color}}
               " .bar" {:-fx-background-color background-color-2
                        :-fx-padding 5}
               
               " .list-view" {:-fx-background-color :transparent
                              " .list-cell" {:-fx-text-fill basic-font-color}
                              " .list-cell:even" {:-fx-background-color background-color}
                              " .list-cell:odd" {:-fx-background-color "#4a4a4a"}}
               " .no-flows" {" .text" {:-fx-font-size 16}}
               " .no-refs" {" .text" {:-fx-font-size 16}}
               " .no-taps" {" .text" {:-fx-font-size 16}}
               " .controls-pane" {:-fx-background-color background-color-2
                                  :-fx-padding 10}
               " .flow-tab-content" {:-fx-padding 10}
               " .ref-tab-content" {:-fx-padding 10}
               " .tab:selected .focus-indicator" {:-fx-border-color :transparent}
               ;; https://stackoverflow.com/questions/17091605/how-to-change-the-tab-pane-style
               " .tab-header-area" {" .tab-header-background" {:-fx-background-color background-color-2}
                                    :-fx-padding [0 0 0 0]}
               " .tab-pane" {" .flow-tab" {:-fx-background-color background-color-2
                                           ":selected" {:-fx-background-color background-color}}
                             " .ref-tab" {:-fx-background-color background-color-2
                                          ":selected" {:-fx-background-color background-color}}
                             " .tap-tab" {:-fx-background-color background-color-2
                                          ":selected" {:-fx-background-color background-color}}
                             " .tool-tab" {:-fx-background-color background-color-2
                                           :-fx-padding [5 20 5 20]
                                           ":selected" {:-fx-background-color "#3b527d"}}
                             " .panel-tab" {:-fx-background-color background-color-2
                                            ":selected" {:-fx-background-color background-color}}
                             " > .tab-header-area" {" > .headers-region" {" > .tab" {:-fx-background-insets [0 1 0 0]}}}}
               " .stack-pane" {:-fx-box-border :transparent}
               " .split-pane" {:-fx-border-color background-color-2
                               :-fx-background-color :transparent
                               :-fx-box-border :transparent
                               " .split-pane-divider" {:-fx-padding 0
                                                       :-fx-border-color "#aaa"}}
               " .vertical-split-pane" {:-fx-border-color :transparent}
               ;; " .horizontal-split-pane" {:-fx-background-color :transparent}
               " .pane-text-area" {:-fx-text-fill basic-font-color
                                   :-fx-background-color background-color
                                   :-fx-padding 10
                                   " .scroll-pane" {:-fx-background-color :transparent
                                                    " .content" {:-fx-background-color background-color
                                                                 :-fx-padding 0}}}
               " .web-view" {:-fx-background-color :blue}
               " .load-button" {" .ikonli-font-icon" (icon "mdi-folder-plus")}
               " .save-button" {" .ikonli-font-icon" (icon "mdi-content-save")}
               " .tree-button" {:-fx-padding 3
                                " .ikonli-font-icon" (-> (icon "mdi-file-tree")
                                                         (assoc :-fx-icon-size 15))}
               " .pprint-button" {:-fx-padding 3
                                  " .ikonli-font-icon" (icon "mdi-file-powerpoint-box")}
               " .reset-button" {" .ikonli-font-icon" (icon "mdi-reload")}
               " .first-button" {" .ikonli-font-icon" (icon "mdi-page-first")}
               " .prev-button" {" .ikonli-font-icon" (icon "mdi-chevron-left")}
               " .next-button" {" .ikonli-font-icon" (icon "mdi-chevron-right")}
               " .last-button" {" .ikonli-font-icon" (icon "mdi-page-last")}
               " .squash-button" {" .ikonli-font-icon" (icon "mdi-arrow-compress")}
               " .result-label" {" .ikonli-font-icon" (merge (icon "mdi-arrow-right-bold")
                                                             {:-fx-icon-color return-label-color})}               
               " .strong-text" {:-fx-font-weight :bold}
               " .locals-view" {" .label" {:-fx-padding [0 10 0 0]}
                                " .local-name" {:-fx-text-fill locals-label-color}
                                ;;" .local-val" {" .text" {:-fx-font-family "'Roboto Light'"}}
                                
                                }
               
               " .layers-view" {}               
               " .calls-tree" {:-fx-padding [10 0 10 10]
                               :-fx-border-color :pink
                               :-fx-border-width [0 0 0 1]}
               " .taps-pane" {:-fx-padding 10}
               " .timeline-pane" {:-fx-padding 10
                                  " .timeline-list-view" {" .timeline-trace-header" {:-fx-padding [0 5 0 0]}
                                                          " .timeline-trace-flow-header" {:-fx-text-fill "#85e68c"}
                                                          " .timeline-trace-ref-header" {:-fx-text-fill :pink}
                                                          " .timeline-trace-tap-header" {:-fx-text-fill :orange}}}
               " .clickable" {:-fx-cursor :hand}}})))
