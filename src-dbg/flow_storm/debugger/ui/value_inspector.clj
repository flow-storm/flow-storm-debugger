(ns flow-storm.debugger.ui.value-inspector
  (:require [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler button label h-box v-box border-pane]]
            [clojure.string :as str]
            [flow-storm.utils :as utils :refer [log-error]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.types :as types]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.value-renderers :as renderers])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.layout VBox HBox Priority]
           [javafx.geometry Orientation]
           [javafx.scene.control SplitPane]))

(declare create-value-pane)

(defn def-val [val]
  (let [val-name (ui-utils/ask-text-dialog
                  {:header "Def var with name. You can use / to provide a namespace, otherwise will be defined under [cljs.]user "
                   :body "Var name :"})]
    (when-not (str/blank? val-name)
      (runtime-api/def-value rt-api (symbol val-name) val))))

(defn- update-vals-panes [{:keys [main-split vals-stack]}]
  (let [[head prev & _] @vals-stack
        value-full-pane (let [def-btn (button :label "def"
                                              :on-click (:def-fn head))
                              tap-btn (button :label "tap"
                                              :on-click (:tap-fn head))
                              val-prev-btn (when-let [fvf (:find-val-fn head)]
                                             (ui-utils/icon-button :icon-name "mdi-ray-end-arrow"
                                                                   :on-click (fn [] (fvf true))
                                                                   :tooltip "Find the prev expression that contains this value"))
                              val-next-btn (when-let [fvf (:find-val-fn head)]
                                             (ui-utils/icon-button :icon-name "mdi-ray-start-arrow"
                                                                   :on-click (fn [] (fvf false))
                                                                   :tooltip "Find the next expression that contains this value"))
                              buttons-box (doto (h-box (cond-> [def-btn tap-btn]
                                                         val-prev-btn (conj val-prev-btn)
                                                         val-next-btn (conj val-next-btn)))
                                            (.setSpacing 5))
                              type-lbl (label (format "Type: %s" (-> head :shallow-val :val/type)))
                              val-header-box (border-pane {:left (v-box (if-let [cnt (-> head :shallow-val :total-count)]
                                                                          [type-lbl (label (format "Count: %d" cnt))]
                                                                          [type-lbl]))
                                                           :right buttons-box}
                                                          "value-inspector-header")
                              meta-box (when-let [mp (:meta-pane head)]
                                         (v-box [(label "Meta")
                                                 mp]))
                              val-pane (border-pane {:top val-header-box
                                                     :center (:val-pane head)})
                              val-full-pane (v-box (if meta-box
                                                     [meta-box val-pane]
                                                     [val-pane]))]
                          (VBox/setVgrow val-pane Priority/ALWAYS)
                          val-full-pane)]
    (HBox/setHgrow value-full-pane Priority/ALWAYS)

    (.clear (.getItems main-split))
    (.addAll (.getItems main-split) (if prev
                                    (let [prev-pane (doto (h-box [(:val-pane prev)])
                                                      (.setId "prev-pane"))]
                                      [prev-pane value-full-pane])
                                    [value-full-pane]))))

(defn- drop-stack-to-frame [{:keys [vals-stack]} val-frame]
  (swap! vals-stack
         (fn [stack]
           (loop [[fr :as stack] stack]
             (if (= fr val-frame)
               stack
               (recur (pop stack)))))))

(defn- update-stack-bar-pane [{:keys [stack-bar-pane vals-stack] :as ctx}]
  (.clear (.getChildren stack-bar-pane))
  (.addAll (.getChildren stack-bar-pane)
           (mapv (fn [{:keys [stack-txt] :as val-frame}]
                   (doto (button :label stack-txt :classes ["stack-bar-btn"])
                     (.setOnAction
                      (event-handler
                       [_]
                       (drop-stack-to-frame ctx val-frame)
                       (update-vals-panes ctx)
                       (update-stack-bar-pane ctx)))))
                 (reverse @vals-stack))))

