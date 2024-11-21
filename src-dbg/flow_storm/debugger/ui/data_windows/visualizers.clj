(ns flow-storm.debugger.ui.data-windows.visualizers
  (:require [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [clojure.string :as str])
  (:import [javafx.scene.canvas Canvas GraphicsContext]
           [javafx.animation AnimationTimer]
           [javafx.scene.paint Color]
           [javafx.scene.text Font]
           [javafx.scene.layout Priority HBox VBox]))

(defonce *visualizers (atom {}))
(defonce *defaults-visualizers (atom ()))

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

(defn add-default-visualizer [pred viz-id]
  (swap! *defaults-visualizers conj {:pred pred :viz-id viz-id}))

(defn default-visualizer [val-data]
  (let [viz-id (->> @*defaults-visualizers
                    (some (fn [{:keys [pred viz-id]}]
                            (when (pred val-data)
                              viz-id))))]
    (visualizer viz-id)))

(defn data-window-current-val [dw-id]
  (dbg-state/data-window-current-val dw-id))

;;;;;;;;;;;;;;;;;
;; Visualizers ;;
;;;;;;;;;;;;;;;;;

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
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :shallow-map))
  :on-create (fn [{:keys [shallow-map/keys-refs shallow-map/vals-refs
                          shallow-map/navs-refs flow-storm.debugger.ui.data-windows.data-windows/dw-id]}]
               (let [keys-previews (mapv (comp :val-preview meta) keys-refs)
                     vals-previews (mapv (comp :val-preview meta) vals-refs)
                     rows (mapv (fn [k-prev k-ref v-prev v-ref nav-ref]
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
                    :columns ["Key" "Val" "Nav"]
                    :cell-factory (fn [_ {:keys [cell-type k-prev k-ref v-prev v-ref nav-ref stack-key]}]
                                    (let [extras {:flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                  :flow-storm.debugger.ui.data-windows.data-windows/stack-key stack-key}]
                                      (case cell-type
                                        :key (ui/label :text k-prev :class "link-lbl" :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id k-ref extras)))
                                        :val (ui/label :text v-prev :class "link-lbl" :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id v-ref extras)))
                                        :nav (when nav-ref (ui/button :label ">" :on-click (fn [] (runtime-api/data-window-push-val-data rt-api dw-id nav-ref extras)))))))
                    :columns-width-percs [0.2 0.7 0.1]
                    :resize-policy :constrained
                    :items rows
                    :search-predicate (fn [{:keys [k-prev v-prev]} search-str]
                                        (or (str/includes? k-prev search-str)
                                            (str/includes? v-prev search-str)))))}))})

(register-visualizer
 {:id :seqable
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :paged-shallow-seqable))
  :on-create (fn [{:keys [flow-storm.debugger.ui.data-windows.data-windows/dw-id] :as seq-page}]
               (let [{:keys [list-view-pane add-all]}
                     (ui/list-view :editable? false
                                   :cell-factory (fn [list-cell {:keys [prev]}]
                                                   (-> list-cell
                                                       (ui-utils/set-text  nil)
                                                       (ui-utils/set-graphic (ui/label :class "link-lbl" :text prev))))
                                   :on-click (fn [_ sel-items _]
                                               (let [{:keys [prev ref]} (first sel-items)
                                                     extras {:flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                             :flow-storm.debugger.ui.data-windows.data-windows/stack-key prev}]
                                                 (runtime-api/data-window-push-val-data rt-api dw-id ref extras))))

                     more-btn (ui/button :label "More")
                     build-and-add-page (fn [{:keys [paged-seq/page-refs paged-seq/next-ref]}]
                                          (let [page-previews (mapv (comp :val-preview meta) page-refs)]
                                            (add-all (mapv (fn [prev ref] {:prev prev :ref ref}) page-previews page-refs))
                                            (if next-ref
                                              (ui-utils/set-button-action more-btn (fn [] (runtime-api/data-window-push-val-data rt-api dw-id next-ref {:update? true})))
                                              (ui-utils/set-disable more-btn true))))]
                 (build-and-add-page seq-page)
                 {:fx/node (ui/v-box
                            :childs [list-view-pane
                                     more-btn])
                  :add-page build-and-add-page}))
  :on-update (fn [_ {:keys [add-page]} seq-page] (add-page seq-page))})

