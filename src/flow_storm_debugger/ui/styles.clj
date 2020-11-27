(ns flow-storm-debugger.ui.styles
  (:require [cljfx.css :as css]
            [cljfx.api :as fx]
            [clojure.java.io :as io]))

(defonce theme (atom :dark))
(defonce font-size (atom 13))

;; javafx styling reference
;; https://openjfx.io/javadoc/12/javafx.graphics/javafx/scene/doc-files/cssref.html

(def themes
  ;;COLOR                      LIGHT     DARK
  {:background-color          ["#FAFAFA" "#424242"]
   :background-color-2        ["#e5e5e5" "#292929"]
   :background-color-3        ["#F6F6F6" "#4a4a4a"]
   :basic-font-color          ["#39525d" "#eaeaea"]
   :button-back               ["#39525d" "#dcdcdc"]
   :button-font-color         ["#FAFAFA" "#000000"]
   :locals-label-color        ["#c35abc" "#FFC0CB"]
   :return-label-color        ["#FF0000" "#00ffa5"]
   :expression-selected-color ["#FFC8C8" "#902638"]
   :selected-tool-color       ["#ffe02e" "#3b527d"]
   :tree-edit-update          ["#85e68c" "#477e30"]
   :divider-color             ["#e5e5e5" "#aaaaaa"]
   :timeline-color-1          ["#477e30" "#85e68c"]
   :timeline-color-2          ["#c35abc" "#FFC0CB"]
   :timeline-color-3          ["#dd8f00" "#FFA500"]})

(defn th [theme-key]
  (let [selected-theme @theme
        [light dark] (get themes theme-key)]
    (case selected-theme
      :light light
      :dark  dark)))

