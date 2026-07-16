(ns flow-storm.debugger.ui.decompiler.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.flows.general :as ui-general :refer [build-form-paint-and-arm-fn]]
            [clojure.string :as str]
            [clojure.set :as set])
  (:import [javafx.scene.control ScrollPane]
           [javafx.scene.layout VBox Priority HBox]))

;; This is a hacky way of using the store-obj/obj-lookup made for flows
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- dec-store-obj [obj-id obj-ref]
  (store-obj :decompiler obj-id obj-ref))

(defn- dec-obj-lookup [obj-id]
  (first (obj-lookup :decompiler obj-id)))

(defn- dec-clean-objects []
  (dbg-state/clean-objs :decompiler))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- update-code-panes [form-id coord-or-bytecode-idx]
  (let [{:keys [form-coord highlighted-decomp-lines]} (dbg-state/decompiler-form-highlighted-decomp-lines form-id coord-or-bytecode-idx)
        {:keys [form-repaint-fn]} (dec-obj-lookup (str "decompiled-form-data-" form-id))
        [{:keys [clear add-all]}] (obj-lookup "decompiled-list-view-data")]
    (clear)
    (add-all highlighted-decomp-lines)
    (form-repaint-fn form-coord)))

(defn- remove-form [form-id]
  (let [{:keys [form-box-obj]} (dec-obj-lookup (str "decompiled-form-data-" form-id))
        [forms-box] (obj-lookup "decompilation-forms-box")]
    (dbg-state/decompiler-remove-form form-id)
    (when (and forms-box form-box-obj)
      (ui-utils/remove-children-from-pane forms-box form-box-obj))))

(defn- tab-spaces [n] (apply str (repeat n " ")))