(register-visualizer
 {:id :indexed
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :shallow-indexed))
  :on-create (fn [{:keys [shallow-idx-coll/vals-refs shallow-idx-coll/navs-refs flow-storm.debugger.ui.data-windows.data-windows/dw-id] :as idx-coll}]
               (let [vals-previews (mapv (comp :val-preview meta) vals-refs)
                     rows (mapv (fn [idx v-prev v-ref nav-ref]
                                  [{:cell-type :key :idx idx}
                                   {:cell-type :val :v-prev v-prev :v-ref v-ref :stack-key (str idx)}
                                   {:cell-type :nav :nav-ref nav-ref :stack-key (str idx "-NAV")}])
                                (range)
                                vals-previews
                                vals-refs
                                navs-refs)]
                 {:fx/node
                  (ui/v-box
                   :childs [(ui/label (format "Count: %d" (:shallow-idx-coll/count idx-coll)))
                            (:table-view
                             (ui/table-view
                              :columns ["Idx" "Val" "Nav"]
                              :columns-width-percs [0.2 0.7 0.1]
                              :resize-policy :constrained
                              :cell-factory (fn [_ {:keys [cell-type idx v-prev v-ref nav-ref stack-key]}]
                                              (let [extras {:flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                            :flow-storm.debugger.ui.data-windows.data-windows/stack-key stack-key}]
                                                (case cell-type
                                                  :key (ui/label :text (str idx))
                                                  :val (ui/label :text v-prev :class "link-lbl" :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id v-ref extras)))
                                                  :nav (when nav-ref (ui/button :label ">" :on-click (fn [] (runtime-api/data-window-push-val-data rt-api dw-id nav-ref extras)))))))
                              :search-predicate (fn [{:keys [v-prev]} search-str] (str/includes? v-prev search-str))
                              :items rows))])}))})

