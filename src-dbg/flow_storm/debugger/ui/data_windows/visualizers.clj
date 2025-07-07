(ns flow-storm.debugger.ui.data-windows.visualizers
  (:require [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [clojure.string :as str]
            [flow-storm.debugger.ui.data-windows.visualizers.oscilloscope :as scope])
  (:import [javafx.scene.layout Priority HBox VBox]))

(defonce *visualizers (atom {}))

;; *defaults-visualizers should be a list (stack) and not a vector so
;; the latest added take precedence and visualizers can be overwritten by users
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
                          :class "monospaced")})
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
                              :class "monospaced")
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

(register-visualizer
 {:id :html
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :string))
  :on-create (fn [{:keys [string/val]}]
               (let [{:keys [web-view set-html]} (ui/web-view)]
                 (set-html val)
                 {:fx/node web-view
                  :re-render (fn [s] (set-html s))}))
  :on-update (fn [_ {:keys [re-render]} {:keys [string/val]}]
               (re-render val))})

(register-visualizer
 {:id :oscilloscope
  :pred (fn [val] (contains? (:flow-storm.runtime.values/kinds val) :oscilloscope-samples-frames))
  :on-create scope/oscilloscope-create
  :on-update scope/oscilloscope-update
  :on-destroy scope/oscilloscope-destroy})

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default visualizers ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

;; The order here matter since they are added on a stack, so the latests ones
;; have preference.

(add-default-visualizer (fn [val-data] (= "nil" (:flow-storm.runtime.values/type val-data)))                                 :preview)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :paged-shallow-seqable))       :seqable)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :shallow-indexed))             :indexed)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :shallow-map))                 :map)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :number))                      :preview)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :int))                         :int)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :string))                      :preview)
(add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :oscilloscope-samples-frames)) :oscilloscope)



;; Don't make this the default until we can make its render fast
;; (add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :byte-array))            :hex-byte-array)
