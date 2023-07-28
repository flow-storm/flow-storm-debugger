(ns flow-storm.debugger.ui.flows.code
  (:require [clojure.pprint :as pp]
            [flow-storm.form-pprinter :as form-pprinter]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon list-view text-field tab-pane tab combo-box border-pane]]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.state :as state]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [hansel.utils :refer [get-form-at-coord]])
  (:import [javafx.scene.control Label Tab TabPane TabPane$TabClosingPolicy SplitPane TextField TextInputDialog]
           [javafx.scene Node]
           [javafx.geometry Insets Orientation Pos]
           [javafx.scene.layout Priority VBox]
           [javafx.scene.text TextFlow Text Font]
           [javafx.scene.input KeyCode MouseEvent MouseButton]))

(declare jump-to-coord)
(declare find-and-jump-same-val)

(defn- maybe-unwrap-runi-tokens

  "Unwrap and discard the (fn* flowstorm-runi ([] <EXPR>)) wrapping added so we just show <EXPR>"

  [print-tokens]

  (if-let [runi-token-idx (some (fn [[i t]] (when (= "flowstorm-runi" (get t 0))
                                              i))
                                (map vector (range) (take 10 print-tokens)))]
    (let [wrap-beg (case runi-token-idx
                     3 9 ;; when it fits in one line
                     5 13) ;; when it render in multiple lines
          wrap-end (- (count print-tokens) 2)]
      (subvec print-tokens wrap-beg wrap-end))

    print-tokens))

