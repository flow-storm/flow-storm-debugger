(ns flow-storm.debugger.ui.flows.code
  (:require [clojure.pprint :as pp]
            [flow-storm.debugger.form-pprinter :as form-pprinter]
            [flow-storm.debugger.ui.flows.components :as flow-cmp]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon list-view]]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.state :as state]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]])
  (:import [javafx.scene.control Label Tab TabPane TabPane$TabClosingPolicy SplitPane]
           [javafx.scene Node]
           [javafx.geometry Orientation Pos]
           [javafx.scene.text TextFlow Text Font]
           [javafx.scene.input MouseEvent MouseButton]))

(declare jump-to-coord)

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
  (let [form (runtime-api/get-form rt-api flow-id thread-id form-id)
        print-tokens (binding [pp/*print-right-margin* 80]
                       (-> (form-pprinter/pprint-tokens (:form/form form))
                           ;; if it is a wrapped repl expression discard some tokens that the user
                           ;; isn't interested in
                           maybe-unwrap-runi-tokens))
        [forms-box] (obj-lookup flow-id (ui-vars/thread-forms-box-id thread-id))
        tokens-texts (->> print-tokens
                          (map (fn [tok]
                                 (let [text (Text.
                                             (case tok
                                               :nl   "\n"
                                               :sp   " "
                                               (first tok)))
                                       _ (ui-utils/add-class text "code-token")
                                       coord (when (vector? tok) (second tok))]
                                   (store-obj flow-id (ui-vars/form-token-id thread-id form-id coord) text)
                                   text))))
        ns-label (doto (label (format "ns: %s" (:form/ns form)))
                   (.setFont (Font. 10)))

        form-header (doto (h-box [ns-label])
                      (.setAlignment (Pos/TOP_RIGHT)))
        form-text-flow (TextFlow. (into-array Text tokens-texts))

        form-pane (v-box [form-header form-text-flow] "form-pane")
        ]
    (store-obj flow-id (ui-vars/thread-form-box-id thread-id form-id) form-pane)

    (-> forms-box
        .getChildren
        (.add 0 form-pane))

    form-pane))

(defn- locals-list-cell-factory [list-cell symb-val]
  (let [symb-lbl (doto (label (first symb-val))
                   (.setPrefWidth 100))
        val-lbl (label  (utils/elide-string (runtime-api/val-pprint rt-api (second symb-val)
                                                                    {:print-length 20
                                                                     :print-level 5
                                                                     :pprint? false})
                                            80))
        hbox (h-box [symb-lbl val-lbl])]
    (.setGraphic ^Node list-cell hbox)))

(defn- on-locals-list-item-click [mev selected-items {:keys [list-view-pane]}]
  (when (= MouseButton/SECONDARY (.getButton mev))
    (let [[_ val] (first selected-items)
          ctx-menu (ui-utils/make-context-menu [{:text "Define var for val"
                                                 :on-click (fn []
                                                             (value-inspector/def-val val))}
                                                {:text "Inspect"
                                                 :on-click (fn []
                                                             (value-inspector/create-inspector val))}])]
      (.show ctx-menu
             list-view-pane
             (.getScreenX mev)
             (.getScreenY mev)))))

(defn- create-locals-pane [flow-id thread-id]
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :selection-mode :single
                    :cell-factory-fn locals-list-cell-factory
                    :on-click on-locals-list-item-click})]
    (store-obj flow-id (ui-vars/thread-locals-list-view-data thread-id) lv-data)

    list-view-pane))

(defn- update-locals-pane [flow-id thread-id bindings]
  (let [[{:keys [clear add-all]}] (obj-lookup flow-id (ui-vars/thread-locals-list-view-data thread-id))]
    (clear)
    (add-all bindings)))

(defn- update-thread-trace-count-lbl [flow-id thread-id cnt]
  (let [[^Label lbl] (obj-lookup flow-id (ui-vars/thread-trace-count-lbl-id thread-id))]
    (.setText lbl (str cnt))))

(defn- highlight-executing [token-text]
  (ui-utils/rm-class token-text "interesting")
  (ui-utils/add-class token-text "executing"))

(defn- highlight-interesting [token-text]
  (ui-utils/rm-class token-text "executing")
  (ui-utils/add-class token-text "interesting"))

(defn- unhighlight-form [flow-id thread-id form-id]
  (let [[form-pane] (obj-lookup flow-id (ui-vars/thread-form-box-id thread-id form-id))]
    (doto form-pane
      (.setOnMouseClicked (event-handler [_])))
    (ui-utils/rm-class form-pane "form-background-highlighted")))

(defn highlight-form [flow-id thread-id form-id]
  (let [form (runtime-api/get-form rt-api flow-id thread-id form-id)
        [form-pane]          (obj-lookup flow-id (ui-vars/thread-form-box-id thread-id form-id))
        [thread-scroll-pane] (obj-lookup flow-id (ui-vars/thread-forms-scroll-id thread-id))

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
                                                               :instrument-options {:disable #{:binding}}
                                                               :ns (:form/ns form)}))}]
        ctx-menu (ui-utils/make-context-menu ctx-menu-options)]

    (.setOnMouseClicked form-pane
                        (event-handler
                         [mev]
                         (when (= MouseButton/SECONDARY (.getButton mev))
                           (.show ctx-menu
                                  form-pane
                                  (.getScreenX mev)
                                  (.getScreenY mev)))))


    (ui-utils/center-node-in-scroll-pane thread-scroll-pane form-pane)
    (ui-utils/add-class form-pane "form-background-highlighted")))

(defn- un-highlight [^Text token-text]
  (ui-utils/rm-class token-text "interesting")
  (ui-utils/rm-class token-text "executing")
  (.setOnMouseClicked token-text (event-handler [_])))

(defn- arm-interesting [flow-id thread-id ^Text token-text traces]
  (if (> (count traces) 1)
    (let [last-idx (get-in traces [(dec (count traces)) :idx])
          make-menu-item (fn [{:keys [idx result]}]
                          (let [v-str (runtime-api/val-pprint rt-api result {:print-length 3 :print-level 3 :pprint? false})]
                            {:text (format "%s" (utils/elide-string v-str 80))
                             :on-click #(jump-to-coord flow-id thread-id idx)}))
          ctx-menu-options (->> traces
                                (map make-menu-item)
                                (into [{:text "Goto Last Iteration"
                                        :on-click #(jump-to-coord flow-id thread-id last-idx)}]))
          ctx-menu (ui-utils/make-context-menu ctx-menu-options)]
      (.setOnMouseClicked token-text (event-handler
                                      [^MouseEvent ev]
                                      (.show ctx-menu
                                             token-text
                                             (.getScreenX ev)
                                             (.getScreenY ev)))))

    (.setOnMouseClicked token-text (event-handler
                                    [ev]
                                    (jump-to-coord flow-id thread-id (-> traces first :idx))))))

(defn jump-to-coord [flow-id thread-id next-idx]
  (let [trace-count (runtime-api/timeline-count rt-api flow-id thread-id)]
    (when (<= 0 next-idx (dec trace-count))
      (let [curr-idx (state/current-idx flow-id thread-id)
            curr-tentry (runtime-api/timeline-entry rt-api flow-id thread-id curr-idx)
            curr-form-id (:form-id curr-tentry)
            next-tentry (runtime-api/timeline-entry rt-api flow-id thread-id next-idx)
            next-form-id (:form-id next-tentry)
            [curr-trace-text-field] (obj-lookup flow-id (ui-vars/thread-curr-trace-tf-id thread-id))
            ;; because how frames are cached by trace, their pointers can't be compared
            ;; so a content comparision is needed. Comparing :frame-idx is enough since it is
            ;; a frame
            first-jump? (and (zero? curr-idx) (zero? next-idx))
            changing-frame? (or first-jump?
                                (not= (:frame-idx (runtime-api/frame-data rt-api flow-id thread-id curr-idx))
                                      (:frame-idx (runtime-api/frame-data rt-api flow-id thread-id next-idx))))
            changing-form? (or first-jump?
                               (not= curr-form-id next-form-id))]

        ;; update thread current trace label and total traces
        (.setText curr-trace-text-field (str (inc next-idx)))
        (update-thread-trace-count-lbl flow-id thread-id trace-count)

        (when changing-form?
          ;; we are leaving a form with this jump, so unhighlight all curr-form interesting tokens
          (when-not first-jump?
            (let [{:keys [expr-executions]} (runtime-api/frame-data rt-api flow-id thread-id curr-idx)]
              (unhighlight-form flow-id thread-id curr-form-id)
              (doseq [{:keys [coor]} expr-executions]
                (let [token-texts (obj-lookup flow-id (ui-vars/form-token-id thread-id curr-form-id coor))]
                  (doseq [text token-texts]
                    (un-highlight text))))))

          (highlight-form flow-id thread-id next-form-id))

        (when changing-frame?
          ;; we are leaving a frame with this jump, or its the first trace
          ;; highlight all interesting tokens for the form we are currently in
          (let [{:keys [expr-executions]} (runtime-api/frame-data rt-api flow-id thread-id next-idx)
                next-exec-expr (->> expr-executions
                                    (group-by :coor))]

            (doseq [[coor traces] next-exec-expr]
              (let [token-id (ui-vars/form-token-id thread-id next-form-id coor)
                    token-texts (obj-lookup flow-id token-id)]
                (doseq [text token-texts]
                  (arm-interesting flow-id thread-id text traces)
                  (highlight-interesting text))))))

        ;; "unhighlight" prev executing tokens
        (when (and (= :expr (:timeline/type curr-tentry))
                   (not first-jump?))
          (let [curr-token-texts (obj-lookup flow-id (ui-vars/form-token-id thread-id
                                                                            (:form-id curr-tentry)
                                                                            (:coor curr-tentry)))]
            (doseq [text curr-token-texts]
              (if (= curr-form-id next-form-id)
                (highlight-interesting text)
                (un-highlight text)))))

        ;; highlight executing tokens
        (when (= :expr (:timeline/type next-tentry))
          (let [next-token-texts (obj-lookup flow-id (ui-vars/form-token-id thread-id
                                                                            (:form-id next-tentry)
                                                                            (:coor next-tentry)))]
            (doseq [text next-token-texts]
              (highlight-executing text))))

        ;; update reusult panel
        (flow-cmp/update-pprint-pane flow-id thread-id "expr_result" (:result next-tentry))

        ;; update locals panel
        (update-locals-pane flow-id thread-id (runtime-api/bindings rt-api flow-id thread-id next-idx))

        (state/set-idx flow-id thread-id next-idx)))))

(defn- create-forms-pane [flow-id thread-id]
  (let [box (doto (v-box [])
              (.setSpacing 5))
        scroll-pane (ui-utils/scroll-pane "forms-scroll-container")]
    (.setContent scroll-pane box)
    (store-obj flow-id (ui-vars/thread-forms-box-id thread-id) box)
    (store-obj flow-id (ui-vars/thread-forms-scroll-id thread-id) scroll-pane)
    scroll-pane))

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
        locals-pane (create-locals-pane flow-id thread-id)]

    (-> locals-result-pane
        .getItems
        (.addAll [result-pane locals-pane]))
    (-> left-right-pane
        .getItems
        (.addAll [forms-pane locals-result-pane]))
    left-right-pane))
