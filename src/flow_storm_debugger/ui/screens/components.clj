(ns flow-storm-debugger.ui.screens.components
  (:require [cljfx.api :as fx]
            [cljfx.mutator :as fx.mutator]
            [cljfx.lifecycle :as fx.lifecycle]
            [cljfx.prop :as fx.prop]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.events :as ui.events :refer [event-handler]])
  (:import [org.kordamp.ikonli.javafx FontIcon]
           [javafx.scene.web WebView]))

(defn font-icon [_]
  ;; Check icons here
  ;; https://kordamp.org/ikonli/cheat-sheet-materialdesign.html
  {:fx/type fx/ext-instance-factory :create #(FontIcon.)})

(def ext-with-html
  (fx/make-ext-with-props
    {:html (fx.prop/make
            (fx.mutator/setter (fn [web-view html] (.loadContent (.getEngine ^WebView web-view) html)))
            fx.lifecycle/scalar)
     :css-uri (fx.prop/make
               (fx.mutator/setter (fn [web-view css-uri] (.setUserStyleSheetLocation (.getEngine ^WebView web-view) css-uri)))
               fx.lifecycle/scalar)}))

(defn result-pprint-pane [{:keys [fx/context result]}]
  {:fx/type :text-area
   :editable false
   :style-class ["pane-text-area"]
   :text result})

(defn print-tree-val [v]
  (if-let [tag (:tag (meta v))]
    (str "#" tag (pr-str v))
    (pr-str v)))

(defn ->result-tree-item [x]
  (if (and (seqable? x) (not (string? x)))
    (cond
      (map-entry? x) {:fx/type :tree-item
                      :value (str (pr-str (key x)) " " (print-tree-val (val x)))
                      :children (if (and (seqable? (val x)) (not (string? (val x))))
                                  (map ->result-tree-item (val x))
                                  [(->result-tree-item (val x))]) }
      :else          {:fx/type :tree-item :value (print-tree-val x) :children (map ->result-tree-item (seq x))})
    {:fx/type :tree-item :value (print-tree-val x)}))

(defn result-tree-pane [{:keys [fx/context result]}]
  {:fx/type :tree-view
   :cell-factory {:fx/cell-type :tree-cell
                  :describe (fn [x]
                              {:text (str x)})}
   :root (binding [*print-length* 5]
           (->result-tree-item result))})

(defn result-pane [{:keys [fx/context type-subs result-subs toggle-type-event]}]
  (let [result-panel-type (fx/sub-ctx context type-subs)
        result (fx/sub-ctx context result-subs (= result-panel-type :pprint))]
    {:fx/type :border-pane
     :top {:fx/type :h-box
           :style-class ["bar"]
           :children [{:fx/type :toggle-button
                       :on-selected-changed (fn [pressed?]
                                              (event-handler {:event/type toggle-type-event
                                                              :panel-type (if pressed? :tree :pprint)}))
                       :style-class (cond-> ["button"]
                                      (= result-panel-type :tree)   (into ["pprint-button"])
                                      (= result-panel-type :pprint) (into ["tree-button"]))
                       :graphic {:fx/type font-icon}
                       :text ""}]}
     :center {:fx/type (case result-panel-type
                         :pprint result-pprint-pane
                         :tree result-tree-pane)
              :result result}}))
