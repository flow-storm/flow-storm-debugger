(ns flow-storm.debugger.ui.decompiler.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.flows.general :as ui-general :refer [show-message build-form-paint-and-arm-fn]]
            [clojure.string :as str])
  (:import [javafx.scene.control ScrollPane]
           [javafx.scene.layout VBox Priority HBox]))

(defn- update-code-panes [form-id coord-or-bytecode-idx]
  (let [{:keys [form-coord highlighted-decomp-lines]} (dbg-state/decompiler-form-highlighted-decomp-lines form-id coord-or-bytecode-idx)
        [decompiled-form-repaint-fn] (obj-lookup (str "decompiled-form-repaint-fn-" form-id))
        [{:keys [clear add-all]}] (obj-lookup "decompiled-list-view-data")]
    (clear)
    (add-all highlighted-decomp-lines)
    (decompiled-form-repaint-fn form-coord)))

(defn- tab-spaces [n] (apply str (repeat n " ")))

(defn- render-class-line [form-id bytecode-map]
  {:form-id form-id
   :coord (:coord bytecode-map)
   :text (cond-> (str "class " (:class/name bytecode-map))
           (:class/super-name bytecode-map) (str " extends " (:class/super-name bytecode-map)))})

(defn- render-method-line [form-id vars bytecode-map]
  (let [[_ args-desc ret-type] (re-find #"\((.*)\)(.*);"  (:method/descriptor bytecode-map))
        args-types (if args-desc (str/split args-desc #";") [])]
    {:form-id form-id
     :coord (:coord bytecode-map)
     :text (str (tab-spaces 4)
                (or ret-type "void")
                " "
                (:method/name bytecode-map)
                "("
                (str/join ", " (map-indexed (fn [i at] (str at " " (get-in vars [i :name]))) args-types))
                ") "
                (str/join "," (:method/exceptions bytecode-map)))})) ;; TODO: render exceptions properly

(defn- render-instruction-line [form-id vars {:keys [instruction/op instruction/kind] :as bytecode-map}]
  (let [inst-text
        (cond
          (= kind :inst) (str/lower-case (name op))
          (= kind :var) (format "%s_%d /* %s */" (name op) (:var bytecode-map) (get-in vars [(:var bytecode-map) :name]))
          (= kind :field) (format "%s %s.%s:%s" (name op) (:owner bytecode-map) (:name bytecode-map) (:descriptor bytecode-map))
          (= kind :method) (format "%s %s.%s:%s" (name op) (:owner bytecode-map) (:name bytecode-map) (:descriptor bytecode-map))
          (= kind :jump) (format "%s %s" (name op) (:label bytecode-map))
          (= op :invoke-dynamic) (format "%s %s" (name op) (:descriptor bytecode-map))
          (= op :ldc) (format "%s %s" (name op) (:value bytecode-map))
          (= op :iinc) (format "%s_%d %s /* %s */" (name op) (:var bytecode-map) (:increment bytecode-map) (get-in vars [(:var bytecode-map) :name]))
          (= op :table-switch) (format "%s" (name op))
          (= op :lookup-switch) (format "%s" (name op))
          (= op :try-catch-block) (format "%s" (name op))
          :else (str "UNHANDLED " bytecode-map))]
    {:form-id form-id
     :coord (:coord bytecode-map)
     :text (str (tab-spaces 12) inst-text)}))

(defn- render-label-line [form-id bytecode-map]
  {:form-id form-id
   :coord (:coord bytecode-map)
   :text (str (tab-spaces 8) (:label/name bytecode-map) ":")})

(defn- render-field-line [form-id bytecode-map]
  {:form-id form-id
   :coord (:coord bytecode-map)
   :text (str (tab-spaces 4) (:field/descriptor bytecode-map) " " (:field/name bytecode-map))})

(defn- wrapping-coord? [coord-a coord-b]
  (if (empty? coord-a)
    true
    (and (every? true? (map = coord-a coord-b))
         (<= (count coord-a) (count coord-b)))))

(defn- build-form-bytecode-lines [{:keys [form-id emitted-coords form-classes]}]
  (let [form-bytecode-lines (->> form-classes
                                 (mapcat
                                  (fn [c]
                                    (-> [(render-class-line form-id (:class c))]
                                        (into (map (partial render-field-line form-id) (:fields c)))
                                        (into (mapcat (fn [{:keys [method vars instructions]}]
                                                        (-> [(render-method-line form-id vars method)]
                                                            (into (map (fn [inst-or-lbl]
                                                                         (case (:emitted/type inst-or-lbl)
                                                                           :instruction (render-instruction-line form-id vars inst-or-lbl)
                                                                           :label (render-label-line form-id inst-or-lbl)))
                                                                       instructions))))
                                                      (:methods c))))))
                                 (mapv (fn [idx l] (assoc l :idx idx)) (range)))
        form-coord-index (reduce (fn [coords-idx next-coord]
                                   (let [bytecode-hl-indices (->> form-bytecode-lines
                                                                  (keep (fn [{:keys [coord idx]}]
                                                                          (when (wrapping-coord? next-coord coord)
                                                                            idx)))
                                                                  (into #{}))]
                                     (assoc coords-idx next-coord bytecode-hl-indices)))
                                 {}
                                 emitted-coords)]
    {:form-bytecode-lines form-bytecode-lines
     :form-coord-idx form-coord-index}))

(defn add-form-decompilation [{:keys [form-id] :as decomp-data}]

  (let [form (runtime-api/get-form rt-api form-id)
        {:keys [form-bytecode-lines form-coord-idx]} (build-form-bytecode-lines decomp-data)
        _ (dbg-state/decompiler-add-form-emissions form-id form-bytecode-lines form-coord-idx)
        [forms-box] (obj-lookup "decompilation-forms-box")
        [forms-scroll-pane] (obj-lookup "decompilation-forms-scroll-pane")
        form-code-area (ui/code-area :editable? false :text "")
        _ (ui-utils/add-class form-code-area "form-pane")
        form-box (ui/v-box :childs [form-code-area]
                           :class "form-pane")
        form-clickable-coords (->> (keys form-coord-idx)
                                   (map (fn [c] {:coord c})))
        form-repaint-fn (partial
                         (build-form-paint-and-arm-fn
                          forms-scroll-pane
                          form
                          form-code-area
                          {:interesting-coord-click-handler (fn [mev clicked-coord-exprs line]
                                                              (update-code-panes form-id (-> clicked-coord-exprs first :coord)))})
                         form-clickable-coords)]
    (store-obj (str "decompiled-form-repaint-fn-" form-id) form-repaint-fn)

    (ui-utils/run-later
      (VBox/setVgrow form-box Priority/ALWAYS)
      (HBox/setHgrow form-box Priority/ALWAYS)

      (ui-utils/add-childrens-to-pane forms-box [form-box])
      (update-code-panes form-id []) ;; use [] as default coord
      )))

(defn main-pane []
  (let [^ScrollPane forms-scroll-pane (ui/scroll-pane :class "forms-scroll-container")
        decompiled-lv-data
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell {:keys [text highlighted?] :as bytecode-map}]
                                      (-> list-cell
                                          (ui-utils/set-text nil)
                                          (ui-utils/set-graphic (ui/label :text text
                                                                          :class (if highlighted?
                                                                                   "decompiled-inst-hl"
                                                                                   "decompiled-inst")))))
                      :on-click (fn [mev sel-items _]
                                  (let [{:keys [form-id idx] :as bytecode-map} (first sel-items)]
                                    (when (ui-utils/mouse-primary? mev)
                                      (update-code-panes form-id idx))))
                      :selection-mode :single)
        decomp-lv-pane (:list-view-pane decompiled-lv-data)
        dec-enable-toggle (ui/toggle-button
                           {:label "Decompilation Enable"
                            :on-change (fn [on?]
                                         (if (dbg-state/clojure-storm-env?)
                                           (runtime-api/turn-collect-forms-emissions rt-api on?)
                                           (show-message "This functionality is only available in Storm modes" :warning)))})
        tools-pane (ui/h-box :childs [dec-enable-toggle])
        code-pane (ui/split :orientation :horizontal
                            :childs [forms-scroll-pane decomp-lv-pane]
                            :sizes [0.5])
        mp (ui/border-pane :top tools-pane
                           :center code-pane
                           :class "decompiler")
        forms-box (ui/v-box :childs [] :spacing 5)]

    (VBox/setVgrow forms-scroll-pane Priority/ALWAYS)
    (HBox/setHgrow forms-scroll-pane Priority/ALWAYS)
    (VBox/setVgrow forms-box Priority/ALWAYS)
    (HBox/setHgrow forms-box Priority/ALWAYS)

    (-> forms-box
        .prefWidthProperty
        (.bind (.widthProperty forms-scroll-pane)))

    (store-obj "decompiled-list-view-data" decompiled-lv-data)
    (store-obj "decompilation-forms-box" forms-box)
    (store-obj "decompilation-forms-scroll-pane" forms-scroll-pane)
    (.setContent forms-scroll-pane forms-box)

    mp))