(register-visualizer
 {:id :scope
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :number))
  :on-create (fn [{:keys [number/val]}]
               (let [top-bottom-margins 25
                     capture-window-size 1000
                     ^doubles captured-samples (double-array capture-window-size 0)
                     curr-pos (atom 0)
                     canvas-width 1000
                     canvas-height 500
                     canvas (Canvas. canvas-width canvas-height)
                     ^GraphicsContext gc (.getGraphicsContext2D canvas)
                     x-step (long (/ canvas-width capture-window-size))
                     mid-y (/ canvas-height 2)
                     _ (.setFont gc (Font. "Arial" 20))
                     _ (.setFill gc Color/MAGENTA)
                     anim-timer (proxy [AnimationTimer] []
                                  (handle [^long now]
                                    (let [maxs (apply max captured-samples)
                                          mins (apply min captured-samples)
                                          sample-range (* 2 (max (Math/abs maxs) (Math/abs mins)))
                                          scale (/ (- canvas-height (* 2 top-bottom-margins))
                                                   (if (zero? sample-range) 1 sample-range))]
                                      (.clearRect  gc 0 0 canvas-width canvas-height)
                                      (.setStroke  gc Color/MAGENTA)

                                      (.strokeLine  gc 0 mid-y canvas-width mid-y)
                                      (.fillText  gc "0.0" 10 (+ mid-y 22))

                                      (.strokeLine  gc 0 top-bottom-margins canvas-width top-bottom-margins)
                                      (when-not (zero? maxs) (.fillText  gc (str maxs) 10 22))

                                      (.strokeLine  gc 0 (- canvas-height top-bottom-margins) canvas-width (- canvas-height top-bottom-margins))
                                      (when-not (zero? mins) (.fillText  gc (str mins) 10 (- canvas-height 5)))

                                      (.setStroke  gc Color/ORANGE)
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

(register-visualizer
 {:id :int
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :int))
  :on-create (fn [#:int {:keys [decimal binary hex octal]}]
               {:fx/node (ui/v-box
                          :childs
                          [(ui/label :text (format "Decimal: %s" decimal))
                           (ui/label :text (format "Hex: %s" hex))
                           (ui/label :text (format "Binary: %s" binary))
                           (ui/label :text (format "Octal: %s" octal))])})})

(defn- make-bytes-table-pane [data]
  (let [pad-cells-cnt (- 16 (mod (count data) 16))
        right-padded-data (into data (repeat pad-cells-cnt ""))]
    (:table-view-pane
     (ui/table-view
      :columns ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12" "13" "14" "15"]
      :resize-policy :constrained
      :cell-factory (fn [_ idx]
                      (ui/label :text (get right-padded-data idx)))
      :items (->> (partition 16 (range (count right-padded-data)))
                  (mapv #(into [] %)))))))

(register-visualizer
 {:id :hex-byte-array
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :byte-array))
  :on-create (fn [#:bytes {:keys [full? hex head-hex tail-hex]}]
               {:fx/node (if full?
                           (make-bytes-table-pane hex)

                           ;; head and tail
                           (ui/v-box
                            :spacing 10
                            :childs
                            [(ui/label :text "Head")
                             (make-bytes-table-pane head-hex)
                             (ui/label :text "Tail")
                             (make-bytes-table-pane tail-hex)]))})})

(register-visualizer
 {:id :bin-byte-array
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :byte-array))
  :on-create (fn [#:bytes {:keys [full? binary head-binary tail-binary]}]
               {:fx/node (if full?
                           (make-bytes-table-pane binary)

                           ;; head and tail
                           (ui/v-box
                            :spacing 10
                            :childs
                            [(ui/label :text "Head")
                             (make-bytes-table-pane head-binary)
                             (ui/label :text "Tail")
                             (make-bytes-table-pane tail-binary)]))})})

(register-visualizer
 {:id :eql-query-pprint
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :eql-query-pprint))
  :on-create (fn [{:keys [eql/pprint eql/query flow-storm.runtime.values/val-ref flow-storm.debugger.ui.data-windows.data-windows/dw-id]}]
               (let [val-txt (ui/text-area
                              :text pprint
                              :editable? false
                              :class "value-pprint")
                     query-txt (ui/text-field
                                :initial-text (pr-str query)
                                :on-return-key (fn [txt]
                                                 (let [new-query (read-string txt)]
                                                   (runtime-api/data-window-push-val-data rt-api
                                                                                          dw-id
                                                                                          val-ref
                                                                                          {:update? true
                                                                                           :query new-query}))))
                     header-box (ui/h-box :spacing 10
                                          :childs [(ui/label :text "Eql query:") query-txt])
                     main-pane (ui/border-pane
                                :top header-box
                                :center val-txt)]

                 (HBox/setHgrow query-txt Priority/ALWAYS)
                 (HBox/setHgrow header-box Priority/ALWAYS)
                 (VBox/setVgrow main-pane Priority/ALWAYS)

                 {:fx/node main-pane
                  :redraw (fn [val-pprint] (ui-utils/set-text-input-text val-txt val-pprint))}))
  :on-update (fn [_ {:keys [redraw]} {:keys [eql/pprint]}]
               (redraw pprint))})

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default visualizers ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :paged-shallow-seqable)) :seqable)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :shallow-indexed))       :indexed)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :shallow-map))           :map)
;; Don't make this the default until we can make its render fast
;; (add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :byte-array))            :hex-byte-array)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :int))                   :int)
(add-default-visualizer (fn [val-data] (#{"java.lang.String" "#object[String]"} (:flow-storm.runtime.values/type val-data))) :preview)
(add-default-visualizer (fn [val-data] (= "nil" (:flow-storm.runtime.values/type val-data))) :preview)
