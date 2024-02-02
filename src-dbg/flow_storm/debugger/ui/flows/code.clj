(ns flow-storm.debugger.ui.flows.code
  (:require [clojure.pprint :as pp]
            [flow-storm.form-pprinter :as form-pprinter]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon list-view text-field tab-pane tab combo-box border-pane]]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.ui.flows.bookmarks :as bookmarks]
            [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup subscribe-to-task-event]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [hansel.utils :refer [get-form-at-coord]])
  (:import [javafx.scene.control Label Tab TabPane TabPane$TabClosingPolicy SplitPane TextField TextInputDialog]
           [javafx.scene Node]
           [javafx.geometry Orientation Pos]
           [javafx.scene.layout Priority VBox HBox]
           [javafx.scene.text Font]
           [javafx.scene.input KeyCode MouseButton KeyEvent ScrollEvent]
           [org.fxmisc.richtext CodeArea]
           [org.fxmisc.richtext.model StyleSpansBuilder TwoDimensional$Bias]
           [javafx.scene.input MouseEvent]))

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
  (when-let [tentry (runtime-api/find-timeline-entry rt-api {:flow-id flow-id
                                                             :thread-id thread-id
                                                             :from-idx (or from-idx 0)
                                                             :backward? backward?
                                                             :eq-val-ref nil
                                                             :comp-fn-key :same-coord
                                                             :comp-fn-coord coord
                                                             :comp-fn-form-id form-id})]
    (jump-to-coord flow-id thread-id tentry)))

(defn- add-to-printer

  "Presents the user a dialog asking for a message and adds a print to the printer
  tool at the timeline-entry coord."

  [flow-id thread-id {:keys [coord fn-call-idx]}]
  (let [{:keys [form-id fn-ns fn-name]} (runtime-api/timeline-entry rt-api flow-id thread-id fn-call-idx :at)
        form (runtime-api/get-form rt-api form-id)
        expr (get-form-at-coord (:form/form form) coord)
        tdiag (doto (TextInputDialog.)
                (.setHeaderText "Printer message. %s will be replaced by the value.")
                (.setContentText "Message : "))
        _ (.showAndWait tdiag)
        format-str (-> tdiag .getEditor .getText)]
    (dbg-state/add-printer form-id coord (assoc (dissoc form :form/form)
                                            :fn-ns fn-ns
                                            :fn-name fn-name
                                            :coord coord
                                            :expr expr
                                            :format-str format-str
                                            :print-length 5
                                            :print-level  3
                                            :enable? true))))