(defn- render-access [access]
  (->> [(when-let [vis (first (set/intersection #{:public :protected :private} access))] (name vis))
        (when (access :final) "final")
        (when (access :static) "static")]
       (remove nil?)
       (str/join " ")))

(defn- render-type [type-str]
  (when type-str
    (if (str/starts-with? type-str "L")
      (str/replace (subs type-str 1) "/" ".")
      type-str)))

(defn- render-class-line [form-id bytecode-map]
  {:form-id form-id
   :coord (:coord bytecode-map)
   :text (cond-> (str (render-access (:class/access bytecode-map)) " class " (:class/name bytecode-map))
           (:class/super-name bytecode-map) (str " extends " (render-type (:class/super-name bytecode-map))))})

(defn- render-field-line [form-id bytecode-map]
  {:form-id form-id
   :coord (:coord bytecode-map)
   :text (str (tab-spaces 2) (render-access (:field/access bytecode-map)) " " (render-type (:field/descriptor bytecode-map)) " " (:field/name bytecode-map))})

(defn- render-method-line [form-id vars bytecode-map]
  (let [[_ args-desc ret-type] (re-find #"\((.*)\)(.*);"  (:method/descriptor bytecode-map))
        args-types (if args-desc (map render-type (str/split args-desc #";")) [])]
    {:form-id form-id
     :coord (:coord bytecode-map)
     :text (str "\n\n"
                (tab-spaces 2)
                (render-access (:method/access bytecode-map))
                " "
                (or (render-type ret-type) "void")
                " "
                (:method/name bytecode-map)
                "("
                (str/join ", " (map-indexed (fn [i at] (str at " " (get-in vars [i :name]))) args-types))
                ") "
                (str/join "," (:method/exceptions bytecode-map)))})) ;; TODO: render exceptions properly

(defn- render-label-line [form-id bytecode-map]
  {:form-id form-id
   :coord (:coord bytecode-map)
   :text (str (tab-spaces 4) (:label/name bytecode-map) ":")})

(defn- render-instruction-line [form-id vars {:keys [instruction/op instruction/kind] :as bytecode-map}]
  (let [inst-text
        (cond
          (= kind :inst) (format "%s %s" (str/lower-case (name op)) (if-let [io (:operand bytecode-map)] io ""))
          (= kind :var) (str (name op) "_" (:var bytecode-map) (if-let [v (get-in vars [(:var bytecode-map) :name])] (format " /* %s */" v) ""))
          (= kind :type) (str (name op) " " (:type bytecode-map))
          (= kind :field) (format "%s %s.%s:%s" (name op) (:owner bytecode-map) (:name bytecode-map) (:descriptor bytecode-map))
          (= kind :method) (format "%s %s.%s:%s" (name op) (:owner bytecode-map) (:name bytecode-map) (:descriptor bytecode-map))
          (= kind :jump) (format "%s %s" (name op) (:label bytecode-map))
          (= op :invoke-dynamic) (format "%s %s" (name op) (:descriptor bytecode-map))
          (= op :ldc) (format "%s %s" (name op) (pr-str (:value bytecode-map)))
          (= op :iinc) (str "%s_%d %s /* %s */" (name op) "_" (:var bytecode-map) " " (:increment bytecode-map) (if-let [v (get-in vars [(:var bytecode-map) :name])] (format " /* %s */" v) "" ))
          (= op :table-switch) (format "%s Low: %d High: %d Default: %s Labels: %s" (name op) (:min bytecode-map) (:max bytecode-map) (:default-label bytecode-map) (into [] (:labels bytecode-map)))
          (= op :lookup-switch) (format "%s Default: %s Keys: %s Labels: %s" (name op) (:default-label bytecode-map) (:keys bytecode-map) (:labels bytecode-map))
          :else (str "UNHANDLED " bytecode-map))]
    {:form-id form-id
     :coord (:coord bytecode-map)
     :text (str (tab-spaces 6) inst-text)}))

(defn- render-try-catch-block [form-id coord {:keys [start-label end-label handler-label type]}]
  {:form-id form-id
   :coord coord
   :text (format "%stype: %s, from:%s, to:%s, handler: %s" (tab-spaces 4) type start-label end-label handler-label)})

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
                                        (into (mapcat (fn [{:keys [method try-catch-blocks vars instructions]}]
                                                        (cond-> [(render-method-line form-id vars method)]
                                                          true (into (map (fn [inst-or-lbl]
                                                                            (case (:emitted/type inst-or-lbl)
                                                                              :instruction (render-instruction-line form-id vars inst-or-lbl)
                                                                              :label (render-label-line form-id inst-or-lbl)))
                                                                          instructions))
                                                          (seq try-catch-blocks) (into (map (fn [tcb] (render-try-catch-block form-id (:coord method) tcb)) try-catch-blocks))))
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
  ;; make sure we don't have a form already for the form-id
  (ui-utils/run-now (remove-form form-id))

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
                          {:interesting-coord-click-handler (fn [_ clicked-coord-exprs _]
                                                              (update-code-panes form-id (-> clicked-coord-exprs first :coord)))})
                         form-clickable-coords)]

    (dec-store-obj (str "decompiled-form-data-" form-id) {:form-repaint-fn form-repaint-fn :form-box-obj form-box})
    (ui-utils/run-later
      (VBox/setVgrow form-box Priority/ALWAYS)
      (HBox/setHgrow form-box Priority/ALWAYS)

      (ui-utils/add-childrens-to-pane forms-box [form-box])
      (update-code-panes form-id []) ;; use [] as default coord
      )))

(defn clear-forms []
  (dbg-state/decompiler-clear)
  (let [[forms-box] (obj-lookup "decompilation-forms-box")
        [{:keys [clear]}] (obj-lookup "decompiled-list-view-data")]
    (ui-utils/clear-pane-childrens forms-box)
    (clear)
    (dec-clean-objects)))

(defn main-pane []
  (let [^ScrollPane forms-scroll-pane (ui/scroll-pane :class "forms-scroll-container")
        decompiled-lv-data
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell {:keys [text highlighted?]}]
                                      (-> list-cell
                                          (ui-utils/set-text nil)
                                          (ui-utils/set-graphic (ui/label :text text
                                                                          :class (if highlighted?
                                                                                   "decompiled-inst-hl"
                                                                                   "decompiled-inst")))))
                      :on-click (fn [mev sel-items _]
                                  (let [{:keys [form-id idx]} (first sel-items)]
                                    (when (ui-utils/mouse-primary? mev)
                                      (when (and form-id idx)
                                        (update-code-panes form-id idx)))))
                      :selection-mode :single)
        decomp-lv-pane (:list-view-pane decompiled-lv-data)
        clear-btn (ui/icon-button :icon-name  "mdi-delete-forever"
                                  :tooltip "Clean all outputs (Ctrl-l)"
                                  :on-click (fn [] (clear-forms)))
        disable-locals-clearing-chk (ui/check-box :selected? false
                                                  :on-change (fn [on?]
                                                               (runtime-api/set-boolean-compiler-option rt-api :disable-locals-clearing on?)))
        direct-linking-chk (ui/check-box :selected? false
                                         :on-change (fn [on?]
                                                      (runtime-api/set-boolean-compiler-option rt-api :direct-linking on?)))
        tools-pane (ui/h-box :childs [clear-btn
                                      (ui/label :text ":disable-locals-clearing") disable-locals-clearing-chk
                                      (ui/label :text ":direct-linking") direct-linking-chk]
                             :spacing 5
                             :paddings [10 10 10 10])
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
