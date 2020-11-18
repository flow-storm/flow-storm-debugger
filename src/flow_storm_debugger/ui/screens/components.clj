(ns flow-storm-debugger.ui.screens.components
  (:require [cljfx.api :as fx]
            [cljfx.mutator :as fx.mutator]
            [cljfx.lifecycle :as fx.lifecycle]
            [cljfx.prop :as fx.prop]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.utils :as utils]
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
   :text (:val result)})

(defn print-tree-val [v]
  (if-let [tag (:tag (meta v))]
    (str "#" tag (pr-str v))
    (pr-str v)))

(defn ->result-tree-item [x patch-map coor]
  (let [expanded? (boolean (some #(utils/parent-coor? coor %) (keys patch-map)))]
   (if (and (seqable? x) (not (string? x)))
     (cond
       (map-entry? x) {:fx/type :tree-item
                       :expanded expanded?
                       :value {:type :map-entry
                               :expanded expanded?
                               :coor coor
                               :key (key x)
                               :val (val x)}
                       :children [(->result-tree-item (val x) patch-map (conj coor (key x)))]}
       (map? x)       {:fx/type :tree-item
                       :expanded expanded?
                       :value {:type :map
                               :expanded expanded?
                               :coor coor
                               :val x} 
                       :children (map (fn [v]
                                        (->result-tree-item v patch-map coor))
                                      (seq x))}
       :else          {:fx/type :tree-item
                       :expanded expanded?
                       :value {:type :seq
                               :expanded expanded?
                               :coor coor
                               :val x}
                       :children (map-indexed (fn [i v]
                                                (->result-tree-item v patch-map (conj coor i)))
                                              (seq x))})
     {:fx/type :tree-item
      :value {:type :single
              :coor coor
              :val x}})))

(defn result-tree-pane [{:keys [fx/context result]}]
  (let [{:keys [val patch-map removes]} result
        tree-cmp {:fx/type :tree-view
                  :cell-factory {:fx/cell-type :tree-cell
                                 :describe (fn [{:keys [type key val coor]}]
                                             (let [extra-style (when-let [[op] (get patch-map coor)]
                                                                 [(str "tree-edit-update")])]
                                               {:graphic {:fx/type :label
                                                          :style-class (into ["label"] extra-style)
                                                          :text (case type
                                                                  :map       (print-tree-val val)
                                                                  :map-entry (str (pr-str key) " " (print-tree-val val))
                                                                  :seq       (print-tree-val val)
                                                                  :single    (print-tree-val val))} 
                                                :text ""}))}
                  :root (binding [*print-length* 5]
                          (->result-tree-item val patch-map []))}
        removes-cmp {:fx/type :v-box
                     :children [{:fx/type :label
                                 :style-class ["label" "label-ref-patch-rm"]
                                 :text "Removed:"}
                                {:fx/type :list-view
                                 :style-class ["list-view" "removes-list-view"]
                                 :cell-factory {:fx/cell-type :list-cell
                                                :describe (fn [coor]                                
                                                            {:style {:-fx-text-fill :pink}
                                                             :text (str coor)})}
                                 :items (map first removes)}]}]
    (cond-> {:fx/type :split-pane
             :style-class ["split-pane" "vertical-split-pane"]
             :orientation :vertical
             :items (cond-> [tree-cmp]
                      (not-empty removes) (into [removes-cmp]))})))

(defn result-pane [{:keys [fx/context type-subs result-subs toggle-type-event]}]
  (let [result-panel-type (fx/sub-ctx context type-subs)
        result (fx/sub-ctx context result-subs (= result-panel-type :pprint))]
    {:fx/type :border-pane
     :style {:-fx-padding [10 0 0 0]}
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
