(ns flow-storm.debugger.ui.browser.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler run-now v-box h-box label icon]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.target-commands :as target-commands]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon button add-class]]
            [clojure.string :as str])
  (:import [javafx.scene.control Button Label ListView SplitPane TextField]
           [javafx.scene Node]
           [ javafx.beans.value ChangeListener]
           [javafx.collections FXCollections ObservableList]
           [javafx.collections.transformation FilteredList]
           [javafx.scene.layout HBox Priority VBox]
           [javafx.geometry Orientation Pos]
           [java.util.function Predicate]))

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
    (.setOnAction browser-instrument-button
                  (event-handler
                   [_]
                   (target-commands/run-command :instrument-fn
                                                {:fn-symb (symbol ns name)})))
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
                                 (ui-utils/run-later (update-vars-pane all-vars)))))

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
        pane (v-box [search-bar namespaces-list-view])]

    (HBox/setHgrow search-field Priority/ALWAYS)
    (VBox/setVgrow namespaces-list-view Priority/ALWAYS)

    (.addListener (.textProperty search-field)
                  (proxy [ChangeListener] []
                    (changed [_ _ new-val]
                      (.setPredicate observable-namespaces-filtered-list
                                     (proxy [Predicate] []
                                       (test [ns-name]
                                         (str/includes? ns-name new-val)))))))
    (.addListener (-> namespaces-list-view .getSelectionModel .selectedItemProperty)
                  (proxy [ChangeListener] []
                    (changed [changed old-val new-val]
                      (when new-val
                        (get-all-vars-for-ns new-val)))))

    (store-obj "browser-observable-namespaces-list" observable-namespaces-list)
    pane))

(defn create-vars-pane []
  (let [observable-vars-list (FXCollections/observableArrayList)
        search-field (TextField.)
        observable-vars-filtered-list (FilteredList. observable-vars-list)
        vars-cell-factory (proxy [javafx.util.Callback] []
                            (call [lv]
                              (ui-utils/create-list-cell-factory
                               (fn [list-cell {:keys [var-name var-ns]}]
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

(defn main-pane []
  (let [namespaces-pane (create-namespaces-pane)
        vars-pane (create-vars-pane)
        selected-fn-detail-pane (create-fn-details-pane)
        split-pane (doto (SplitPane.)
                     (.setOrientation (Orientation/HORIZONTAL)))]

    (-> split-pane
        .getItems
        (.addAll [namespaces-pane vars-pane selected-fn-detail-pane]))

    (.setDividerPosition split-pane 0 0.3)
    (.setDividerPosition split-pane 1 0.6)

    split-pane
    ))
