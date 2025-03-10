(ns flow-storm.debugger.ui.flows.code
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [flow-storm.form-pprinter :as form-pprinter]
            [flow-storm.debugger.ui.flows.general :refer [open-form-in-editor]]
            [flow-storm.debugger.ui.commons :refer [def-val]]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.ui.flows.bookmarks :as bookmarks]
            [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.tasks :as tasks]
            [hansel.utils :refer [get-form-at-coord]]
            [flow-storm.debugger.ui.flows.printer :as printer]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows])
  (:import [javafx.scene Node]
           [javafx.scene.layout Priority VBox HBox]
           [javafx.scene.control ScrollPane Label SelectionModel]
           [javafx.scene.text Font]
           [javafx.scene.input KeyEvent ScrollEvent MouseEvent]
           [org.fxmisc.richtext CodeArea]
           [org.fxmisc.richtext.model StyleSpansBuilder TwoDimensional$Bias]))


(declare jump-to-coord)
(declare find-and-jump-same-val)

(defn- maybe-unwrap-runi-tokens

  "Unwrap <EXPR> from (fn* flowstorm-runi ([] <EXPR>)) if it is wrapped,
  returns print-tokens otherwise.
  Works at print-tokens level instead of at form level."

  [print-tokens]

  ;; This is as hacky as it gets but it is also for the vanilla non fn expressions
  ;; typed at the repl, like (+ 1 2)
  (if-let [runi-token-idx (some (fn [[i {:keys [text]}]]
                                  (when (= text "flowstorm-runi")
                                    i))
                                (map vector (range) (take 10 print-tokens)))]
    (let [expands-into-multiple-lines? (= runi-token-idx 5)
          wrap-beg (if expands-into-multiple-lines? 13 9)
          wrap-end (- (count print-tokens) 2)
          sub-tokens (subvec print-tokens wrap-beg wrap-end)
          expr-offset (+ (count "(fn* flowstorm-runi ([] ")
                         (if expands-into-multiple-lines?
                           4
                           0))]
      (->> sub-tokens
           ;; since we removed some tokens we need to move all idx-from back
           (map (fn [{:keys [idx-from] :as tok}]
                  (if idx-from
                    (update tok :idx-from #(- % expr-offset))
                    tok)))))

    print-tokens))

(defn- jump-to-record-here [flow-id thread-id form-id coord {:keys [backward? from-idx]}]
  (tasks/submit-task runtime-api/find-expr-entry-task
                     [{:flow-id   flow-id
                       :thread-id thread-id
                       :from-idx  from-idx
                       :backward? backward?
                       :coord     coord
                       :form-id   form-id}]
                     {:on-finished (fn [{:keys [result]}]
                                     (when result
                                       (jump-to-coord flow-id thread-id result)))}))

(defn- add-to-printer

  "Presents the user a dialog asking for a message and adds a print to the printer
  tool at the timeline-entry coord."

  [flow-id thread-id {:keys [coord fn-call-idx]}]
  (let [{:keys [form-id fn-ns fn-name]} (runtime-api/timeline-entry rt-api flow-id thread-id fn-call-idx :at)
        form (runtime-api/get-form rt-api form-id)
        source-expr (get-form-at-coord (:form/form form) coord)
        params-map (ui/ask-text-dialog+ :header "Printer message. Check textfields tooltips for help."
                                        :bodies [{:key :message-format :label "Message format" :tooltip "Use %s if you want to reposition the val in the message."}
                                                 {:key :expr-str  :label "Expression" :tooltip "Use functions like #(str (:name %) (:age %)) to transform the value. (No ClojureScript support yet)"}]
                                        :width  800
                                        :height 100
                                        :center-on-stage (dbg-state/main-jfx-stage))]
    (dbg-state/add-printer flow-id
                           form-id
                           coord
                           (assoc (dissoc form :form/form)
                                  :fn-ns fn-ns
                                  :fn-name fn-name
                                  :coord coord
                                  :source-expr source-expr
                                  :format-str (:message-format params-map)
                                  :transform-expr-str (:expr-str params-map)
                                  :print-length 5
                                  :print-level  3
                                  :enable? true))
    (printer/update-prints-controls flow-id)))

(defn- calculate-execution-idx-range [spans curr-coord]
  (when curr-coord
    (let [[s1 s2 :as hl-coords-spans] (->> spans
                                          (map-indexed (fn [i s] (assoc s :i i)))
                                          (filter (fn [{:keys [coord]}] (= coord curr-coord))))]
     (case (count hl-coords-spans)
       1 [(:idx-from s1) (+ (:idx-from s1) (:len s1))]
       2 [(:idx-from s1) (+ (:idx-from s2) (:len s2))]
       nil))))

(defn- build-style-spans

  "Given coord-spans as generated by `form-pprinter/coord-spans` build
  StyleSpans to be used in RichTextFX CodeAreas"

  [coord-spans curr-coord]

  (let [^StyleSpansBuilder spb (StyleSpansBuilder.)
        [exec-from exec-to] (calculate-execution-idx-range coord-spans curr-coord)]
    (doseq [{:keys [idx-from len coord interesting? tab?]} coord-spans]
      (let [executing? (and exec-from exec-to
                            (<= exec-from idx-from (+ idx-from len) exec-to))
            color-classes (cond-> ["code-token"]
                            (and coord (not interesting?))
                            (conj "possible")

                            (and executing? (not tab?))
                            (conj "executing")

                            (and executing? tab?)
                            (conj "executing-dim")

                            interesting?
                            (conj "interesting"))]

        (.add spb color-classes len)))

    (.create spb)))


(defn- make-coord-expressions-menu [flow-id thread-id clicked-coord-exprs curr-idx]
  (let [first-entry (first clicked-coord-exprs)
        last-entry  (get clicked-coord-exprs (dec (count clicked-coord-exprs)))
        interesting-N 20
        interesting-entries (cond
                              ;; if we are currently before this expressions
                              ;; show the first N discarding the first one since we
                              ;; add it as a special entry
                              (< curr-idx (:idx first-entry))
                              (subvec clicked-coord-exprs
                                      1
                                      (min interesting-N (count clicked-coord-exprs)))

                              ;; if we are currently after this expressions
                              ;; show the last N discarding the last one since we
                              ;; add it as a special entry
                              (> curr-idx (:idx last-entry))
                              (subvec clicked-coord-exprs
                                      (max 0 (- (count clicked-coord-exprs) interesting-N))
                                      (- (count clicked-coord-exprs) 2))

                              ;; if we are somewhere in the middle show N/2 before
                              ;; and N/2 after our current position
                              :else (let [{:keys [before match after]} (utils/grep-coll clicked-coord-exprs
                                                                                        (/ interesting-N 2)
                                                                                        (/ interesting-N 2)
                                                                                        (fn [{:keys [idx]}] (> idx curr-idx)))]
                                      (-> before
                                          (into [{:idx curr-idx}])
                                          (into [match])
                                          (into after))))

        make-menu-item (fn [{:keys [idx result]}]
                         (if (= idx curr-idx)
                           {:text (format "%d - << we are here >>" curr-idx)
                            :disable? true}
                           (let [v-str (-> (runtime-api/val-pprint rt-api result {:print-length 3 :print-level 3 :pprint? false})
                                           :val-str
                                           (utils/elide-string 80))]
                             {:text (cond
                                      (= (:idx first-entry) idx) (format "[FIRST] %d - %s" idx v-str)
                                      (= (:idx last-entry) idx)  (format "[LAST] %d - %s" idx v-str)
                                      :else                      (format "%d - %s" idx v-str))

                              :on-click #(jump-to-coord flow-id
                                                        thread-id
                                                        (runtime-api/timeline-entry rt-api flow-id thread-id idx :at))})))
        ctx-menu-options (mapv make-menu-item
                               (-> [first-entry]
                                   (into interesting-entries)
                                   (into [last-entry])
                                   ;; kind of hack. On small cases when we are in the middle
                                   ;; but close to the border we are going to have first and last
                                   ;; already added by interesting-entries
                                   dedupe))
        loop-traces-menu (ui/context-menu :items ctx-menu-options)]
    loop-traces-menu))