(defn- calculate-execution-idx-range [spans curr-coord]
  (let [[s1 s2 :as hl-coords-spans] (->> spans
                                         (map-indexed (fn [i s] (assoc s :i i)))
                                         (filter (fn [{:keys [coord]}] (= coord curr-coord))))]
    (case (count hl-coords-spans)
      1 [(:idx-from s1) (+ (:idx-from s1) (:len s1))]
      2 [(:idx-from s1) (+ (:idx-from s2) (:len s2))]
      nil)))

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
                              (let [char-hit (-> mev .getSource (.hit (.getX mev) (.getY mev)))
                                    opt-char-idx (.getCharacterIndex char-hit)]
                                (when (.isPresent opt-char-idx)
                                  (let [char-idx (.getAsInt opt-char-idx)
                                        clicked-span (->> spans
                                                          (some (fn [{:keys [idx-from len] :as span}]
                                                                  (when (and (>= char-idx idx-from)
                                                                             (< char-idx (+ idx-from len)))
                                                                    span))))]
                                    (when-let [coord (:coord clicked-span)]
                                      (if (:interesting? clicked-span)
                                        (let [clicked-coord-exprs (get interesting-coords coord)
                                              last-idx (get-in clicked-coord-exprs [(dec (count clicked-coord-exprs)) :idx])

                                              token-right-click-menu (ui-utils/make-context-menu
                                                                      (cond-> [{:text "Add to prints"
                                                                                :on-click #(add-to-printer flow-id thread-id (first clicked-coord-exprs))}]

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
                                                                                                                    :ns (:form/ns form)}))}])))]

                                          (if (= MouseButton/SECONDARY (.getButton mev))
                                            (.show token-right-click-menu
                                                   form-code-area
                                                   (.getScreenX mev)
                                                   (.getScreenY mev))
                                            (if (= 1 (count clicked-coord-exprs))
                                              (jump-to-coord flow-id thread-id (first clicked-coord-exprs))

                                              (let [make-menu-item (fn [{:keys [idx result]}]
                                                                     (let [v-str (:val-str (runtime-api/val-pprint rt-api result {:print-length 3 :print-level 3 :pprint? false}))]
                                                                       {:text (format "%s" (utils/elide-string v-str 80))
                                                                        :on-click #(jump-to-coord flow-id
                                                                                                  thread-id
                                                                                                  (runtime-api/timeline-entry rt-api flow-id thread-id idx :at))}))
                                                    ctx-menu-options (->> clicked-coord-exprs
                                                                          (map make-menu-item)
                                                                          (into [{:text "Goto Last Iteration"
                                                                                  :on-click #(jump-to-coord flow-id
                                                                                                            thread-id
                                                                                                            (runtime-api/timeline-entry rt-api flow-id thread-id last-idx :at))}]))
                                                    loop-traces-menu (ui-utils/make-context-menu ctx-menu-options)]
                                                (.show loop-traces-menu
                                                       form-code-area
                                                       (.getScreenX mev)
                                                       (.getScreenY mev))))))

                                        ;; else if it is not interesting? we don't want to jump there
                                        ;; but provide a way of search and jump to it by coord and form
                                        (let [form-id (:form/id form)
                                              curr-idx (dbg-state/current-idx flow-id thread-id)

                                              token-right-click-menu
                                              (ui-utils/make-context-menu
                                               [{:text "Jump to first record here"
                                                 :on-click (fn [] (jump-to-record-here flow-id thread-id form-id coord {:backward? false :from-idx 0}))}
                                                {:text "Jump forward here"
                                                 :on-click (fn [] (jump-to-record-here flow-id thread-id form-id coord {:backward? false :from-idx curr-idx}))}
                                                {:text "Jump backwards here"
                                                 :on-click (fn [] (jump-to-record-here flow-id thread-id form-id coord {:backward? true :from-idx curr-idx}))}])]

                                          (when (= MouseButton/SECONDARY (.getButton mev))
                                            (.show token-right-click-menu
                                                   form-code-area
                                                   (.getScreenX mev)
                                                   (.getScreenY mev)))))))))))))))