(defn- make-stack-frame [{:keys [find-and-jump-same-val] :as ctx} stack-txt vref]
  (let [{:keys [val/shallow-meta] :as shallow-val} (runtime-api/shallow-val rt-api vref)]
    {:stack-txt stack-txt
     :val-pane (create-value-pane ctx shallow-val)
     :meta-pane (when shallow-meta (create-value-pane ctx shallow-meta))
     :def-fn (fn [] (def-val vref))
     :tap-fn (fn [] (runtime-api/tap-value rt-api vref))
     :find-val-fn (when find-and-jump-same-val
                    (fn [backward?] (find-and-jump-same-val vref {:comp-fn-key :identity} backward?)))
     :shallow-val shallow-val}))

(defn make-item [stack-key v]
  (let [browsable-val? (types/value-ref? v)
        item {:browsable-val? browsable-val?
              :stack-txt (if (types/value-ref? stack-key)
                           (-> stack-key meta :val-preview)
                           (pr-str stack-key))}]
    (if browsable-val?
      (assoc item
             :val-ref v
             :val-txt (-> v meta :val-preview))

      (assoc item
             :val-ref v
             :val-txt (pr-str v)))))

(defn- create-value-pane [ctx shallow-val]
  (let [on-selected (fn [{:keys [stack-txt val-ref]} prev-pane?]
                      (let [new-frame (make-stack-frame ctx stack-txt val-ref)]
                        (when prev-pane?
                          (swap! (:vals-stack ctx) pop))

                        (swap! (:vals-stack ctx) conj new-frame)
                        (update-vals-panes ctx)
                        (update-stack-bar-pane ctx)))
        renderer-val (case (:val/kind shallow-val)
                       :object (:val/str shallow-val)
                       :map (->> (:val/map-entries shallow-val)
                                 (map (fn [[k v]]
                                        [(make-item "<key>" k) (make-item k v)])))
                       :seq (map-indexed (fn [i v] (make-item i v)) (:val/page shallow-val)))]

    (case (:val/kind shallow-val)
      :object (h-box [(label (:val/str shallow-val))])
      :map (renderers/create-map-browser-pane renderer-val on-selected)
      :seq (let [load-next-page (when (:val/more shallow-val)
                                  (fn load-next [more-ref]
                                    (let [{:keys [page/offset val/page val/more]} (runtime-api/shallow-val rt-api more-ref)
                                          new-page (map-indexed
                                                    (fn [i v]
                                                      (make-item (+ offset i) v))
                                                    page)]
                                      {:page new-page
                                       :load-next (partial load-next more)})))]
             (renderers/create-seq-browser-pane renderer-val
                                                (when load-next-page (partial load-next-page (:val/more shallow-val)))
                                                on-selected)))))

(defn- create-inspector-pane [vref {:keys [find-and-jump-same-val]}]
  (let [*vals-stack (atom nil)
        stack-bar-pane (doto (h-box [] "value-inspector-stack-pane")
                         (.setSpacing 5))
        main-split (doto (SplitPane.)
                   (.setOrientation (Orientation/HORIZONTAL))
                   (.setDividerPosition 0 0.5))
        ctx {:stack-bar-pane stack-bar-pane
             :main-split main-split
             :vals-stack *vals-stack
             :find-and-jump-same-val find-and-jump-same-val}

        mp (border-pane {:top stack-bar-pane
                         :center main-split}
                        "value-inspector-main-pane")]


    (swap! *vals-stack conj (make-stack-frame ctx "/" vref))
    (update-vals-panes ctx)
    (update-stack-bar-pane ctx)

    mp))

(defn create-inspector [vref opts]
  (try
    (let [scene (Scene. (create-inspector-pane vref opts) 1000 600)
          stage (doto (Stage.)
                  (.setTitle "FlowStorm value inspector")
                  (.setScene scene))]

      (dbg-state/register-and-init-stage! stage)

      (-> stage .show))

    (catch Exception e
      (log-error "UI Thread exception" e))))
