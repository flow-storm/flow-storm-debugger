(ns flow-storm.debugger.ui.browser.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon button add-class]]
            [flow-storm.utils :refer [log-error]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.target-commands :as target-commands]
            [clojure.string :as str])
  (:import [javafx.scene.control CheckBox Label ListView SplitPane TextField SelectionMode]
           [javafx.scene Node]
           [ javafx.beans.value ChangeListener]
           [javafx.collections FXCollections]
           [javafx.collections.transformation FilteredList]
           [javafx.scene.layout HBox Priority VBox]
           [javafx.geometry Orientation Pos]
           [java.util.function Predicate]
           [javafx.scene.input MouseButton]))

(defn make-inst-var [var-ns var-name]
  {:inst-type :var
   :var-ns var-ns
   :var-name var-name})

(defn make-inst-ns
  ([ns-name] (make-inst-ns ns-name nil))
  ([ns-name profile]
   {:inst-type :ns
    :profile profile
    :ns-name ns-name}))

(defn add-to-var-instrumented-list [var-ns var-name]
  (let [[observable-instrumentations-list] (obj-lookup "browser-observable-instrumentations-list")]
    (.addAll observable-instrumentations-list (into-array Object [(make-inst-var var-ns var-name)]))))

(defn remove-from-var-instrumented-list [var-ns var-name]
  (let [[observable-instrumentations-list] (obj-lookup "browser-observable-instrumentations-list")]
    (.removeAll observable-instrumentations-list (into-array Object [(make-inst-var var-ns var-name)]))))

(defn- instrument-function [var-ns var-name add?]

  (target-commands/run-command :instrument-fn {:fn-symb (symbol var-ns var-name)})

  (when add?
    (add-to-var-instrumented-list var-ns var-name)))

(defn- uninstrument-function [var-ns var-name remove?]

  (target-commands/run-command :uninstrument-fns {:vars-symbs [(symbol var-ns var-name)]})

  (when remove?
    (remove-from-var-instrumented-list var-ns var-name)))

(defn add-to-namespace-instrumented-list [ns-maps]
  (let [[observable-instrumentations-list] (obj-lookup "browser-observable-instrumentations-list")]
    (.addAll observable-instrumentations-list (into-array Object ns-maps))))

(defn remove-from-namespace-instrumented-list [ns-maps]
  (let [[observable-instrumentations-list] (obj-lookup "browser-observable-instrumentations-list")]
    (.removeAll observable-instrumentations-list (into-array Object ns-maps))))

(defn- instrument-namespaces [inst-namespaces add?]
  (target-commands/run-command
   :instrument-namespaces
   {:ns-names (map :ns-name inst-namespaces)
    :profile (:profile (first inst-namespaces))})

  (when add?
    (add-to-namespace-instrumented-list inst-namespaces)))

(defn- uninstrument-namespaces [inst-namespaces remove?]
  (target-commands/run-command
   :uninstrument-namespaces
   {:ns-names (map :ns-name inst-namespaces)})

  (when remove?
    (remove-from-namespace-instrumented-list inst-namespaces)))