(defn- add-form

  "Pprints and adds a form to the flow and thread forms_box container."

  [form flow-id thread-id form-id]
  (let [print-tokens (binding [pp/*print-right-margin* 80]
                       (-> (form-pprinter/pprint-tokens (:form/form form))
                           ;; if it is a wrapped repl expression discard some tokens that the user
                           ;; isn't interested in
                           maybe-unwrap-runi-tokens))
        [forms-box] (obj-lookup flow-id thread-id "forms_box")
        code-text (form-pprinter/to-string print-tokens)
        ns-label (doto (label (if-let [form-line (some-> form :form/form meta :line)]
                                (format "%s:%d" (:form/ns form) form-line)
                                (:form/ns form)))
                   (.setFont (Font. 10)))

        form-header (doto (h-box [ns-label])
                          (.setAlignment (Pos/TOP_RIGHT)))

        ^CodeArea form-code-area (ui-utils/code-area {:editable? false
                                                      :text code-text})

        form-pane (v-box [form-header form-code-area] "form-pane")

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

(defn- locals-list-cell-factory [list-cell symb-val]
  (let [symb-lbl (doto (label (first symb-val))
                   (.setPrefWidth 100))
        val-lbl (label  (utils/elide-string (:val-str (runtime-api/val-pprint rt-api (second symb-val)
                                                                              {:print-length 20
                                                                               :print-level 5
                                                                               :pprint? false}))
                                            80))
        hbox (h-box [symb-lbl val-lbl])]
    (.setGraphic ^Node list-cell hbox)))

(defn- on-locals-list-item-click [flow-id thread-id mev selected-items {:keys [list-view-pane]}]
  (when (= MouseButton/SECONDARY (.getButton mev))
    (let [[_ val] (first selected-items)
          ctx-menu (ui-utils/make-context-menu [{:text "Define all frame vars"
                                                 :on-click (fn []
                                                             (let [curr-idx (dbg-state/current-idx flow-id thread-id)
                                                                   {:keys [fn-ns]} (dbg-state/current-frame flow-id thread-id)
                                                                   all-bindings (runtime-api/bindings rt-api flow-id thread-id curr-idx {:all-frame? true})]
                                                               (doseq [[symb-name vref] all-bindings]
                                                                 (let [symb (symbol fn-ns symb-name)]
                                                                   (runtime-api/def-value rt-api symb vref)))))}
                                                {:text "Define var for val"
                                                 :on-click (fn []
                                                             (value-inspector/def-val val))}
                                                {:text "Tap val"
                                                 :on-click (fn []
                                                             (runtime-api/tap-value rt-api val))}
                                                {:text "Inspect"
                                                 :on-click (fn []
                                                             (value-inspector/create-inspector val {:find-and-jump-same-val (partial find-and-jump-same-val flow-id thread-id)}))}])]
      (.show ctx-menu
             list-view-pane
             (.getScreenX mev)
             (.getScreenY mev)))))

(defn- create-locals-pane [flow-id thread-id]
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :selection-mode :single
                    :cell-factory-fn locals-list-cell-factory
                    :on-click (partial on-locals-list-item-click flow-id thread-id)})]
    (store-obj flow-id thread-id "locals_list" lv-data)

    list-view-pane))

(defn- update-locals-pane [flow-id thread-id bindings]
  (let [[{:keys [clear add-all]}] (obj-lookup flow-id thread-id "locals_list")]
    (clear)
    (add-all bindings)))

(defn- create-stack-pane [flow-id thread-id]
  (let [cell-factory (fn [list-cell {:keys [fn-ns fn-name form-def-kind dispatch-val]}]
                       (.setGraphic list-cell (label (if (= :defmethod form-def-kind)
                                                       (str fn-ns "/" fn-name " " dispatch-val)
                                                       (str fn-ns "/" fn-name))
                                                     "link-lbl")))
        item-click (fn [mev selected-items _]
                     (let [{:keys [fn-call-idx]} (first selected-items)]
                       (when (and (= MouseButton/PRIMARY (.getButton mev))
                                  (= 2 (.getClickCount mev)))
                         (jump-to-coord flow-id
                                        thread-id
                                        (runtime-api/timeline-entry rt-api flow-id thread-id fn-call-idx :at)))))
        {:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :selection-mode :single
                    :cell-factory-fn cell-factory
                    :on-click item-click})]
    (store-obj flow-id thread-id "stack_list" lv-data)

    list-view-pane))

(defn- update-frames-stack [flow-id thread-id fn-call-idx]
  (let [stack (runtime-api/stack-for-frame rt-api flow-id thread-id fn-call-idx)
        [{:keys [clear add-all]}] (obj-lookup flow-id thread-id "stack_list")]
    (clear)
    (add-all stack)))

(defn- update-thread-trace-count-lbl [flow-id thread-id cnt]
  (let [[^Label lbl] (obj-lookup flow-id thread-id "thread_trace_count_lbl")]
    (.setText lbl (str (dec cnt)))))

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
    (let [trace-count (runtime-api/timeline-count rt-api flow-id thread-id)
          curr-frame (if-let [cfr (dbg-state/current-frame flow-id thread-id)]
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
      (.setText curr-trace-text-field (str next-idx))
      (update-thread-trace-count-lbl flow-id thread-id trace-count)

      (when (or first-jump? changing-frame?)

        (un-highlight-form-tokens flow-id thread-id curr-form-id)

        (update-frames-stack flow-id thread-id next-fn-call-idx)

        (when (or first-jump? changing-form?)
          (unhighlight-form flow-id thread-id curr-form-id)
          (add-or-highlight-form flow-id thread-id next-form-id)))

      (highlight-interesting-form-tokens flow-id thread-id next-form-id next-frame next-tentry)

      ;; update reusult panel
      (flow-cmp/update-pprint-pane flow-id
                                   thread-id
                                   "expr_result"
                                   (:result next-tentry)
                                   {:find-and-jump-same-val (partial find-and-jump-same-val flow-id thread-id)})

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

(defn find-and-jump-same-val [flow-id thread-id curr-vref search-params backward?]
  (let [{:keys [idx]} (dbg-state/current-timeline-entry flow-id thread-id)]
    (when-let [next-tentry (runtime-api/find-timeline-entry rt-api (merge
                                                                    {:flow-id flow-id
                                                                     :thread-id thread-id
                                                                     :from-idx (if backward?
                                                                                 (dec idx)
                                                                                 (inc idx))
                                                                     :backward? backward?
                                                                     :eq-val-ref curr-vref}
                                                                    search-params))]
      (jump-to-coord flow-id thread-id next-tentry))))

(defn step-same-val [flow-id thread-id {:keys [fixed-coord?] :as search-params} backward?]
  (let [{:keys [result coord]} (dbg-state/current-timeline-entry flow-id thread-id)
        search-params (if fixed-coord?
                        (let [{:keys [form-id]} (dbg-state/current-frame flow-id thread-id)]
                          (assoc search-params
                                 :comp-fn-coord coord
                                 :comp-fn-form-id form-id))
                         search-params)]
    ;; hmm if the current entry is a fn-call then result will be nil and we will be following nils
    ;; not sure how to do about that, since custom stepping goes this path also
    ;; and doesn't work with the current expr result
    (find-and-jump-same-val flow-id thread-id result search-params backward?)))


(defn- power-stepping-pane [flow-id thread-id]
  (let [custom-expression-txt (text-field {:initial-text "(fn [v] v)"})
        show-custom-field (fn [show?]
                            (doto custom-expression-txt
                              (.setVisible show?)
                              (.setPrefWidth (if show? 200 0))))
        _ (show-custom-field false)
        step-type-combo (combo-box {:items ["identity" "equality" "same-coord" "custom" "custom-same-coord"]
                                    :on-change-fn (fn [_ new-val]
                                                    (case new-val
                                                      "identity"     (show-custom-field false)
                                                      "equality"     (show-custom-field false)
                                                      "same-coord"   (show-custom-field false)
                                                      "custom"       (show-custom-field true)
                                                      "custom-same-coord" (show-custom-field true)))})
        search-params (fn []
                        (let [step-type-val (-> step-type-combo .getSelectionModel .getSelectedItem)]
                          (case step-type-val
                            "identity"          {:comp-fn-key :identity}
                            "equality"          {:comp-fn-key :equality}
                            "same-coord"        {:comp-fn-key :same-coord
                                                 :fixed-coord? true}
                            "custom"            {:comp-fn-key :custom
                                                 :comp-fn-code (.getText custom-expression-txt)}
                            "custom-same-coord" {:comp-fn-key :custom
                                                 :comp-fn-code (.getText custom-expression-txt)
                                                 :fixed-coord? true})))
        val-prev-btn (ui-utils/icon-button :icon-name "mdi-ray-end-arrow"
                                           :on-click (fn [] (step-same-val flow-id thread-id (search-params) true))
                                           :tooltip "Find the prev expression that contains this value")
        val-next-btn (ui-utils/icon-button :icon-name "mdi-ray-start-arrow"
                                           :on-click (fn [] (step-same-val flow-id thread-id (search-params) false))
                                           :tooltip "Find the next expression that contains this value")

        power-stepping-pane (doto (h-box [val-prev-btn val-next-btn step-type-combo custom-expression-txt])
                              (.setSpacing 3))]
    power-stepping-pane))

(defn undo-jump [flow-id thread-id]
  (binding [dbg-state/*undo-redo-jump* true]
    (jump-to-coord flow-id thread-id (dbg-state/undo-nav-history flow-id thread-id))))

(defn redo-jump [flow-id thread-id]
  (binding [dbg-state/*undo-redo-jump* true]
    (jump-to-coord flow-id thread-id (dbg-state/redo-nav-history flow-id thread-id))))

(defn- trace-pos-pane [flow-id thread-id]
  (let [first-btn (ui-utils/icon-button :icon-name "mdi-page-first"
                                        :on-click (fn [] (step-first flow-id thread-id))
                                        :tooltip "Step to the first recorded expression")
        last-btn (ui-utils/icon-button :icon-name "mdi-page-last"
                                       :on-click (fn [] (step-last flow-id thread-id))
                                       :tooltip "Step to the last recorded expression")

        curr-trace-text-field (doto (text-field {:initial-text "0"
                                                 :on-return-key (fn [idx-str]
                                                                  (let [[forms-scroll-pane] (obj-lookup flow-id thread-id "forms_scroll")
                                                                        target-idx (Long/parseLong idx-str)
                                                                        target-tentry (runtime-api/timeline-entry rt-api flow-id thread-id target-idx :at)]
                                                                    (jump-to-coord flow-id thread-id target-tentry)
                                                                    (.requestFocus forms-scroll-pane)))
                                                 :align :right})
                                (.setPrefWidth 80))
        separator-lbl (label "/")
        thread-trace-count-lbl (label "?")]

    (store-obj flow-id thread-id "thread_curr_trace_tf" curr-trace-text-field)
    (store-obj flow-id thread-id "thread_trace_count_lbl" thread-trace-count-lbl)

    (doto (h-box [first-btn curr-trace-text-field separator-lbl thread-trace-count-lbl last-btn]
                 "trace-position-box")
      (.setSpacing 2.0))))

(defn- create-bookmarks-and-nav-pane [flow-id thread-id]
  (let [bookmark-btn (ui-utils/icon-button :icon-name "mdi-bookmark"
                                           :on-click (fn []
                                                       (bookmarks/bookmark-add
                                                        flow-id
                                                        thread-id
                                                        (dbg-state/current-idx flow-id thread-id)))
                                           :tooltip "Bookmark the current position")
        undo-nav-btn (ui-utils/icon-button :icon-name "mdi-undo"
                                           :on-click (fn [] (undo-jump flow-id thread-id))
                                           :tooltip "Undo navigation")
        redo-nav-btn (ui-utils/icon-button :icon-name "mdi-redo"
                                           :on-click (fn [] (redo-jump flow-id thread-id))
                                           :tooltip "Redo navigation")
        {:keys [flow/execution-expr]} (dbg-state/get-flow flow-id)

        execution-expression? (and (:ns execution-expr)
                                   (:form execution-expr))
        re-run-flow-btn (ui-utils/icon-button :icon-name "mdi-cached"
                                              :on-click (fn []
                                                          (when execution-expression?
                                                            (runtime-api/eval-form rt-api (:form execution-expr) {:instrument? false
                                                                                                                  :ns (:ns execution-expr)})))
                                              :disable (not execution-expression?))]
    (doto (h-box [undo-nav-btn redo-nav-btn
                  bookmark-btn
                  re-run-flow-btn])
      (.setSpacing 2.0))))


(defn- create-controls-first-row-pane [flow-id thread-id]
  (let [bookmarks-and-nav-pane (create-bookmarks-and-nav-pane flow-id thread-id)
        search-txt (doto (TextField.)
                     (.setPromptText "Search"))
        search-lvl-txt (doto (TextField. "2")
                         (.setPrefWidth 50)
                         (.setAlignment Pos/CENTER))
        search-len-txt (doto (TextField. "10")
                         (.setPrefWidth 50)
                         (.setAlignment Pos/CENTER))
        search-progress-lbl (label "")

        search-btn (ui-utils/icon-button :icon-name "mdi-magnify"
                                         :class "tree-search")
        search (fn []
                 (.setDisable search-btn true)
                 (.setText search-progress-lbl "% 0.0 %%")
                 (let [task-id (runtime-api/async-search-next-timeline-entry
                                rt-api
                                flow-id
                                thread-id
                                (.getText search-txt)
                                (inc (dbg-state/current-idx flow-id thread-id))
                                {:print-level (Integer/parseInt (.getText search-lvl-txt))
                                 :print-length (Integer/parseInt (.getText search-len-txt))})]

                   (subscribe-to-task-event :progress
                                            task-id
                                            (fn [progress-perc]
                                              (ui-utils/run-later
                                                (.setText search-progress-lbl (format "%.2f %%" (double progress-perc))))))

                   (subscribe-to-task-event :result
                                            task-id
                                            (fn [tl-entry]

                                              (if tl-entry

                                                (ui-utils/run-later
                                                  (.setText search-progress-lbl "")
                                                  (jump-to-coord flow-id thread-id tl-entry))

                                                (ui-utils/run-later (.setText search-progress-lbl "No match found")))

                                              (ui-utils/run-later (.setDisable search-btn false))))))]

    (.setOnAction search-btn (event-handler [_] (search)))

    (.setOnKeyReleased search-txt (event-handler
                                   [kev]
                                   (when (= (.getCode kev) KeyCode/ENTER)
                                     (search))))

    (border-pane {:left bookmarks-and-nav-pane
                  :right (doto (h-box [search-txt
                                       search-btn
                                       (label "*print-level* : ") search-lvl-txt
                                       (label "*print-length* : ") search-len-txt
                                       search-progress-lbl])
                           (.setSpacing 3.0))}
                 "thread-controls-pane")))

(defn- create-controls-second-row-pane [flow-id thread-id]
  (let [prev-over-btn (ui-utils/icon-button :icon-name "mdi-debug-step-over"
                                            :on-click (fn [] (step-prev-over flow-id thread-id))
                                            :tooltip "Step to the previous recorded interesting expression in the current frame"
                                            :mirrored? true)
        prev-btn (ui-utils/icon-button :icon-name "mdi-chevron-left"
                                       :on-click (fn [] (step-prev flow-id thread-id))
                                       :tooltip "Step to the previous recorded interesting expression")

        out-btn (ui-utils/icon-button :icon-name "mdi-debug-step-out"
                                      :on-click (fn []
                                                  (step-out flow-id thread-id))
                                      :tooltip "Step to the parent first expression")

        next-btn (ui-utils/icon-button :icon-name "mdi-chevron-right"
                                       :on-click (fn [] (step-next flow-id thread-id))
                                       :tooltip "Step to the next recorded interesting expression")
        next-over-btn (ui-utils/icon-button :icon-name "mdi-debug-step-over"
                                            :on-click (fn [] (step-next-over flow-id thread-id))
                                            :tooltip "Step to the next recorded interesting expression in the current frame")

        controls-box (doto (h-box [prev-over-btn prev-btn out-btn next-btn next-over-btn])
                       (.setSpacing 2.0))]

    (border-pane {:left controls-box
                  :center (trace-pos-pane flow-id thread-id)
                  :right (power-stepping-pane flow-id thread-id)}
                 "thread-controls-pane")))

(defn- create-forms-pane [flow-id thread-id]
  (let [forms-box (doto (v-box [])
                    (.setOnScroll (event-handler
                                   [ev]
                                   (when (or (.isAltDown ev) (.isControlDown ev))
                                     (.consume ev)
                                     (cond
                                       (> (.getDeltaY ev) 0) (step-prev flow-id thread-id)
                                       (< (.getDeltaY ev) 0) (step-next flow-id thread-id)))))
                    (.setSpacing 5))
        scroll-pane (ui-utils/scroll-pane "forms-scroll-container")
        controls-first-row-pane (create-controls-first-row-pane flow-id thread-id)
        controls-second-row-pane (create-controls-second-row-pane flow-id thread-id)
        outer-box (v-box [controls-first-row-pane
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
  (let [tools-tab-pane (doto (TabPane.)
                         (.setTabClosingPolicy TabPane$TabClosingPolicy/UNAVAILABLE))
        pprint-tab (doto (Tab.)
                     (.setGraphic (icon "mdi-code-braces"))
                     (.setContent (flow-cmp/create-pprint-pane flow-id thread-id "expr_result")))]
    (-> tools-tab-pane
        .getTabs
        (.addAll [pprint-tab]))

    tools-tab-pane))

(defn create-code-pane [flow-id thread-id]
  (let [left-right-pane (doto (SplitPane.)
                          (.setOrientation (Orientation/HORIZONTAL)))
        locals-result-pane (doto (SplitPane.)
                             (.setOrientation (Orientation/VERTICAL)))
        forms-pane (create-forms-pane flow-id thread-id)
        result-pane (create-result-pane flow-id thread-id)
        locals-stack-tab-pane (tab-pane {:tabs [(tab {:text "Locals"
                                                      :content (create-locals-pane flow-id thread-id)
                                                      :tooltip "Locals"})
                                                (tab {:text "Stack"
                                                      :content (create-stack-pane flow-id thread-id)
                                                      :tooltip "Locals"})]
                                         :side :top
                                         :closing-policy :unavailable})]

    (.setDividerPosition left-right-pane 0 0.6)

    (-> locals-result-pane
        .getItems
        (.addAll [result-pane locals-stack-tab-pane]))
    (-> left-right-pane
        .getItems
        (.addAll [forms-pane locals-result-pane]))
    left-right-pane))
