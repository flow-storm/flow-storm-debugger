(ns flow-storm-debugger.ui.screens.timeline
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.subs.timeline :as subs.timeline]
            [flow-storm-debugger.ui.events :as ui.events]
            [flow-storm-debugger.ui.screens.components :as components]))

(defn flow-fn-call [{:keys [fx/context trace]}]
  {:fx/type :h-box
   :style-class ["h-box" "clickable"]
   :on-mouse-clicked {:event/type ::ui.events/focus-thing
                      :thing {:thing/type :flow
                              :flow-id (:flow-id trace)
                              :trace-idx (:trace-idx trace)}}
   :children [{:fx/type :label
               :style-class ["label" "timeline-trace-header" "timeline-trace-flow-header"]
               :text (format "[%s] fn" (:flow-id trace))}
              {:fx/type :label :text "("}
              {:fx/type :label :style {:-fx-font-weight :bold}
               :text (str (:fn-name trace) " ")}
              {:fx/type :label :text (str (:args-vec trace))}
              {:fx/type :label :text ")"}]})

(defn flow-group [{:keys [fx/context trace]}]
  {:fx/type :h-box
   :on-mouse-clicked {:event/type ::ui.events/focus-thing
                      :thing {:thing/type :flow
                              :flow-id (:flow-id trace)
                              :trace-idx (:trace-idx trace)}}
   :style-class ["h-box" "clickable"]
   :children [{:fx/type :label
               :style-class ["label" "timeline-trace-header" "timeline-trace-flow-header"]
               :text (format "[%s]" (:flow-name trace))}
              {:fx/type :label :text (format "... [%d]" (:trace-group-count trace))}]})

(defn ref-trace [{:keys [fx/context trace]}]
  {:fx/type :h-box
   :style-class ["h-box" "clickable"]
   :on-mouse-clicked {:event/type ::ui.events/focus-thing
                      :thing {:thing/type :ref
                              :ref-id (:ref-id trace)
                              :patch-idx (:patch-idx trace)}}
   :children [{:fx/type :label
               :style-class ["label" "timeline-trace-header" "timeline-trace-ref-header"]
               :text (str (format "[%s] " (:ref-name trace))
                          (if (:init-val trace) "ref" "ref>>"))}
              {:fx/type :label
               :text (str (or (:init-val trace)
                              (:patch trace)))}]})

(defn tap-trace [{:keys [fx/context trace]}]
  {:fx/type :h-box
   :style-class ["h-box" "clickable"]   
   :on-mouse-clicked {:event/type ::ui.events/focus-thing
                      :thing {:thing/type :tap
                              :tap-id (:tap-id trace)
                              :tap-trace-idx (:tap-trace-idx trace)}}
   :children [{:fx/type :label
               :style-class ["label" "timeline-trace-header" "timeline-trace-tap-header"]
               :text (format "[%s] tap>" (or (:tap-name trace)
                                             (:tap-id trace)))}
              {:fx/type :label               
               :text (str (:value trace))}]})

(defn timeline [{:keys [fx/context]}]
  (let [traces (fx/sub-ctx context subs.timeline/timeline)]
    {:fx/type :border-pane
     :style-class ["border-pane" "timeline-pane"]
     :center {:fx/type :list-view
              :style-class ["list-view" "timeline-list-view"]
              :cell-factory {:fx/cell-type :list-cell
                             :describe (fn [trace]                                
                                         {:text ""
                                          :graphic (case (:trace/type trace)
                                                     :flow-fn-call {:fx/type flow-fn-call :trace trace}
                                                     :flow-group   {:fx/type flow-group   :trace trace}
                                                     :ref          {:fx/type ref-trace    :trace trace}                                                     
                                                     :tap          {:fx/type tap-trace    :trace trace})})}
              :items traces}}))