(defn- update-selected-fn-detail-pane [{:keys [added ns name file static line arglists doc]}]
  (let [[browser-instrument-button]   (obj-lookup "browser-instrument-button")
        [selected-fn-fq-name-label]   (obj-lookup "browser-selected-fn-fq-name-label")
        [selected-fn-added-label]     (obj-lookup "browser-selected-fn-added-label")
        [selected-fn-file-label]      (obj-lookup "browser-selected-fn-file-label")
        [selected-fn-static-label]    (obj-lookup "browser-selected-fn-static-label")
        [selected-fn-args-list-v-box] (obj-lookup "browser-selected-fn-args-list-v-box")
        [selected-fn-doc-label]       (obj-lookup "browser-selected-fn-doc-label")

        args-lists-labels (map (fn [al] (label (str al))) arglists)]

    (add-class browser-instrument-button "enable")
    (.setOnAction browser-instrument-button (event-handler [_] (instrument-function ns name true)))
    (.setText selected-fn-fq-name-label (format "%s" #_ns name))
    (when added
      (.setText selected-fn-added-label (format "Added: %s" added)))
    (.setText selected-fn-file-label (format "File: %s:%d" file line))
    (when static
      (.setText selected-fn-static-label "Static: true"))
    (-> selected-fn-args-list-v-box .getChildren .clear)
    (.addAll (.getChildren selected-fn-args-list-v-box)
             (into-array Node args-lists-labels))
    (.setText selected-fn-doc-label doc)))

(defn- update-vars-pane [vars]
  (let [[observable-vars-list] (obj-lookup "browser-observable-vars-list")]
    (.clear observable-vars-list)
    (.addAll observable-vars-list (into-array Object (sort-by :var-name vars)))))

(defn- update-namespaces-pane [namespaces]
  (let [[observable-namespaces-list] (obj-lookup "browser-observable-namespaces-list")]
    (.clear observable-namespaces-list)
    (.addAll observable-namespaces-list (into-array String (sort namespaces)))))

(defn get-var-meta [{:keys [var-name var-ns]}]
  (target-commands/run-command :get-var-meta
                               {:var-ns var-ns :var-name var-name}
                               (fn [var-meta]
                                 (ui-utils/run-later (update-selected-fn-detail-pane var-meta)))))

(defn- get-all-vars-for-ns [ns-name]
  (target-commands/run-command :get-all-vars-for-ns
                               {:ns-name ns-name}
                               (fn [all-vars]
                                 (ui-utils/run-later (update-vars-pane (->> all-vars
                                                                            (map #(assoc % :inst-type :var))))))))

(defn get-all-namespaces []
  (target-commands/run-command :get-all-namespaces
                               {}
                               (fn [all-namespaces]
                                 (ui-utils/run-later (update-namespaces-pane all-namespaces)))))

(defn create-namespaces-pane []
  (let [observable-namespaces-list (FXCollections/observableArrayList)
        search-field (TextField.)
        observable-namespaces-filtered-list (FilteredList. observable-namespaces-list)
        search-bar (h-box [search-field (doto (Label.)
                                          (.setGraphic (icon "mdi-magnify")))])
        namespaces-list-view (doto (ListView. observable-namespaces-filtered-list)
                               (.setEditable false))
        namespaces-list-selection (.getSelectionModel namespaces-list-view)
        _ (.setSelectionMode namespaces-list-selection SelectionMode/MULTIPLE)
        pane (v-box [search-bar namespaces-list-view])

        ]

    (HBox/setHgrow search-field Priority/ALWAYS)
    (VBox/setVgrow namespaces-list-view Priority/ALWAYS)

    (.addListener (.textProperty search-field)
                  (proxy [ChangeListener] []
                    (changed [_ _ new-val]
                      (.setPredicate observable-namespaces-filtered-list
                                     (proxy [Predicate] []
                                       (test [ns-name]
                                         (str/includes? ns-name new-val)))))))

    (.setOnMouseClicked namespaces-list-view
                        (event-handler
                         [mev]
                         (cond (= MouseButton/PRIMARY (.getButton mev))
                               (let [sel-items (.getSelectedItems namespaces-list-selection)
                                     sel-cnt (count sel-items)]
                                 (when (= 1 sel-cnt)
                                   (get-all-vars-for-ns (first sel-items))))

                               (= MouseButton/SECONDARY (.getButton mev))
                               (let [sel-items (.getSelectedItems namespaces-list-selection)
                                     ctx-menu-instrument-ns-light {:text "Instrument namespace :light"
                                                                   :on-click (fn []
                                                                               (instrument-namespaces (map #(make-inst-ns % :light) sel-items) true))}
                                     ctx-menu-instrument-ns-full {:text "Instrument namespace :full"
                                                                  :on-click (fn []
                                                                              (instrument-namespaces (map #(make-inst-ns % :full) sel-items) true))}
                                     ctx-menu (ui-utils/make-context-menu [ctx-menu-instrument-ns-light
                                                                           ctx-menu-instrument-ns-full])]

                                 (.show ctx-menu
                                        namespaces-list-view
                                        (.getScreenX mev)
                                        (.getScreenY mev))))))

    (store-obj "browser-observable-namespaces-list" observable-namespaces-list)

    pane))

(defn create-vars-pane []
  (let [observable-vars-list (FXCollections/observableArrayList)
        search-field (TextField.)
        observable-vars-filtered-list (FilteredList. observable-vars-list)
        vars-cell-factory (proxy [javafx.util.Callback] []
                            (call [lv]
                              (ui-utils/create-list-cell-factory
                               (fn [list-cell {:keys [var-name]}]
                                 (.setGraphic ^Node list-cell (label var-name))))))
        vars-list-view (doto (ListView. observable-vars-filtered-list)
                         (.setCellFactory vars-cell-factory)
                         (.setEditable false))
        search-bar (h-box [search-field (doto (Label.)
                                          (.setGraphic (icon "mdi-magnify")))])
        pane (v-box [search-bar vars-list-view])]
    (HBox/setHgrow search-field Priority/ALWAYS)
    (VBox/setVgrow vars-list-view Priority/ALWAYS)

    (.addListener (.textProperty search-field)
                  (proxy [ChangeListener] []
                    (changed [_ _ new-val]
                      (.setPredicate observable-vars-filtered-list
                                     (proxy [Predicate] []
                                       (test [{:keys [var-name]}]
                                         (str/includes? var-name new-val)))))))
    (.addListener (-> vars-list-view .getSelectionModel .selectedItemProperty)
                  (proxy [ChangeListener] []
                    (changed [changed old-val new-val]
                      (when new-val
                        (get-var-meta new-val)))))
    (store-obj "browser-observable-vars-list" observable-vars-list)
    pane))

(defn create-fn-details-pane []
  (let [selected-fn-fq-name-label (label "" "browser-fn-fq-name")
        inst-button (button "Instrument" "browser-instrument-btn")
        name-box (doto (h-box [selected-fn-fq-name-label inst-button])
                   (.setAlignment Pos/CENTER_LEFT))
        selected-fn-added-label (label "" "browser-fn-attr")
        selected-fn-file-label (label "" "browser-fn-attr")
        selected-fn-static-label (label "" "browser-fn-attr")
        selected-fn-args-list-v-box (v-box [] "browser-fn-args-box")
        selected-fn-doc-label (label "" "browser-fn-attr")

        selected-fn-detail-pane (v-box [name-box selected-fn-args-list-v-box
                                        selected-fn-added-label selected-fn-doc-label
                                        selected-fn-file-label selected-fn-static-label])]

    (store-obj "browser-instrument-button" inst-button)
    (store-obj "browser-selected-fn-fq-name-label" selected-fn-fq-name-label)
    (store-obj "browser-selected-fn-added-label" selected-fn-added-label)
    (store-obj "browser-selected-fn-file-label" selected-fn-file-label)
    (store-obj "browser-selected-fn-static-label" selected-fn-static-label)
    (store-obj "browser-selected-fn-args-list-v-box" selected-fn-args-list-v-box)
    (store-obj "browser-selected-fn-doc-label" selected-fn-doc-label)

    selected-fn-detail-pane))

(defn- create-instrumentations-pane []
  (let [observable-instrumentations-list (FXCollections/observableArrayList)
        instrumentations-cell-factory
        (proxy [javafx.util.Callback] []
          (call [lv]
            (ui-utils/create-list-cell-factory
             (fn [list-cell {:keys [inst-type] :as inst}]
               (try
                 (let [inst-box (case inst-type
                                 :var (let [{:keys [var-name var-ns]} inst
                                            inst-lbl (doto (h-box [(label "VAR:" "browser-instr-type-lbl")
                                                                   (label (format "%s/%s" var-ns var-name) "browser-instr-label")])
                                                       (.setSpacing 10))
                                            inst-del-btn (doto (button "del" "browser-instr-del-btn")
                                                           (.setOnAction (event-handler
                                                                          [_]
                                                                          (uninstrument-function var-ns var-name true))))]
                                        (doto (h-box [inst-lbl inst-del-btn])
                                          (.setSpacing 10)
                                          (.setAlignment Pos/CENTER_LEFT)))
                                 :ns (let [{:keys [ns-name] :as inst-ns} inst
                                           inst-lbl (doto (h-box [(label "NS:" "browser-instr-type-lbl")
                                                                  (label ns-name "browser-instr-label")])
                                                      (.setSpacing 10))
                                           inst-del-btn (doto (button "del" "browser-instr-del-btn")
                                                          (.setOnAction (event-handler
                                                                         [_]
                                                                         (uninstrument-namespaces [inst-ns] true))))]
                                       (doto (h-box [inst-lbl inst-del-btn])
                                         (.setSpacing 10)
                                         (.setAlignment Pos/CENTER_LEFT))))]
                   (.setGraphic ^Node list-cell inst-box))
                 (catch Exception e (log-error e)))))))
        instrumentations-list (doto (ListView. observable-instrumentations-list)
                                (.setCellFactory instrumentations-cell-factory)
                                (.setEditable false))
        delete-all-btn (doto (button "Delete all")
                         (.setOnAction (event-handler
                                        [_]
                                        (let [type-groups (group-by :inst-type observable-instrumentations-list)
                                              del-namespaces   (:ns type-groups)
                                              del-vars (:var type-groups)]

                                          (uninstrument-namespaces del-namespaces true)

                                          (doseq [v del-vars]
                                            (uninstrument-function (:var-ns v) (:var-name v) true))))))
        en-dis-chk (doto (CheckBox.)
                     (.setSelected true))
        _ (.setOnAction en-dis-chk
                        (event-handler
                         [_]
                         (let [type-groups (group-by :inst-type observable-instrumentations-list)
                               change-namespaces   (:ns type-groups)
                               change-vars (:var type-groups)]

                           (if (.isSelected en-dis-chk)
                             (instrument-namespaces change-namespaces false)
                             (uninstrument-namespaces change-namespaces false))

                           (doseq [v change-vars]
                             (if (.isSelected en-dis-chk)
                               (instrument-function (:var-ns v) (:var-name v) false)
                               (uninstrument-function (:var-ns v) (:var-name v) false))))))
        instrumentations-tools (doto (h-box [(label "Enable all")
                                             en-dis-chk
                                             delete-all-btn]
                                            "browser-instr-tools-box")
                                 (.setSpacing 10)
                                 (.setAlignment Pos/CENTER_LEFT))
        pane (v-box [(label "Instrumentations")
                     instrumentations-tools
                     instrumentations-list])]

    (store-obj "browser-observable-instrumentations-list" observable-instrumentations-list)

    pane))

(defn main-pane []
  (let [namespaces-pane (create-namespaces-pane)
        vars-pane (create-vars-pane)
        selected-fn-detail-pane (create-fn-details-pane)
        inst-pane (create-instrumentations-pane)
        top-split-pane (doto (SplitPane.)
                         (.setOrientation (Orientation/HORIZONTAL)))
        top-bottom-split-pane (doto (SplitPane.)
                                (.setOrientation (Orientation/VERTICAL)))]

    (-> top-split-pane
        .getItems
        (.addAll [namespaces-pane vars-pane selected-fn-detail-pane]))

    (-> top-bottom-split-pane
        .getItems
        (.addAll [top-split-pane
                  inst-pane]))

    (.setDividerPosition top-split-pane 0 0.3)
    (.setDividerPosition top-split-pane 1 0.6)

    (.setDividerPosition top-bottom-split-pane 0 0.7)

    top-bottom-split-pane
    ))