(defn- add-form [flow-id thread-id form-id]
  (let [form (runtime-api/get-form rt-api form-id)
        print-tokens (binding [pp/*print-right-margin* 80]
                       (-> (form-pprinter/pprint-tokens (:form/form form))
                           ;; if it is a wrapped repl expression discard some tokens that the user
                           ;; isn't interested in
                           maybe-unwrap-runi-tokens))
        [forms-box] (obj-lookup flow-id thread-id "forms_box")
        tokens-texts (->> print-tokens
                          (map (fn [tok]
                                 (let [text (Text.
                                             (case tok
                                               :nl   "\n"
                                               :sp   " "
                                               (first tok)))
                                       _ (ui-utils/add-class text "code-token")
                                       coord (when (vector? tok) (second tok))]
                                   (when coord
                                     (store-obj flow-id thread-id (ui-vars/form-token-id form-id coord) text))
                                   text))))
        ns-label (doto (label (format "ns: %s" (:form/ns form)))
                   (.setFont (Font. 10)))

        form-header (doto (h-box [ns-label])
                      (.setAlignment (Pos/TOP_RIGHT)))
        form-text-flow (TextFlow. (into-array Text tokens-texts))

        form-pane (v-box [form-header form-text-flow] "form-pane")
        ]
    (store-obj flow-id thread-id (ui-vars/thread-form-box-id form-id) form-pane)

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
                                                             (let [curr-idx (state/current-idx flow-id thread-id)
                                                                   {:keys [fn-ns]} (state/current-frame flow-id thread-id)
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
  (let [cell-factory (fn [list-cell {:keys [fn-ns fn-name]}]
                       (.setGraphic list-cell (label (str fn-ns "/" fn-name) "link-lbl")))
        item-click (fn [mev selected-items _]
                     (let [{:keys [fn-call-idx]} (first selected-items)]
                       (when (= MouseButton/PRIMARY (.getButton mev))
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
    (.setText lbl (str cnt))))

(defn- highlight-executing [^Text token-text]
  (ui-utils/rm-class token-text "interesting")
  (ui-utils/add-class token-text "executing"))

(defn- highlight-interesting [^Text token-text]
  (ui-utils/rm-class token-text "executing")
  (ui-utils/add-class token-text "interesting"))

(defn- unhighlight-form [flow-id thread-id form-id]
  (let [[form-pane] (obj-lookup flow-id thread-id (ui-vars/thread-form-box-id form-id))]
    (when form-pane
      (doto ^Node form-pane
        (.setOnMouseClicked (event-handler [_])))
      (ui-utils/rm-class form-pane "form-background-highlighted"))))

(defn highlight-form [flow-id thread-id form-id]
  (let [form (runtime-api/get-form rt-api form-id)
        [form-pane]          (obj-lookup flow-id thread-id (ui-vars/thread-form-box-id form-id))
        [thread-scroll-pane] (obj-lookup flow-id thread-id "forms_scroll")

        ;; if the form we are about to highlight doesn't exist in the view add it first
        form-pane (or form-pane (add-form flow-id thread-id form-id))
        ctx-menu-options [{:text "Fully instrument this form"
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
                                                               :ns (:form/ns form)}))}]
        ctx-menu (ui-utils/make-context-menu ctx-menu-options)]

    (.setOnMouseClicked ^Node form-pane
                        (event-handler
                         [mev]
                         (when (and (= MouseButton/SECONDARY (.getButton mev))
                                    (not ui-vars/clojure-storm-env?))
                           (.show ctx-menu
                                  form-pane
                                  (.getScreenX mev)
                                  (.getScreenY mev)))))

    (ui-utils/enusure-node-visible-in-scroll-pane thread-scroll-pane form-pane)
    (ui-utils/add-class form-pane "form-background-highlighted")))

(defn- un-highlight [^Text token-text]
  (ui-utils/rm-class token-text "interesting")
  (ui-utils/rm-class token-text "executing")
  (.setOnMouseClicked token-text (event-handler [_])))

(defn- add-to-print [flow-id thread-id {:keys [coord fn-call-idx]}]
  (let [{:keys [form-id fn-ns fn-name]} (runtime-api/timeline-entry rt-api flow-id thread-id fn-call-idx :at)
        form (runtime-api/get-form rt-api form-id)
        expr (get-form-at-coord (:form/form form) coord)
        tdiag (doto (TextInputDialog.)
                (.setHeaderText "Printer message. %s will be replaced by the value.")
                (.setContentText "Message : "))
        _ (.showAndWait tdiag)
        format-str (-> tdiag .getEditor .getText)]
    (state/add-printer form-id coord (assoc (dissoc form :form/form)
                                            :fn-ns fn-ns
                                            :fn-name fn-name
                                            :coord coord
                                            :expr expr
                                            :format-str format-str
                                            :print-length 5
                                            :print-level  3
                                            :enable? true))))

(defn- arm-interesting [flow-id thread-id ^Text token-text traces]
  (let [token-right-click-menu (ui-utils/make-context-menu
                                [{:text "Add to prints"
                                  :on-click #(add-to-print flow-id thread-id (first traces))}])]
    (if (> (count traces) 1)
      ;; loop expression
      (let [last-idx (get-in traces [(dec (count traces)) :idx])
            make-menu-item (fn [{:keys [idx result]}]
                             (let [v-str (:val-str (runtime-api/val-pprint rt-api result {:print-length 3 :print-level 3 :pprint? false}))]
                               {:text (format "%s" (utils/elide-string v-str 80))
                                :on-click #(jump-to-coord flow-id
                                                          thread-id
                                                          (runtime-api/timeline-entry rt-api flow-id thread-id idx :at))}))
            ctx-menu-options (->> traces
                                  (map make-menu-item)
                                  (into [{:text "Goto Last Iteration"
                                          :on-click #(jump-to-coord flow-id
                                                                    thread-id
                                                                    (runtime-api/timeline-entry rt-api flow-id thread-id last-idx :at))}]))
            loop-traces-menu (ui-utils/make-context-menu ctx-menu-options)]
        (.setOnMouseClicked token-text (event-handler
                                        [^MouseEvent mev]
                                        (if (= MouseButton/SECONDARY (.getButton mev))
                                          (.show token-right-click-menu
                                                 token-text
                                                 (.getScreenX mev)
                                                 (.getScreenY mev))
                                          (.show loop-traces-menu
                                                 token-text
                                                 (.getScreenX mev)
                                                 (.getScreenY mev))))))

      ;; single expression
      (.setOnMouseClicked token-text (event-handler
                                      [mev]
                                      (.consume mev)
                                      (if (= MouseButton/SECONDARY (.getButton mev))
                                        (.show token-right-click-menu
                                                 token-text
                                                 (.getScreenX mev)
                                                 (.getScreenY mev))
                                        (jump-to-coord flow-id
                                                       thread-id
                                                       (runtime-api/timeline-entry rt-api flow-id thread-id (-> traces first :idx) :at))))))))

(defn un-highlight-form-tokens [flow-id thread-id form-id]
  (let [token-texts (ui-vars/form-tokens flow-id thread-id form-id)]
    (doseq [text token-texts]
      (un-highlight text))))

(defn remove-exec-mark-tokens [flow-id thread-id form-id coord leave-unmarked?]
  (let [curr-token-texts (obj-lookup flow-id thread-id (ui-vars/form-token-id form-id coord))]
    (doseq [text curr-token-texts]
      (if leave-unmarked?
        (highlight-interesting text)
        (un-highlight text)))))

(defn highlight-exec-mark-tokens [flow-id thread-id form-id coord]
  (let [[thread-scroll-pane] (obj-lookup flow-id thread-id "forms_scroll")
        next-token-texts (obj-lookup flow-id thread-id (ui-vars/form-token-id form-id coord))]

    (when-let [text (first next-token-texts)]
      (ui-utils/enusure-node-visible-in-scroll-pane thread-scroll-pane text))

    (doseq [text next-token-texts]
      (highlight-executing text))))

(defn arm-and-highlight-interesting-form-tokens [flow-id thread-id next-form-id next-fn-call-idx]
  (let [{:keys [expr-executions]} (runtime-api/frame-data rt-api flow-id thread-id next-fn-call-idx {:include-exprs? true})
        next-exec-expr (->> expr-executions
                            (group-by :coord))]

    (doseq [[coord traces] next-exec-expr]
      (let [token-id (ui-vars/form-token-id next-form-id coord)
            token-texts (obj-lookup flow-id thread-id token-id)]
        (doseq [text token-texts]
          (arm-interesting flow-id thread-id text traces)
          (highlight-interesting text))))))


(defn jump-to-coord [flow-id thread-id next-tentry]
  (try
    (let [trace-count (runtime-api/timeline-count rt-api flow-id thread-id)
          curr-frame (if-let [cfr (state/current-frame flow-id thread-id)]
                       cfr
                       ;; if we don't have a current frame it means it is the first
                       ;; jump so, initialize the debugger thread state
                       (let [first-frame (runtime-api/frame-data rt-api flow-id thread-id 0 {})
                             first-tentry (runtime-api/timeline-entry rt-api flow-id thread-id 0 :at)]
                         (state/set-current-frame flow-id thread-id first-frame)
                         (state/set-current-timeline-entry flow-id thread-id first-tentry)
                         first-frame))

          curr-tentry (state/current-timeline-entry flow-id thread-id)
          curr-idx (:idx curr-tentry)
          next-idx (:idx next-tentry)

          curr-fn-call-idx (:fn-call-idx curr-frame)
          next-fn-call-idx (:fn-call-idx next-tentry)
          changing-frame? (not= curr-fn-call-idx next-fn-call-idx)
          next-frame (if changing-frame?
                       (let [nfr (runtime-api/frame-data rt-api flow-id thread-id next-fn-call-idx {})]
                         nfr)
                       curr-frame)
          curr-form-id (:form-id curr-frame)
          next-form-id (:form-id next-frame)

          [curr-trace-text-field] (obj-lookup flow-id thread-id "thread_curr_trace_tf")
          ;; because how frames are cached by trace, their pointers can't be compared
          ;; so a content comparision is needed. Comparing :fn-call-idx is enough since it is
          ;; a frame
          first-jump? (and (zero? curr-idx) (zero? next-idx))
          changing-form? (not= curr-form-id next-form-id)]

      ;; update thread current trace label and total traces
      (.setText curr-trace-text-field (str (inc next-idx)))
      (update-thread-trace-count-lbl flow-id thread-id trace-count)

      (when first-jump?
        (highlight-form flow-id thread-id next-form-id))

      (when (or first-jump? changing-frame?)

        (update-frames-stack flow-id thread-id next-fn-call-idx)

        (when (or first-jump? changing-form?)
          (unhighlight-form flow-id thread-id curr-form-id)
          (highlight-form flow-id thread-id next-form-id))

        (un-highlight-form-tokens flow-id thread-id curr-form-id)

        (arm-and-highlight-interesting-form-tokens flow-id thread-id next-form-id next-fn-call-idx))

      (when (and (not first-jump?) (or (= :expr (:type curr-tentry))
                                       (= :fn-return (:type curr-tentry))))
        (remove-exec-mark-tokens flow-id thread-id curr-form-id (:coord curr-tentry) (not changing-form?)))

      (when (or (= :expr (:type next-tentry))
                (= :fn-return (:type next-tentry)))
        (highlight-exec-mark-tokens flow-id thread-id next-form-id (:coord next-tentry)))

      ;; update reusult panel
      (flow-cmp/update-pprint-pane flow-id
                                   thread-id
                                   "expr_result"
                                   (:result next-tentry)
                                   {:find-and-jump-same-val (partial find-and-jump-same-val flow-id thread-id)})

      ;; update locals panel
      (update-locals-pane flow-id thread-id (runtime-api/bindings rt-api flow-id thread-id next-idx {}))

      (when changing-frame?
        (state/set-current-frame flow-id thread-id next-frame))

      (state/set-current-timeline-entry flow-id thread-id next-tentry))
    (catch Throwable e
      (utils/log-error (str "Error jumping into " flow-id " " thread-id " " next-tentry) e))))

(defn step-prev [flow-id thread-id]
  (let [curr-idx (state/current-idx flow-id thread-id)
        prev-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :prev)]
    (jump-to-coord flow-id thread-id prev-tentry)))

(defn step-next [flow-id thread-id]
  (let [curr-idx (state/current-idx flow-id thread-id)
        next-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :next)]
    (jump-to-coord flow-id thread-id next-tentry)))

(defn step-next-over [flow-id thread-id]
  (let [curr-idx (state/current-idx flow-id thread-id)
        next-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :next-over)]
    (jump-to-coord flow-id thread-id next-tentry)))

(defn step-prev-over [flow-id thread-id]
  (let [curr-idx (state/current-idx flow-id thread-id)
        prev-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx :prev-over)]
    (jump-to-coord flow-id thread-id prev-tentry)))

(defn step-out [flow-id thread-id]
  (let [curr-idx (state/current-idx flow-id thread-id)
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
  (let [{:keys [idx]} (state/current-timeline-entry flow-id thread-id)]
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

(defn step-same-val [flow-id thread-id search-params backward?]
  (let [{:keys [type result]} (state/current-timeline-entry flow-id thread-id)]
    (when (#{:expr :fn-return} type)
      (find-and-jump-same-val flow-id thread-id result search-params backward?))))


(defn- power-stepping-pane [flow-id thread-id]
  (let [custom-expression-txt (doto (text-field {:initial-text "(fn [v] v)"})
                                (.setVisible false))
        step-type-combo (combo-box {:items ["identity" "equality" "custom"]
                                    :on-change-fn (fn [_ new-val]
                                                    (case new-val
                                                      "identity" (.setVisible custom-expression-txt false)
                                                      "equality" (.setVisible custom-expression-txt false)
                                                      "custom"   (.setVisible custom-expression-txt true)))})
        search-params (fn []
                        (let [step-type-val (-> step-type-combo .getSelectionModel .getSelectedItem)]
                          (case step-type-val
                            "identity" {:comp-fn-key :identity}
                            "equality" {:comp-fn-key :equality}
                            "custom"   {:comp-fn-key :custom
                                        :comp-fn-code (.getText custom-expression-txt)})))
        val-prev-btn (ui-utils/icon-button :icon-name "mdi-ray-end-arrow"
                                           :on-click (fn [] (step-same-val flow-id thread-id (search-params) true))
                                           :tooltip "Find the prev expression that contains this value")
        val-next-btn (ui-utils/icon-button :icon-name "mdi-ray-start-arrow"
                                           :on-click (fn [] (step-same-val flow-id thread-id (search-params) false))
                                           :tooltip "Find the next expression that contains this value")

        power-stepping-pane (doto (h-box [val-prev-btn val-next-btn step-type-combo custom-expression-txt])
                              (.setSpacing 3))]
    power-stepping-pane))

(defn- create-thread-controls-pane [flow-id thread-id]
  (let [first-btn (ui-utils/icon-button :icon-name "mdi-page-first"
                                        :on-click (fn [] (step-first flow-id thread-id))
                                        :tooltip "Step to the first recorded expression")
        prev-over-btn (ui-utils/icon-button :icon-name "mdi-step-backward"
                                       :on-click (fn [] (step-prev-over flow-id thread-id))
                                       :tooltip "Step to the previous recorded interesting expression in the current frame")
        prev-btn (ui-utils/icon-button :icon-name "mdi-chevron-left"
                                       :on-click (fn [] (step-prev flow-id thread-id))
                                       :tooltip "Step to the previous recorded interesting expression")

        out-btn (ui-utils/icon-button :icon-name "mdi-debug-step-out"
                                      :on-click (fn []
                                                  (step-out flow-id thread-id))
                                      :tooltip "Step to the parent first expression")

        curr-trace-text-field (doto (text-field {:initial-text "1"
                                                 :on-return-key (fn [idx-str]
                                                                  (let [target-idx (dec (Long/parseLong idx-str))
                                                                        target-tentry (runtime-api/timeline-entry rt-api flow-id thread-id target-idx :at)]
                                                                    (jump-to-coord flow-id thread-id target-tentry)))
                                                 :align :right})
                                (.setPrefWidth 80))

        separator-lbl (label "/")
        thread-trace-count-lbl (label "?")
        _ (store-obj flow-id thread-id "thread_curr_trace_tf" curr-trace-text-field)
        _ (store-obj flow-id thread-id "thread_trace_count_lbl" thread-trace-count-lbl)
        {:keys [flow/execution-expr]} (state/get-flow flow-id)
        execution-expression? (and (:ns execution-expr)
                                   (:form execution-expr))
        next-btn (ui-utils/icon-button :icon-name "mdi-chevron-right"
                                       :on-click (fn [] (step-next flow-id thread-id))
                                       :tooltip "Step to the next recorded interesting expression")
        next-over-btn (ui-utils/icon-button :icon-name "mdi-step-forward"
                                            :on-click (fn [] (step-next-over flow-id thread-id))
                                            :tooltip "Step to the next recorded interesting expression in the current frame")


        last-btn (ui-utils/icon-button :icon-name "mdi-page-last"
                                       :on-click (fn [] (step-last flow-id thread-id))
                                       :tooltip "Step to the last recorded expression")


        re-run-flow-btn (ui-utils/icon-button :icon-name "mdi-cached"
                                              :on-click (fn []
                                                          (when execution-expression?
                                                            (runtime-api/eval-form rt-api (:form execution-expr) {:instrument? false
                                                                                                                  :ns (:ns execution-expr)})))
                                              :disable (not execution-expression?))


        trace-pos-box (doto (h-box [curr-trace-text-field separator-lbl thread-trace-count-lbl] "trace-position-box")
                        (.setSpacing 2.0))
        controls-box (doto (h-box [first-btn prev-over-btn prev-btn out-btn re-run-flow-btn next-btn next-over-btn last-btn])
                       (.setSpacing 2.0))]

    (border-pane {:left controls-box
                  :center trace-pos-box
                  :right (power-stepping-pane flow-id thread-id)}
                 "thread-controls-pane")
    ))

(defn- create-search-pane [flow-id thread-id]
  (let [search-txt (doto (TextField.)
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
                                (inc (state/current-idx flow-id thread-id))
                                {:print-level (Integer/parseInt (.getText search-lvl-txt))
                                 :print-length (Integer/parseInt (.getText search-len-txt))})]

                   (ui-vars/subscribe-to-task-event :progress
                                                    task-id
                                                    (fn [progress-perc]
                                                      (ui-utils/run-later
                                                       (.setText search-progress-lbl (format "%.2f %%" (double progress-perc))))))

                   (ui-vars/subscribe-to-task-event :result
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

    (doto (h-box [search-txt
                  (label "*print-level* : ") search-lvl-txt
                  (label "*print-length* : ") search-len-txt
                  search-btn
                  search-progress-lbl])
      (.setSpacing 3.0)
      (.setPadding (Insets. 4.0)))))

(defn- create-forms-pane [flow-id thread-id]
  (let [box (doto (v-box [])
              (.setOnScroll (event-handler
                             [ev]
                             (when (or (.isAltDown ev) (.isControlDown ev))
                               (.consume ev)
                               (cond
                                 (> (.getDeltaY ev) 0) (step-prev flow-id thread-id)
                                 (< (.getDeltaY ev) 0) (step-next flow-id thread-id)))))
              (.setSpacing 5))
        scroll-pane (ui-utils/scroll-pane "forms-scroll-container")
        controls-pane (create-thread-controls-pane flow-id thread-id)
        search-pane (create-search-pane flow-id thread-id)
        outer-box (v-box [controls-pane search-pane scroll-pane])]
    (VBox/setVgrow box Priority/ALWAYS)
    (VBox/setVgrow scroll-pane Priority/ALWAYS)
    (.setContent scroll-pane box)
    (store-obj flow-id thread-id "forms_box" box)
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