;; javafx CSS
;; https://gist.github.com/maxd/63691840fc372f22f470
(def style
  (delay
    (css/register
     ::style
     (let [font-family "'Roboto Medium'"
           icon (fn [i] {:-fx-icon-code i
                         :-fx-icon-color (th :button-font-color)
                         :-fx-icon-size 16})]
       {"#tools-tab-pane > .tab-header-area" {:-fx-padding [0 0 0 0]}
        ".root" {:-fx-background-color (th :background-color)
                 " .text" {:-fx-font-family font-family}
                 " .label" {:-fx-text-fill (th :basic-font-color) 
                            :-fx-font-size @font-size}
                 " .button" {:-fx-background-color (th :button-back)
                             :-fx-text-fill (th :button-font-color)
                             :-fx-padding 3}
                 " .vertical-tab" {:-fx-background-color (th :background-color-2)}
                 " .tree-view" {:-fx-background-color :transparent
                                " .tree-cell" {:-fx-background-color :transparent
                                               :-fx-text-fill (th :basic-font-color)}
                                " .arrow" {:-fx-background-color (th :basic-font-color)}
                                " .tree-edit-update" {:-fx-background-color (th :tree-edit-update)}}
                 ;; styling scrollbars
                 ;; https://guigarage.com/2015/11/styling-a-javafx-scrollbar/
                 " .scroll-bar" {" .track-background" {:-fx-background-color (th :background-color-2)}
                                 " .thumb" {:-fx-background-color (th :background-color)}}
                 " .bar" {:-fx-background-color (th :background-color-2)
                          :-fx-padding 5}
                 
                 " .list-view" {:-fx-background-color :transparent
                                " .list-cell" {:-fx-text-fill (th :basic-font-color)}
                                " .list-cell:even" {:-fx-background-color (th :background-color)}
                                " .list-cell:odd" {:-fx-background-color (th :background-color-3)}
                                " .list-cell:filled:selected" {:-fx-background-color (th :expression-selected-color)}
                                }
                 " .no-flows" {" .text" {:-fx-font-size 16}}
                 " .no-refs" {" .text" {:-fx-font-size 16}}
                 " .no-taps" {" .text" {:-fx-font-size 16}}
                 " .no-traces" {" .text" {:-fx-font-size 16}}
                 " .controls-pane" {:-fx-background-color (th :background-color-2)
                                    :-fx-padding 10}
                 " .result-pane" {" .pprint-button" {:-fx-padding 1}
                                  " .tree-button" {:-fx-padding 1}}
                 " .flow-tab-content" {:-fx-padding 10
                                       " .result-pane" {" .bar" {:-fx-padding [4 4 4 4]}}}
                 " .ref-tab-content" {:-fx-padding 10
                                      " .result-pane" {:-fx-padding [10 0 0 0]}}
                 " .tab:selected .focus-indicator" {:-fx-border-color :transparent}
                 ;; https://stackoverflow.com/questions/17091605/how-to-change-the-tab-pane-style
                 " .tab-header-area" {" .tab-header-background" {:-fx-background-color (th :background-color-2)}
                                      :-fx-padding [0 0 0 0]}
                 " .tab-pane" {" .flow-tab" {:-fx-background-color (th :background-color-2)
                                             ":selected" {:-fx-background-color (th :background-color)}}
                               " .ref-tab" {:-fx-background-color (th :background-color-2)
                                            ":selected" {:-fx-background-color (th :background-color)}}
                               " .tap-tab" {:-fx-background-color (th :background-color-2)
                                            ":selected" {:-fx-background-color (th :background-color)}}
                               " .tool-tab" {:-fx-background-color (th :background-color-2)
                                             :-fx-padding [5 20 5 20]
                                             ":selected" {:-fx-background-color (th :selected-tool-color)}}
                               " .panel-tab" {:-fx-background-color (th :background-color-2)
                                              ":selected" {:-fx-background-color (th :background-color)}}
                               " > .tab-header-area" {" > .headers-region" {" > .tab" {:-fx-background-insets [0 1 0 0]}}}}
                 " .stack-pane" {:-fx-box-border :transparent}
                 " .split-pane" {:-fx-border-color (th :background-color-2)
                                 :-fx-background-color :transparent
                                 :-fx-box-border :transparent
                                 " .split-pane-divider" {:-fx-padding 0
                                                         :-fx-border-color (th :divider-color)}}
                 " .vertical-split-pane" {:-fx-border-color :transparent}
                 ;; " .horizontal-split-pane" {:-fx-background-color :transparent}
                 " .pane-text-area" {:-fx-text-fill (th :basic-font-color)
                                     :-fx-background-color (th :background-color)
                                     :-fx-padding 10
                                     " .scroll-pane" {:-fx-background-color :transparent
                                                      " .content" {:-fx-background-color (th :background-color)
                                                                   :-fx-padding 0}}}
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
                                                               {:-fx-icon-color (th :return-label-color)})}               
                 " .strong-text" {:-fx-font-weight :bold}
                 " .locals-view" {" .label" {:-fx-padding [0 10 0 0]}
                                  " .local-name" {:-fx-text-fill (th :locals-label-color)}
                                  }
                 
                 " .layers-view" {}               
                 " .calls-tree" {:-fx-padding [10 0 10 10]
                                 :-fx-border-color (th :timeline-color-2)
                                 :-fx-border-width [0 0 0 1]}
                 " .taps-pane" {:-fx-padding 10}
                 " .timeline-pane" {:-fx-padding 10
                                    " .timeline-list-view" {" .timeline-trace-header" {:-fx-padding [0 5 0 0]}
                                                            " .timeline-trace-flow-header" {:-fx-text-fill (th :timeline-color-1)}
                                                            " .timeline-trace-ref-header"  {:-fx-text-fill (th :timeline-color-2)}
                                                            " .timeline-trace-tap-header"  {:-fx-text-fill (th :timeline-color-3)}}}
                 " .clickable" {:-fx-cursor :hand}}}))))

(defn code-panel-styles []
  (format
   "body{
	   background-color: %s;
	   color: %s;
	   font-size: %dpx;
   }
   .hl {
   	   background-color: %s;
   	   font-weight: bold;
       border-radius: 5px;
   }"
   (th :background-color)
   (th :basic-font-color)
   @font-size
   (th :expression-selected-color)))

(defn build-styles []
  {:app-styles        (:cljfx.css/url @style)
   :font-styles       (str (io/resource "fonts.css"))
   :code-panel-styles (code-panel-styles)})
