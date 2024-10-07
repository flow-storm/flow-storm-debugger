(ns flow-storm.debugger.ui.data-windows.visualizers
  (:require [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.utils :as ui-utils])
  (:import [javafx.scene.canvas Canvas GraphicsContext]
           [javafx.animation AnimationTimer]
           [javafx.scene.paint Color]
           [javafx.scene.text Font]))

(defonce *visualizers (atom {}))
(defonce *types->defaults (atom {}))

(defn register-visualizer [{:keys [id] :as viz}]
  (swap! *visualizers assoc id viz))

(defn visualizers []
  @*visualizers)

(defn visualizer [viz-id]
  (get @*visualizers viz-id))

(defn appliable-visualizers [val-data]
  (->> (vals (visualizers))
       (filter (fn [{:keys [pred]}] (pred val-data)))))

(defn make-visualizer-for-val [viz-id val]
  (let [{:keys [on-create]} (visualizer viz-id)
        viz-ctx (on-create val)]
    viz-ctx))

(defn set-default-visualizer [val-type-symb viz-id]
  (swap! *types->defaults assoc val-type-symb viz-id))

(defn default-visualizer [val-type-symb]
  (visualizer (get @*types->defaults val-type-symb)))

(register-visualizer
 {:id :preview
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :previewable))
  :on-create (fn [{:keys [preview/pprint]}]
               {:fx/node (ui/text-area
                          :text pprint
                          :editable? false
                          :class "value-pprint")})
  :on-update (fn [_ {:keys [fx/node]} {:keys [new-val]}]
               (ui-utils/set-text-input-text node (str new-val)))})

(register-visualizer
 {:id :map
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :map))
  :on-create (fn [{:keys [map/keys-previews map/keys-refs map/vals-previews map/vals-refs map/navs-refs flow-storm.debugger.ui.data-windows.data-windows/dw-id]}]
               (let [rows (mapv (fn [k-prev k-ref v-prev v-ref nav-ref]
                                  [{:cell-type :key :k-prev k-prev :k-ref k-ref :stack-key (str k-prev "-KEY")}
                                   {:cell-type :val :v-prev v-prev :v-ref v-ref :stack-key k-prev}
                                   {:cell-type :nav :nav-ref nav-ref :stack-key (str k-prev "-NAV")}])
                           keys-previews
                           keys-refs
                           vals-previews
                           vals-refs
                           navs-refs)]
                 {:fx/node
                  (:table-view
                   (ui/table-view
                    :columns ["Key" "Val" "_"]
                    :cell-factory (fn [_ {:keys [cell-type k-prev k-ref v-prev v-ref nav-ref stack-key]}]
                                    (let [extras {:flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                  :flow-storm.debugger.ui.data-windows.data-windows/stack-key stack-key}]
                                      (case cell-type
                                        :key (ui/label :text k-prev :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id k-ref extras)))
                                        :val (ui/label :text v-prev :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id v-ref extras)))
                                        :nav (when nav-ref (ui/button :label ">" :on-click (fn [] (runtime-api/data-window-push-val-data rt-api dw-id nav-ref extras)))))))
                    :items rows))}))})

(register-visualizer
 {:id :seqable
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :seqable))
  :on-create (fn [{:keys [flow-storm.debugger.ui.data-windows.data-windows/dw-id] :as seq-page}]
               (let [{:keys [list-view-pane add-all]}
                     (ui/list-view :editable? false
                                   :cell-factory (fn [list-cell {:keys [prev]}]
                                                   (-> list-cell
                                                       (ui-utils/set-text  nil)
                                                       (ui-utils/set-graphic (ui/label :text prev))))
                                   :on-click (fn [_ sel-items _]
                                               (let [{:keys [prev ref]} (first sel-items)
                                                     extras {:flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                             :flow-storm.debugger.ui.data-windows.data-windows/stack-key prev}]
                                                 (runtime-api/data-window-push-val-data rt-api dw-id ref extras))))

                     more-btn (ui/button :label "More")
                     build-and-add-page (fn [{:keys [seq/page-previews seq/page-refs seq/next-ref]}]
                                          (add-all (mapv (fn [prev ref] {:prev prev :ref ref}) page-previews page-refs))
                                          (if next-ref
                                            (ui-utils/set-button-action more-btn (fn [] (runtime-api/data-window-push-val-data rt-api dw-id next-ref {:update? true})))
                                            (ui-utils/set-disable more-btn true)))]
                 (build-and-add-page seq-page)
                 {:fx/node (ui/v-box
                            :childs [list-view-pane
                                     more-btn])
                  :add-page build-and-add-page}))
  :on-update (fn [_ {:keys [add-page]} seq-page] (add-page seq-page))})