(defn copy-current-frame-symbol [flow-id thread-id args?]
  (let [{:keys [fn-name fn-ns args-vec]} (dbg-state/current-frame flow-id thread-id)]
    (ui-utils/copy-selected-frame-to-clipboard fn-ns fn-name (when args? args-vec))))

(defn- build-form-paint-and-arm-fn

  "Builds a form-paint-fn function that when called with expr-executions and a curr-coord
  will repaint and arm the form-code-area with the interesting and currently executing tokens.
  All interesting tokens will be clickable."

  [flow-id thread-id form ^CodeArea form-code-area print-tokens]
  (let [[thread-scroll-pane] (obj-lookup flow-id thread-id "forms_scroll")]
    (fn [expr-executions curr-coord]
      (let [interesting-coords (group-by :coord expr-executions)
            spans (->> print-tokens
                       (map (fn [{:keys [coord] :as tok}]
                              (if (contains? interesting-coords coord)
                                (assoc tok :interesting? true)
                                tok)))
                       form-pprinter/coord-spans)

            exec-idx  (some (fn [{:keys [coord idx-from]}]
                              (when (= coord curr-coord)
                                idx-from))
                            spans)
            style-spans (build-style-spans spans curr-coord)]
        (when exec-idx
          (.moveTo form-code-area exec-idx)
          (.requestFollowCaret form-code-area)

          (let [caret-pos (.getCaretPosition form-code-area)
                caret-pos-2d (.offsetToPosition form-code-area caret-pos TwoDimensional$Bias/Forward)
                caret-line (.getMajor caret-pos-2d)
                area-lines (-> form-code-area .getParagraphs .size)
                caret-area-perc (if (pos? area-lines) (float (/ caret-line area-lines)) 0)]
            (ui-utils/ensure-node-visible-in-scroll-pane thread-scroll-pane form-code-area caret-area-perc)))

        (.setStyleSpans form-code-area 0 0 style-spans)

        (.setOnMouseClicked form-code-area
                            (event-handler
                                [^MouseEvent mev]
                              (let [char-hit (.hit form-code-area (.getX mev) (.getY mev))
                                    opt-char-idx (.getCharacterIndex char-hit)
                                    ctx-menu-options (cond-> [{:text "Copy qualified function symbol"
                                                               :on-click (fn [] (copy-current-frame-symbol flow-id thread-id false))}
                                                              {:text "Copy function calling form"
                                                               :on-click (fn [] (copy-current-frame-symbol flow-id thread-id true))}]

                                                       (not (dbg-state/clojure-storm-env?))
                                                       (into [{:text "Fully instrument this form"
                                                               :on-click (fn []
                                                                           (runtime-api/eval-form rt-api
                                                                                                  (pr-str (:form/form form))
                                                                                                  {:instrument? true
                                                                                                   :ns (:form/ns form)}))}
                                                              {:text "Instrument this form without bindings"
                                                               :on-click (fn []
                                                                           (runtime-api/eval-form rt-api
                                                                                                  (pr-str (:form/form form))
                                                                                                  {:instrument? true
                                                                                                   :instrument-options {:disable #{:bind}}
                                                                                                   :ns (:form/ns form)}))}]))]

                                (if (.isPresent opt-char-idx)
                                  (let [char-idx (.getAsInt opt-char-idx)
                                        clicked-span (->> spans
                                                          (some (fn [{:keys [idx-from len] :as span}]
                                                                  (when (and (>= char-idx idx-from)
                                                                             (< char-idx (+ idx-from len)))
                                                                    span))))
                                        ctx-menu-options (cond-> ctx-menu-options
                                                           (:line clicked-span)
                                                           (into [{:text "Open in editor"
                                                                   :on-click (fn [] (open-form-in-editor form (:line clicked-span)))}]))
                                        curr-idx (dbg-state/current-idx flow-id thread-id)]
                                    (when-let [coord (:coord clicked-span)]
                                      (if (:interesting? clicked-span)
                                        (let [clicked-coord-exprs (get interesting-coords coord)]

                                          (if (ui-utils/mouse-secondary? mev)
                                            (ui-utils/show-context-menu :menu (ui/context-menu
                                                                               :items (into ctx-menu-options
                                                                                            [{:text "Add to prints"
                                                                                              :on-click #(add-to-printer flow-id thread-id (first clicked-coord-exprs))}]))
                                                                        :parent form-code-area
                                                                        :mouse-ev mev)
                                            ;; else
                                            (if (= 1 (count clicked-coord-exprs))

                                              (jump-to-coord flow-id thread-id (first clicked-coord-exprs))

                                              (ui-utils/show-context-menu
                                               :menu (make-coord-expressions-menu flow-id thread-id clicked-coord-exprs curr-idx)
                                               :parent form-code-area
                                               :mouse-ev mev))))

                                        ;; else if it is not interesting? we don't want to jump there
                                        ;; but provide a way of search and jump to it by coord and form
                                        (let [form-id (:form/id form)]

                                          (when (ui-utils/mouse-secondary? mev)
                                            (ui-utils/show-context-menu
                                             :menu (ui/context-menu
                                                    :items (into [{:text "Jump to first record here"
                                                                   :on-click (fn [] (jump-to-record-here flow-id thread-id form-id coord {:backward? false :from-idx 0}))}
                                                                  {:text "Jump forward here"
                                                                   :on-click (fn [] (jump-to-record-here flow-id thread-id form-id coord {:backward? false :from-idx curr-idx}))}
                                                                  {:text "Jump backwards here"
                                                                   :on-click (fn [] (jump-to-record-here flow-id thread-id form-id coord {:backward? true :from-idx curr-idx}))}]
                                                                 ctx-menu-options))
                                             :parent form-code-area
                                             :mouse-ev mev))))))

                                  ;; else clicked on the form background
                                  (when (ui-utils/mouse-secondary? mev)
                                    (ui-utils/show-context-menu
                                     :menu (ui/context-menu :items ctx-menu-options)
                                     :parent form-code-area
                                     :mouse-ev mev))))))))))
(defn- add-form

  "Pprints and adds a form to the flow and thread forms_box container."

  [form flow-id thread-id form-id]
  (let [print-tokens (binding [pp/*print-right-margin* 80]
                       (-> (form-pprinter/pprint-tokens (:form/form form))
                           ;; if it is a wrapped repl expression discard some tokens that the user
                           ;; isn't interested in
                           maybe-unwrap-runi-tokens))
        [^VBox forms-box] (obj-lookup flow-id thread-id "forms_box")
        code-text (form-pprinter/to-string print-tokens)
        ns-label (let [form-line (some-> form :form/form meta :line)
                       ^Label ns-lbl (ui/label :text (if form-line
                                                       (format "%s:%d" (:form/ns form) form-line)
                                                       (:form/ns form))
                                               :class "link-lbl-no-color")]
                   (doto ns-lbl
                     (.setOnMouseClicked (event-handler [_] (open-form-in-editor form)))
                     (.setFont (Font. 10))))

        form-header (ui/h-box :childs [ns-label]
                              :align :top-right)

        ^CodeArea form-code-area (ui/code-area :editable? false
                                               :text code-text)

        form-pane (ui/v-box
                   :childs [form-header form-code-area]
                   :class "form-pane")

        form-paint-fn (build-form-paint-and-arm-fn flow-id thread-id form form-code-area print-tokens)]

    ;; The code area when focused will capture all keyboard events, so we
    ;; re-fire them so they can be handled up in the chain
    (.addEventFilter form-code-area
                     KeyEvent/ANY
                     (event-handler
                         [^KeyEvent kev]
                       (.fireEvent ^Node forms-box (.copyFor kev form-code-area ^Node forms-box))))

    (.addEventFilter form-code-area
                     ScrollEvent/ANY
                     (event-handler
                         [^ScrollEvent sev]
                       (.fireEvent ^Node forms-box (.copyFor sev form-code-area ^Node forms-box))))

    (ui-utils/add-class form-code-area "form-pane")

    (store-obj flow-id thread-id (ui-utils/thread-form-box-id form-id) form-pane)
    (store-obj flow-id thread-id (ui-utils/thread-form-paint-fn form-id) form-paint-fn)

    (-> forms-box
        .getChildren
        (.add 0 form-pane))

    form-pane))

(defn- locals-cell-factory [_ {:keys [cell-type symb-name val-ref]}]
  (case cell-type
    :symbol (ui/label :text symb-name)
    :val-ref (ui/label :text (utils/elide-string (:val-str (runtime-api/val-pprint rt-api val-ref
                                                                                   {:print-length 20
                                                                                    :print-level 5
                                                                                    :pprint? false}))
                                                 80))))

(defn- on-locals-item-click [flow-id thread-id mev selected-items {:keys [table-view-pane]}]
  (when (ui-utils/mouse-secondary? mev)
    (let [[_ {:keys [val-ref]}] (first selected-items)
          ctx-menu (ui/context-menu :items
                                    [{:text "Define all frame vars"
                                      :on-click (fn []
                                                  (let [curr-idx (dbg-state/current-idx flow-id thread-id)
                                                        {:keys [fn-ns]} (dbg-state/current-frame flow-id thread-id)
                                                        all-bindings (runtime-api/bindings rt-api flow-id thread-id curr-idx {:all-frame? true})]
                                                    (doseq [[symb-name vref] all-bindings]
                                                      (let [symb (symbol fn-ns symb-name)]
                                                        (runtime-api/def-value rt-api symb vref)))))}
                                     {:text "Define var for val"
                                      :on-click (fn []
                                                  (def-val val-ref))}
                                     {:text "Tap val"
                                      :on-click (fn []
                                                  (runtime-api/tap-value rt-api val-ref))}
                                     {:text "Inspect"
                                      :on-click (fn []
                                                  (data-windows/create-data-window-for-vref val-ref))}])]
      (ui-utils/show-context-menu :menu ctx-menu
                                  :parent table-view-pane
                                  :mouse-ev mev))))

(defn- create-locals-pane [flow-id thread-id]
  (let [{:keys [table-view-pane] :as tv-data}
        (ui/table-view
         :columns ["Binding" "Value"]
         :cell-factory locals-cell-factory
         :resize-policy :constrained
         :on-click (partial on-locals-item-click flow-id thread-id)
         :selection-mode :single
         :search-predicate (fn [[{:keys [symb-name]} _] search-str]
                             (str/includes? symb-name search-str)))]
    (store-obj flow-id thread-id "locals_table" tv-data)

    table-view-pane))

(defn- update-locals-pane [flow-id thread-id bindings]
  (let [[{:keys [clear add-all]}] (obj-lookup flow-id thread-id "locals_table")]
    (clear)
    (->> bindings
         (mapv (fn [[symb-name val-ref]]
                 [{:cell-type :symbol
                   :symb-name symb-name}
                  {:cell-type :val-ref
                   :val-ref val-ref}]))
         add-all) ))

(defn- create-stack-pane [flow-id thread-id]
  (let [cell-factory (fn [list-cell {:keys [fn-ns fn-name form-def-kind dispatch-val]}]
                       (ui-utils/set-graphic list-cell (ui/label :text (if (= :defmethod form-def-kind)
                                                                         (str fn-ns "/" fn-name " " dispatch-val)
                                                                         (str fn-ns "/" fn-name))
                                                                 :class "link-lbl")))
        item-click (fn [mev selected-items _]
                     (let [{:keys [fn-call-idx]} (first selected-items)]
                       (when (and (ui-utils/mouse-primary? mev)
                                  (ui-utils/double-click? mev))
                         (jump-to-coord flow-id
                                        thread-id
                                        (runtime-api/timeline-entry rt-api flow-id thread-id fn-call-idx :at)))))
        {:keys [list-view-pane] :as lv-data}
        (ui/list-view :editable? false
                      :selection-mode :single
                      :cell-factory cell-factory
                      :on-click item-click)]
    (store-obj flow-id thread-id "stack_list" lv-data)

    list-view-pane))

(defn- update-frames-stack [flow-id thread-id fn-call-idx]
  (let [stack (runtime-api/stack-for-frame rt-api flow-id thread-id fn-call-idx)
        [{:keys [clear add-all]}] (obj-lookup flow-id thread-id "stack_list")]
    (clear)
    (add-all stack)))

(defn update-thread-trace-count-lbl [flow-id thread-id]
  (when-let [[lbl] (obj-lookup flow-id thread-id "thread_trace_count_lbl")]
    (let [cnt (runtime-api/timeline-count rt-api flow-id thread-id)]
      (ui-utils/set-text lbl (str (dec cnt))))))

(defn- unhighlight-form [flow-id thread-id form-id]
  (let [[form-pane] (obj-lookup flow-id thread-id (ui-utils/thread-form-box-id form-id))]
    (when form-pane
      (ui-utils/rm-class form-pane "form-background-highlighted"))))

(defn add-or-highlight-form [flow-id thread-id form-id]
  (let [form (runtime-api/get-form rt-api form-id)
        [form-pane]          (obj-lookup flow-id thread-id (ui-utils/thread-form-box-id form-id))

        ;; if the form we are about to highlight doesn't exist in the view add it first
        form-pane (or form-pane (add-form form flow-id thread-id form-id))]

    (ui-utils/add-class form-pane "form-background-highlighted")))

(defn un-highlight-form-tokens [flow-id thread-id form-id]
  (let [[form-paint-fn] (obj-lookup flow-id thread-id (ui-utils/thread-form-paint-fn form-id))]
    (when form-paint-fn
      (form-paint-fn [] nil))))

(defn highlight-interesting-form-tokens [flow-id thread-id form-id frame-data entry]
  (let [{:keys [expr-executions]} frame-data
        [form-paint-fn] (obj-lookup flow-id thread-id (ui-utils/thread-form-paint-fn form-id))]
    (when form-paint-fn
      (form-paint-fn expr-executions (:coord entry)))))

(defn jump-to-coord [flow-id thread-id next-tentry]
  (try
    (when (:debug-mode? (dbg-state/debugger-config))
      (utils/log (str "Jump to " next-tentry)))
    (let [curr-frame (if-let [cfr (dbg-state/current-frame flow-id thread-id)]
                       cfr
                       ;; if we don't have a current frame it means it is the first
                       ;; jump so, initialize the debugger thread state
                       (let [first-frame (runtime-api/frame-data rt-api flow-id thread-id 0 {:include-exprs? true})
                             first-tentry (runtime-api/timeline-entry rt-api flow-id thread-id 0 :at)]
                         (dbg-state/set-current-frame flow-id thread-id first-frame)
                         (dbg-state/set-current-timeline-entry flow-id thread-id first-tentry)
                         first-frame))

          curr-tentry (dbg-state/current-timeline-entry flow-id thread-id)
          curr-idx (:idx curr-tentry)
          next-idx (:idx next-tentry)

          curr-fn-call-idx (:fn-call-idx curr-frame)
          next-fn-call-idx (:fn-call-idx next-tentry)
          changing-frame? (not= curr-fn-call-idx next-fn-call-idx)
          next-frame (if changing-frame?
                       (let [nfr (runtime-api/frame-data rt-api flow-id thread-id next-fn-call-idx {:include-exprs? true})]
                         nfr)
                       curr-frame)
          curr-form-id (:form-id curr-frame)
          next-form-id (:form-id next-frame)

          [curr-trace-text-field] (obj-lookup flow-id thread-id "thread_curr_trace_tf")
          first-jump? (and (zero? curr-idx) (zero? next-idx))
          changing-form? (not= curr-form-id next-form-id)]

      ;; update thread current trace label and total traces
      (ui-utils/set-text-input-text curr-trace-text-field (str next-idx))
      (update-thread-trace-count-lbl flow-id thread-id)

      (when (or first-jump? changing-frame?)

        (un-highlight-form-tokens flow-id thread-id curr-form-id)

        (update-frames-stack flow-id thread-id next-fn-call-idx)

        (when (or first-jump? changing-form?)
          (unhighlight-form flow-id thread-id curr-form-id)
          (add-or-highlight-form flow-id thread-id next-form-id)))

      (highlight-interesting-form-tokens flow-id thread-id next-form-id next-frame next-tentry)

      ;; update expression reusult panels
      (let [[val-ref e-text class] (case (:type next-tentry)
                                     :fn-call   [nil                      "Call"    :normal]
                                     :expr      [(:result next-tentry)    ""        :normal]
                                     :fn-return [(:result next-tentry)    "Return"  :normal]
                                     :fn-unwind [(:throwable next-tentry) "Throws" :fail])
            dw-id (format "expr-result-flow-%s-thread-%s" flow-id thread-id)]
        (flow-cmp/update-pprint-pane flow-id thread-id "expr_result"
                                     {:val-ref   val-ref
                                      :extra-text e-text
                                      :class      class}
                                     {:find-and-jump-same-val (partial find-and-jump-same-val flow-id thread-id)})
        (runtime-api/data-window-push-val-data rt-api
                                               dw-id
                                               val-ref
                                               {:root? true
                                                :flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                :flow-storm.debugger.ui.data-windows.data-windows/stack-key "expr-result"}))

      ;; update locals panel
      (update-locals-pane flow-id thread-id (runtime-api/bindings rt-api flow-id thread-id next-idx {}))

      (when changing-frame?
        (dbg-state/set-current-frame flow-id thread-id next-frame))

      (dbg-state/set-current-timeline-entry flow-id thread-id next-tentry)
      (dbg-state/update-nav-history flow-id thread-id next-tentry))
    (catch Throwable e
      (utils/log-error (str "Error jumping into " flow-id " " thread-id " " next-tentry) e))))

(defn step-prev [flow-id thread-id]
  (let [curr-idx (dbg-state/current-idx flow-id thread-id)
        prev-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :prev)]
    (jump-to-coord flow-id thread-id prev-tentry)))

(defn step-next [flow-id thread-id]
  (let [curr-idx (dbg-state/current-idx flow-id thread-id)
        next-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :next)]
    (jump-to-coord flow-id thread-id next-tentry)))

(defn step-next-over [flow-id thread-id]
  (let [curr-idx (dbg-state/current-idx flow-id thread-id)
        next-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :next-over)]
    (jump-to-coord flow-id thread-id next-tentry)))

(defn step-prev-over [flow-id thread-id]
  (let [curr-idx (dbg-state/current-idx flow-id thread-id)
        prev-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :prev-over)]
    (jump-to-coord flow-id thread-id prev-tentry)))

(defn step-out [flow-id thread-id]
  (let [curr-idx (dbg-state/current-idx flow-id thread-id)
        out-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :next-out)]
    (jump-to-coord flow-id thread-id out-tentry)))

(defn step-first [flow-id thread-id]
  (let [first-tentry (runtime-api/timeline-entry rt-api flow-id thread-id 0 :at)]
    (jump-to-coord flow-id thread-id first-tentry)))

(defn step-last [flow-id thread-id]
  (let [last-idx (dec (runtime-api/timeline-count rt-api flow-id thread-id))
        last-tentry (runtime-api/timeline-entry rt-api flow-id thread-id last-idx :at)]
    (jump-to-coord flow-id thread-id last-tentry)))

(defn find-and-jump [current-flow-id current-thread-id search-params]
  (tasks/submit-task runtime-api/find-expr-entry-task
                     [search-params]
                     {:on-finished (fn [{:keys [result]}]
                                     (when result
                                       (let [{:keys [thread-id idx] :as next-tentry} result]
                                         (if (= current-thread-id thread-id)
                                           (jump-to-coord current-flow-id current-thread-id next-tentry)
                                           (let [goto-loc (requiring-resolve 'flow-storm.debugger.ui.flows.screen/goto-location)]
                                             (goto-loc {:flow-id   current-flow-id
                                                        :thread-id thread-id
                                                        :idx       idx}))))))}))

(defn find-and-jump-same-val [flow-id thread-id v-ref backward?]
  (let [{:keys [idx]} (dbg-state/current-timeline-entry flow-id thread-id)
        from-idx (if backward? (dec idx) (inc idx))]
    (find-and-jump flow-id thread-id {:identity-val v-ref
                                      :flow-id flow-id
                                      :thread-id thread-id
                                      :backward? backward?
                                      :from-idx from-idx})))

(defn- power-stepping-pane [flow-id thread-id]
  (let [custom-expression-txt (ui/text-field :initial-text "(fn [v] v)")
        *selected-fn (atom nil)
        fn-selector (ui/autocomplete-textfield
                     :on-select-set-text? true
                     :get-completions
                     (fn []
                       (into []
                             (keep (fn [{:keys [fn-ns fn-name]}]

                                     (when-not (re-find #"fn--[\d]+$" fn-name)
                                       {:text (format "%s/%s" fn-ns fn-name)
                                        :on-select (fn []
                                                     (reset! *selected-fn (symbol fn-ns fn-name)))})))
                             (runtime-api/fn-call-stats rt-api flow-id thread-id))))
        show-custom-field (fn [field]
                            (doto custom-expression-txt (.setVisible false) (.setPrefWidth 0))
                            (doto fn-selector (.setVisible false) (.setPrefWidth 0))
                            (case field
                              :custom-txt  (doto custom-expression-txt (.setVisible true) (.setPrefWidth 200))
                              :fn-selector (doto fn-selector (.setVisible true) (.setPrefWidth 200))
                              nil))
        _ (show-custom-field nil)
        step-type-combo (ui/combo-box :items ["identity" "identity-other-thread" "equality" "same-coord" "custom" "custom-same-coord" "fn-call"]
                                      :on-change (fn [_ new-val]
                                                   (case new-val
                                                     "identity"              (show-custom-field nil)
                                                     "identity-other-thread" (show-custom-field nil)
                                                     "equality"              (show-custom-field nil)
                                                     "same-coord"            (show-custom-field nil)
                                                     "custom"                (show-custom-field :custom-txt)
                                                     "custom-same-coord"     (show-custom-field :custom-txt)
                                                     "fn-call"               (show-custom-field :fn-selector))))
        search-params (fn [backward?]
                        (let [^SelectionModel sel-model (.getSelectionModel step-type-combo)
                              step-type-val (.getSelectedItem sel-model)
                              tentry (dbg-state/current-timeline-entry flow-id thread-id)
                              [idx target-val coord] (if (= :fn-unwind (:type tentry))
                                                       [(:idx tentry) (:throwable tentry) (:coord tentry)]
                                                       [(:idx tentry) (:result tentry) (:coord tentry)])

                              {:keys [form-id]} (dbg-state/current-frame flow-id thread-id)
                              from-idx (if backward? (dec idx) (inc idx))
                              sel-fn-call-symb @*selected-fn
                              params (case step-type-val
                                       "identity"              {:identity-val target-val
                                                                :thread-id thread-id
                                                                :backward? backward?
                                                                :from-idx from-idx}
                                       "identity-other-thread" {:identity-val target-val
                                                                :from-idx 0
                                                                :skip-threads #{thread-id}
                                                                :backward? false}
                                       "equality"              {:equality-val target-val
                                                                :thread-id thread-id
                                                                :backward? backward?
                                                                :from-idx from-idx}
                                       "same-coord"            {:coord coord
                                                                :form-id form-id
                                                                :thread-id thread-id
                                                                :backward? backward?
                                                                :from-idx from-idx}
                                       "custom"                {:custom-pred-form (.getText custom-expression-txt)
                                                                :thread-id thread-id
                                                                :backward? backward?
                                                                :from-idx from-idx}
                                       "custom-same-coord"     {:custom-pred-form (.getText custom-expression-txt)
                                                                :coord coord
                                                                :form-id form-id
                                                                :thread-id thread-id
                                                                :backward? backward?
                                                                :from-idx from-idx}
                                       "fn-call"               {:fn-ns   (namespace sel-fn-call-symb)
                                                                :fn-name (name sel-fn-call-symb)
                                                                :from-idx from-idx
                                                                :thread-id thread-id
                                                                :backward? backward?})]
                          (assoc params :flow-id flow-id)))
        val-first-btn (ui/icon-button :icon-name "mdi-ray-start"
                                      :on-click (fn []
                                                  (find-and-jump flow-id
                                                                 thread-id
                                                                 (-> (search-params false)
                                                                     (assoc :from-idx 0))))
                                      :tooltip "Power step to the first expression")
        val-prev-btn (ui/icon-button :icon-name "mdi-ray-end-arrow"
                                     :on-click (fn [] (find-and-jump flow-id thread-id (search-params true)))
                                     :tooltip "Power step to the prev expression")
        val-next-btn (ui/icon-button :icon-name "mdi-ray-start-arrow"
                                     :on-click (fn [] (find-and-jump flow-id thread-id (search-params false)))
                                     :tooltip "Power step to the next expression")
        val-last-btn (ui/icon-button :icon-name "mdi-ray-end"
                                     :on-click (fn []
                                                 (find-and-jump flow-id
                                                                thread-id
                                                                (-> (search-params true)
                                                                    (dissoc :from-idx))))
                                     :tooltip "Power step to the last expression")

        power-stepping-pane (ui/h-box :childs [val-first-btn val-prev-btn val-next-btn val-last-btn step-type-combo custom-expression-txt fn-selector]
                                      :spacing 3)]
    power-stepping-pane))

(defn undo-jump [flow-id thread-id]
  (binding [dbg-state/*undo-redo-jump* true]
    (jump-to-coord flow-id thread-id (dbg-state/undo-nav-history flow-id thread-id))))

(defn redo-jump [flow-id thread-id]
  (binding [dbg-state/*undo-redo-jump* true]
    (jump-to-coord flow-id thread-id (dbg-state/redo-nav-history flow-id thread-id))))

(defn- trace-pos-pane [flow-id thread-id]
  (let [first-btn (ui/icon-button :icon-name "mdi-page-first"
                                  :on-click (fn [] (step-first flow-id thread-id))
                                  :tooltip "Step to the first recorded expression")
        last-btn (ui/icon-button :icon-name "mdi-page-last"
                                 :on-click (fn [] (step-last flow-id thread-id))
                                 :tooltip "Step to the last recorded expression")

        curr-trace-text-field (ui/text-field :initial-text "0"
                                             :on-return-key (fn [idx-str]
                                                              (let [[^ScrollPane forms-scroll-pane] (obj-lookup flow-id thread-id "forms_scroll")
                                                                    target-idx (Long/parseLong idx-str)
                                                                    target-tentry (runtime-api/timeline-entry rt-api flow-id thread-id target-idx :at)]
                                                                (jump-to-coord flow-id thread-id target-tentry)
                                                                (.requestFocus forms-scroll-pane)))
                                             :align :center-right
                                             :pref-width 80)

        separator-lbl (ui/label :text "/")
        thread-trace-count-lbl (ui/label :text "?")]

    (store-obj flow-id thread-id "thread_curr_trace_tf" curr-trace-text-field)
    (store-obj flow-id thread-id "thread_trace_count_lbl" thread-trace-count-lbl)

    (ui/h-box :childs [first-btn curr-trace-text-field separator-lbl thread-trace-count-lbl last-btn]
              :class "trace-position-box"
              :spacing 2)))

(defn- create-bookmarks-and-nav-pane [flow-id thread-id]
  (let [bookmark-btn (ui/icon-button :icon-name "mdi-bookmark"
                                     :on-click (fn []
                                                 (bookmarks/bookmark-add
                                                  flow-id
                                                  thread-id
                                                  (dbg-state/current-idx flow-id thread-id)))
                                     :tooltip "Bookmark the current position")
        undo-nav-btn (ui/icon-button :icon-name "mdi-undo"
                                     :on-click (fn [] (undo-jump flow-id thread-id))
                                     :tooltip "Undo navigation")
        redo-nav-btn (ui/icon-button :icon-name "mdi-redo"
                                     :on-click (fn [] (redo-jump flow-id thread-id))
                                     :tooltip "Redo navigation")]
    (ui/h-box :childs [undo-nav-btn redo-nav-btn
                       bookmark-btn]
              :spacing 2)))


(defn- create-controls-first-row-pane [flow-id thread-id]
  (let [bookmarks-and-nav-pane (create-bookmarks-and-nav-pane flow-id thread-id)]
    (ui/h-box :childs [bookmarks-and-nav-pane
                       (power-stepping-pane flow-id thread-id)]
              :class "thread-controls-pane"
              :spacing 20)))

(defn- create-controls-second-row-pane [flow-id thread-id]
  (let [prev-over-btn (ui/icon-button :icon-name "mdi-debug-step-over"
                                      :on-click (fn [] (step-prev-over flow-id thread-id))
                                      :tooltip "Step over to the previous expression in the current frame"
                                      :mirrored? true)
        prev-btn (ui/icon-button :icon-name "mdi-chevron-left"
                                 :on-click (fn [] (step-prev flow-id thread-id))
                                 :tooltip "Step in the previous expression")

        out-btn (ui/icon-button :icon-name "mdi-debug-step-out"
                                :on-click (fn []
                                            (step-out flow-id thread-id))
                                :tooltip "Step out to the caller, right after calling this funciton.")

        next-btn (ui/icon-button :icon-name "mdi-chevron-right"
                                 :on-click (fn [] (step-next flow-id thread-id))
                                 :tooltip "Step in the next expression")
        next-over-btn (ui/icon-button :icon-name "mdi-debug-step-over"
                                      :on-click (fn [] (step-next-over flow-id thread-id))
                                      :tooltip "Step over to the next expression in the current frame")

        controls-box (ui/h-box :childs [prev-over-btn prev-btn out-btn next-btn next-over-btn]
                               :spacing 2)]

    (ui/h-box :childs [controls-box
                       (trace-pos-pane flow-id thread-id)]
              :class "thread-controls-pane"
              :spacing 20)))

(defn- create-forms-pane [flow-id thread-id]
  (let [^VBox forms-box (ui/v-box :childs []
                                  :spacing 5)
        _ (.setOnScroll forms-box
                        (event-handler
                            [^ScrollEvent ev]
                          (when (or (.isAltDown ev) (.isControlDown ev))
                            (.consume ev)
                            (cond
                              (> (.getDeltaY ev) 0) (step-prev flow-id thread-id)
                              (< (.getDeltaY ev) 0) (step-next flow-id thread-id)))))
        ^ScrollPane scroll-pane (ui/scroll-pane :class "forms-scroll-container")
        controls-first-row-pane (create-controls-first-row-pane flow-id thread-id)
        controls-second-row-pane (create-controls-second-row-pane flow-id thread-id)
        outer-box (ui/v-box :childs [controls-first-row-pane
                                     controls-second-row-pane
                                     scroll-pane])]
    (VBox/setVgrow forms-box Priority/ALWAYS)
    (VBox/setVgrow scroll-pane Priority/ALWAYS)
    (HBox/setHgrow scroll-pane Priority/ALWAYS)
    (-> forms-box
        .prefWidthProperty
        (.bind (.widthProperty scroll-pane)))
    (.setContent scroll-pane forms-box)
    (store-obj flow-id thread-id "forms_box" forms-box)
    (store-obj flow-id thread-id "forms_scroll" scroll-pane)
    outer-box))

(defn- create-result-pane [flow-id thread-id]
  (let [pprint-tab (ui/tab :graphic (ui/icon :name "mdi-code-braces")
                           :content (flow-cmp/create-pprint-pane flow-id thread-id "expr_result"))
        dw-tab (ui/tab :graphic (ui/icon :name "mdi-flash-red-eye")
                       :content (data-windows/data-window-pane {:data-window-id (format "expr-result-flow-%s-thread-%s" flow-id thread-id)}))
        tools-tab-pane (ui/tab-pane :closing-policy :unavailable
                                    :tabs [dw-tab pprint-tab])]
    tools-tab-pane))

(defn create-code-pane [flow-id thread-id]
  (let [forms-pane (create-forms-pane flow-id thread-id)
        result-pane (create-result-pane flow-id thread-id)
        locals-stack-tab-pane (ui/tab-pane :tabs [(ui/tab :text "Locals"
                                                          :content (create-locals-pane flow-id thread-id)
                                                          :tooltip "Locals")
                                                  (ui/tab :text "Stack"
                                                          :content (create-stack-pane flow-id thread-id)
                                                          :tooltip "Locals")]
                                           :side :top
                                           :closing-policy :unavailable)
        locals-result-pane (ui/split :orientation :vertical
                                     :childs [result-pane locals-stack-tab-pane])
        left-right-pane (ui/split :orientation :horizontal
                                  :childs [forms-pane locals-result-pane]
                                  :sizes [0.6])]

    left-right-pane))