(register-visualizer
 {:id :indexed
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :indexed))
  :on-create (fn [{:keys [idx-coll/vals-previews idx-coll/vals-refs idx-coll/navs-refs flow-storm.debugger.ui.data-windows.data-windows/dw-id] :as idx-coll}]
               (let [rows (mapv (fn [idx v-prev v-ref nav-ref]
                                  [{:cell-type :key :idx idx}
                                   {:cell-type :val :v-prev v-prev :v-ref v-ref :stack-key (str idx)}
                                   {:cell-type :nav :nav-ref nav-ref :stack-key (str idx "-NAV")}])
                                (range)
                                vals-previews
                                vals-refs
                                navs-refs)]
                 {:fx/node
                  (ui/v-box
                   :childs [(ui/label (format "Count: %d" (:idx-coll/count idx-coll)))
                            (:table-view
                             (ui/table-view
                              :columns ["Key" "Val" "_"]
                              :cell-factory (fn [_ {:keys [cell-type idx v-prev v-ref nav-ref stack-key]}]
                                              (let [extras {:flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                            :flow-storm.debugger.ui.data-windows.data-windows/stack-key stack-key}]
                                                (case cell-type
                                                  :key (ui/label :text (str idx))
                                                  :val (ui/label :text v-prev :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id v-ref extras)))
                                                  :nav (when nav-ref (ui/button :label ">" :on-click (fn [] (runtime-api/data-window-push-val-data rt-api dw-id nav-ref extras)))))))
                              :items rows))])}))})

(set! *warn-on-reflection* true)
(register-visualizer
 {:id :scope
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :number))
  :on-create (fn [{:keys [number/val]}]
               (let [capture-window-size 1000
                     ^doubles captured-samples (double-array capture-window-size 0)
                     curr-pos (atom 0)
                     canvas-width 1000
                     canvas-height 500
                     canvas (Canvas. canvas-width canvas-height)
                     ^GraphicsContext gc (.getGraphicsContext2D canvas)
                     x-step (long (/ canvas-width capture-window-size))
                     mid-y (/ canvas-height 2)
                     _ (.setFont gc (Font. "Arial" 20))
                     _ (.setFill gc Color/YELLOW)
                     anim-timer (proxy [AnimationTimer] []
                                  (handle [^long now]
                                    (let [maxs (apply max captured-samples)
                                          mins (apply min captured-samples)
                                          sample-range (- maxs mins)
                                          scale (/ canvas-height (if (zero? sample-range) 1 sample-range))]
                                      (.clearRect  gc 0 0 canvas-width canvas-height)
                                      (.setStroke  gc Color/GREEN)

                                      (.strokeLine  gc 0 mid-y canvas-width mid-y)
                                      (.fillText  gc "0.0" 10 (+ mid-y 22))

                                      (.strokeLine  gc 0 0 canvas-width 0)
                                      (when-not (zero? maxs) (.fillText  gc (str maxs) 10 22))

                                      (.strokeLine  gc 0 (dec canvas-height) canvas-width (dec canvas-height))
                                      (when-not (zero? mins) (.fillText  gc (str mins) 10 (- canvas-height 22)))

                                      (.setStroke  gc Color/YELLOW)
                                      (loop [i 0
                                             x 0]
                                        (when (< i (dec capture-window-size))
                                          (let [s-i      (aget captured-samples i)
                                                s-i-next (aget captured-samples (inc i))
                                                y1 (- mid-y (* scale s-i))
                                                y2 (- mid-y (* scale s-i-next))]
                                            (.strokeLine ^GraphicsContext gc x y1 (+ x x-step) y2)
                                            (recur (inc i) (+ x x-step))))))))
                     add-sample (fn add-sample [^double s]
                                  (aset-double captured-samples @curr-pos s)
                                  (swap! curr-pos (fn [cp]
                                                    (if (< cp (dec capture-window-size))
                                                      (inc cp)
                                                      0))))]
                 (add-sample val)
                 (.start anim-timer)
                 {:fx/node canvas
                  :add-sample add-sample
                  :stop-timer (fn [] (.stop anim-timer))}))
  :on-update (fn [_ {:keys [add-sample]} {:keys [new-val]}]
               (add-sample new-val))
  :on-destroy (fn [{:keys [stop-timer]}] (stop-timer))})


(set-default-visualizer "clojure.lang.PersistentArrayMap" :map)
(set-default-visualizer "clojure.lang.PersistentVector" :indexed)
