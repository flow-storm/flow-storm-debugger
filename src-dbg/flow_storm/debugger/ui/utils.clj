(ns flow-storm.debugger.ui.utils

  "Mostly javaFx Utilities for building the UI"

  (:require [flow-storm.utils :refer [log-error]]
            [clojure.string :as str])
  (:import [javafx.scene.control Button ContextMenu Label ListView SelectionMode ListCell MenuItem ScrollPane Tab
            Alert ButtonType Alert$AlertType ProgressIndicator ProgressBar TextField TextArea TableView TableColumn TableCell TableRow
            TabPane$TabClosingPolicy TabPane$TabDragPolicy TableColumn$CellDataFeatures TabPane Tooltip
            ComboBox CheckBox TextInputDialog]
           [javafx.scene.input KeyCharacterCombination KeyCombination$Modifier KeyCombination]
           [javafx.scene.layout HBox VBox BorderPane]
           [javafx.geometry Side Pos]
           [javafx.collections.transformation FilteredList]
           [javafx.beans.value ChangeListener]
           [javafx.beans.value ObservableValue]
           [javafx.scene Node]
           [javafx.scene.layout HBox Priority VBox Region]
           [javafx.util Duration]
           [java.util.function Predicate]
           [org.kordamp.ikonli.javafx FontIcon]
           [javafx.collections FXCollections ObservableList]
           [org.fxmisc.richtext CodeArea]
           [org.fxmisc.flowless VirtualFlow VirtualizedScrollPane]
           [com.jthemedetecor OsThemeDetector]
           [java.awt Toolkit]
           [java.awt.datatransfer StringSelection]))

(defn run-later*
  [f]
  (javafx.application.Platform/runLater f))

(defmacro run-later
  [& body]
  `(run-later* (fn ~(symbol "run-later-fn") []
                 (try
                   ~@body
                   (catch Exception e#
                     (log-error (str "Exception in UI thread @1 " (.getMessage e#)) e#))))))

(defn run-now*
  [f]
  (let [result (promise)]
    (run-later
     (deliver result (try (f)
                          (catch Exception e
                            (log-error (str "Exception in UI thread @2" (.getMessage e)) e)))))
    @result))

(defmacro run-now
  [& body]
  `(run-now* (fn ~(symbol "run-now-fn") [] ~@body)))

(defn event-handler*
  [f]
  (reify javafx.event.EventHandler
    (handle [_ e] (f e))))

(defmacro event-handler [arg & body]
  `(event-handler* (fn ~(symbol "event-handler-fn") ~arg ~@body)))

(defn make-context-menu [items]
  (let [cm (ContextMenu.)
        cm-items (->> items
                      (map (fn [{:keys [text on-click]}]
                             (doto (MenuItem. text)
                               (.setOnAction (event-handler [_] (on-click)))))))]
    (-> cm
        .getItems
        (.addAll ^objects (into-array Object cm-items)))
    cm))

(defn ensure-node-visible-in-scroll-pane [^ScrollPane scroll-pane ^Node node y-perc]
  (let [scroll-pane-content (.getContent scroll-pane)

        ;; first take the top left corner of the `node` and the `scroll-pane` into scene coordinates
        ;; we are using the `scroll-pane` top left corner as the view-port top left corner
        scene-node-bounds (.localToScene node (.getBoundsInLocal node))
        scene-scroll-pane-bounds (.localToScene scroll-pane (.getBoundsInLocal scroll-pane))

        ;; now transform those related to the content pane, so we have everything in content coordinates
        ;; and can make calculations
        node-bounds-in-content (.sceneToLocal scroll-pane-content scene-node-bounds)
        viewport-bounds-in-content (.sceneToLocal scroll-pane-content scene-scroll-pane-bounds)

        pane-w (-> scroll-pane .getContent .getBoundsInLocal .getWidth)
        pane-h (-> scroll-pane .getContent .getBoundsInLocal .getHeight)

        node-interesting-x-in-content (.getMinX node-bounds-in-content)
        node-interesting-y-in-content (+ (.getMinY node-bounds-in-content)
                                         (* y-perc (- (.getMaxY node-bounds-in-content)
                                                      (.getMinY node-bounds-in-content))))

        pane-view-min-x (.getMinX viewport-bounds-in-content)
        pane-view-max-x (+ pane-view-min-x (-> scroll-pane .getViewportBounds .getWidth))

        pane-view-min-y (.getMinY viewport-bounds-in-content)
        pane-view-max-y (+ pane-view-min-y (-> scroll-pane .getViewportBounds .getHeight))

        ;; check if the node is visible in both axis
        node-visible-x? (<= pane-view-min-x node-interesting-x-in-content pane-view-max-x)
        node-visible-y? (<= pane-view-min-y node-interesting-y-in-content pane-view-max-y)]

    ;; if the node isn't visible in any of the axis scroll accordingly
    (when-not node-visible-x?
      (.setHvalue scroll-pane (/ node-interesting-x-in-content pane-w)))
    (when-not node-visible-y?
      (.setVvalue scroll-pane (/ node-interesting-y-in-content pane-h)))))

(defn create-list-cell-factory [update-item-fn]
  (proxy [ListCell] []
    (updateItem [item empty?]
      (let [^ListCell this this]
        (proxy-super updateItem item empty?)
        (if empty?
          (.setGraphic this nil)
          (update-item-fn this item))))))

(defn add-class [^Node node class]
  (.add (.getStyleClass node) class))

(defn rm-class [^Node node class]
  (.remove (.getStyleClass node) class))

(defn clear-classes [^Node node]
  (.clear (.getStyleClass node)))

(defn icon [^String icon-name]
  (FontIcon. icon-name))

(defn- tool-tip [text]
  (doto (Tooltip. text)
    (.setShowDelay (Duration. 400))))

(defn button [& {:keys [label classes on-click disable tooltip]}]
  (let [b (Button. label)]

    (when on-click
      (.setOnAction b (event-handler [_] (on-click))))

    (when classes
      (doseq [c classes]
        (.add (.getStyleClass b) c)))

    (when disable
      (.setDisable b true))

    (when tooltip (.setTooltip b (tool-tip tooltip)))

    b))

(defn icon-button [& {:keys [icon-name classes on-click disable tooltip mirrored?]}]
  (let [b (doto (Button.)
            (.setGraphic (FontIcon. icon-name)))]

    (when tooltip (.setTooltip b (tool-tip tooltip)))
    (when on-click
      (.setOnAction b (event-handler [_] (on-click))))

    (when classes
      (doseq [c classes]
        (.add (.getStyleClass b) c)))

    (when mirrored?
      (.add (.getStyleClass b) "mirrored"))

    (when disable
      (.setDisable b true))

    b))

(defn update-button-icon [btn new-icon-name]
  (.setGraphic btn (FontIcon. new-icon-name)))

(defn v-box
  ([childs] (v-box childs nil))
  ([childs class]
   (let [box (VBox. ^"[Ljavafx.scene.Node;" (into-array Node childs))]
     (when class
       (.add (.getStyleClass box) class))
     box)))

(defn h-box
  ([childs] (h-box childs nil))
  ([childs class]
   (let [box (HBox. ^"[Ljavafx.scene.Node;" (into-array Node childs))]
     (when class
       (.add (.getStyleClass box) class))
     box)))

(defn border-pane
  ([params] (border-pane params nil))
  ([{:keys [center bottom top left right]} class]
   (let [bp (BorderPane.)]
     (when center (.setCenter bp center))
     (when top (.setTop bp top))
     (when bottom (.setBottom bp bottom))
     (when left (.setLeft bp left))
     (when right (.setRight bp right))

     (when class
       (.add (.getStyleClass bp) class))

     bp)))

(defn label
  ([text] (label text nil))
  ([text class]
   (let [lbl (Label. text)]
     (when class
       (add-class lbl class))
     lbl)))

(defn text-area [text {:keys [:editable?] :or {editable? true}}]
  (let [ta (TextArea.)]

    (when text
      (.setText ta text))

    (.setEditable ta editable?)

    ta))

(defn combo-box-set-items [^ComboBox cbox items]
  (let [observable-list (FXCollections/observableArrayList)]
    (.setItems cbox observable-list)
    (.addAll observable-list ^objects (into-array Object items))))

(defn combo-box [{:keys [items on-change-fn on-showing-fn]}]
  (let [cb (ComboBox.)
        sel-model (.getSelectionModel cb)]
    (combo-box-set-items cb items)
    (.select sel-model (first items))

    (when on-change-fn
      (-> cb
          .valueProperty
          (.addListener (proxy [ChangeListener] []
                          (changed [_ prev-val new-val]
                            (on-change-fn prev-val new-val))))))

    (when on-showing-fn
      (.setOnShowing
       cb
       (event-handler [_] (on-showing-fn cb))))
    cb))

(defn check-box [{:keys [on-change]}]
  (let [cb (CheckBox.)]
    (when on-change
      (-> cb
          .selectedProperty
          (.addListener (proxy [ChangeListener] []
                          (changed  [_ _ new-selected?]
                            (on-change new-selected?))))))
    cb))

(defn set-min-size-wrap-content [node]
  (doto node
    (.setMinHeight (Region/USE_PREF_SIZE))))


(defn text-field
  ([params] (text-field params nil))
  ([{:keys [initial-text on-return-key on-change align]} class]
   (let [tf (TextField. "")]
     (when class
       (add-class tf class))
     (when initial-text
       (.setText tf initial-text))
     (when on-return-key
       (.setOnAction tf (event-handler [_] (on-return-key (.getText tf)))))
     (when align
       (.setAlignment tf (get {:left   Pos/CENTER_LEFT
                               :right  Pos/CENTER_RIGHT
                               :center Pos/CENTER}
                              align)))
     (when on-change
      (-> tf
          .textProperty
          (.addListener (proxy [ChangeListener] []
                          (changed  [_ _ new-text]
                            (on-change new-text))))))
     tf)))

(defn tab [{:keys [id text graphic class content on-selection-changed tooltip]}]
  (let [t (Tab.)]

    (if graphic
      (.setGraphic t graphic)
      (.setText t text))

    (.add (.getStyleClass t) class)
    (.setContent t content)

    (when id
      (.setId t (str id)))

    (when on-selection-changed
      (.setOnSelectionChanged t on-selection-changed))

    (when tooltip (.setTooltip t (tool-tip tooltip)))

    t))

(defn alert-dialog [{:keys [type message buttons]
                     :or {type :none}}]
  (let [alert-type (get {:error        Alert$AlertType/ERROR
                         :confirmation Alert$AlertType/CONFIRMATION
                         :information  Alert$AlertType/INFORMATION
                         :warning      Alert$AlertType/WARNING
                         :none         Alert$AlertType/NONE}
                        type)
        buttons-vec (->> buttons
                         (mapv (fn [b]
                                 (get {:apply  ButtonType/APPLY
                                       :close  ButtonType/CLOSE
                                       :cancel ButtonType/CANCEL}
                                      b)))
                         (into-array ButtonType))]
    (Alert. alert-type message buttons-vec)))

(defn progress-indicator [size]
  (doto (ProgressIndicator.)
    (.setPrefSize size size)))

(defn progress-bar [width]
  (doto (ProgressBar.)
    (.setPrefWidth width)))

(defn tab-pane [{:keys [tabs rotate? side closing-policy drag-policy on-tab-change]
                 :or {closing-policy :unavailable
                      drag-policy :fixed
                      side :top
                      rotate? false}}]
  (let [tp (TabPane.)
        tabs-list (.getTabs tp)]

    (doto tp
      (.setRotateGraphic rotate?)
      (.setSide (get {:left (Side/LEFT)
                      :right (Side/RIGHT)
                      :top (Side/TOP)
                      :bottom (Side/BOTTOM)}
                     side))
      (.setTabClosingPolicy (get {:unavailable  TabPane$TabClosingPolicy/UNAVAILABLE
                                  :all-tabs     TabPane$TabClosingPolicy/ALL_TABS
                                  :selected-tab TabPane$TabClosingPolicy/SELECTED_TAB}
                                 closing-policy))
      (.setTabDragPolicy (get {:fixed   TabPane$TabDragPolicy/FIXED
                               :reorder TabPane$TabDragPolicy/REORDER}
                              drag-policy)))

    (when on-tab-change
      (-> tp
          .getSelectionModel
          .selectedItemProperty
          (.addListener (proxy [ChangeListener] []
                          (changed [_ old-tab new-tab]
                            (on-tab-change old-tab new-tab))))))
    (when tabs
      (.addAll tabs-list ^objects (into-array Object tabs)))

    tp))

(defn scroll-pane
  ([] (scroll-pane nil))
  ([class]
   (let [sp (ScrollPane.)]
     (when class
       (add-class sp class))
     sp)))

(defn list-view [{:keys [editable? cell-factory-fn on-click on-enter selection-mode search-predicate]
                  :or {editable? false
                       selection-mode :multiple}}]
  (let [observable-list (FXCollections/observableArrayList)
        ;; by default create a constantly true predicate
        observable-filtered-list (FilteredList. observable-list)
        lv (ListView. observable-filtered-list)
        list-selection (.getSelectionModel lv)
        add-all (fn [elems] (.addAll observable-list ^objects (into-array Object elems)))
        remove-all (fn [elems] (.removeAll observable-list ^objects (into-array Object elems)))
        clear (fn [] (.clear observable-list))
        get-all-items (fn [] (seq observable-list))
        selected-items (fn [] (.getSelectedItems list-selection))

        search-field (TextField.)
        search-bar (h-box [(doto (Label.) (.setGraphic (icon "mdi-magnify"))) search-field])

        box (v-box (cond-> []
                     search-predicate (conj search-bar)
                     true (conj lv)))
        list-view-data {:list-view-pane box
                        :list-view lv
                        :add-all add-all
                        :clear clear
                        :get-all-items get-all-items
                        :remove-all remove-all}]

    (HBox/setHgrow search-field Priority/ALWAYS)
    (HBox/setHgrow lv Priority/ALWAYS)
    (VBox/setVgrow lv Priority/ALWAYS)

    (when search-predicate
      (.addListener (.textProperty search-field)
                    (proxy [ChangeListener] []
                      (changed [_ _ new-val]
                        (.setPredicate observable-filtered-list
                                       (proxy [Predicate] []
                                         (test [item]
                                           (search-predicate item new-val))))))))

    (case selection-mode
      :multiple (.setSelectionMode list-selection SelectionMode/MULTIPLE)
      :single   (.setSelectionMode list-selection SelectionMode/SINGLE))

    (when cell-factory-fn
      (.setCellFactory lv (proxy [javafx.util.Callback] []
                            (call [lv]
                              (create-list-cell-factory cell-factory-fn)))))

    (.setEditable lv editable?)

    (when on-enter
      (.setOnKeyPressed
       lv
       (event-handler
        [kev]
        (when (= "Enter" (.getName (.getCode kev)))
          (on-enter (selected-items))))))

    (when on-click
      (.setOnMouseClicked
       lv
       (event-handler
        [mev]
        (on-click mev (selected-items) list-view-data))))

    list-view-data))

(defn table-view

  "Create a TableView component.
  `columns` should be a vector of strings with columns names.
  `cell-factory-fn` a fn that gets called with each cell and item and should return a Node for the cell.
  `items` a collection of data items for the table. Each one should be a vector with the same amount "

  [{:keys [columns cell-factory-fn row-update-fn items selection-mode search-predicate
           on-click on-enter resize-policy columns-width-percs]
    :or {selection-mode :multiple
         resize-policy :unconstrained}}]

  (assert (every? #(= (count %) (count columns)) items) "Every item should have the same amount of elements as columns")

  (let [tv (TableView.)
        make-column (fn [col-idx col-text]
                      (let [col (doto (TableColumn. col-text)
                                  (.setCellValueFactory (proxy [javafx.util.Callback] []
                                                          (call [^TableColumn$CellDataFeatures cell-val]
                                                            (proxy [ObservableValue] []
                                                              (addListener [_])
                                                              (removeListener [_])
                                                              (getValue []
                                                                (get (.getValue cell-val) col-idx))))))
                                  (.setCellFactory (proxy [javafx.util.Callback] []
                                                     (call [tcol]
                                                       (proxy [TableCell] []
                                                         (updateItem [item empty?]
                                                           (let [^TableCell this this]
                                                             (proxy-super updateItem item empty?)
                                                             (if empty?

                                                               (doto this
                                                                 (.setGraphic nil)
                                                                 (.setText nil))

                                                               (let [cell-graphic (cell-factory-fn this item)]
                                                                 (doto this
                                                                   (.setText nil)
                                                                   (.setGraphic cell-graphic)))))))))))]
                        (when (seq columns-width-percs)
                          (.bind (.prefWidthProperty col)
                                 (-> tv .widthProperty (.multiply (get columns-width-percs col-idx)))))
                        col))

        columns (map-indexed make-column columns)
        table-selection (.getSelectionModel tv)
        selected-items (fn [] (.getSelectedItems table-selection))
        search-field (TextField.)
        search-bar (h-box [(doto (Label.) (.setGraphic (icon "mdi-magnify"))) search-field])

        box (v-box (cond-> []
                     search-predicate (conj search-bar)
                     true (conj tv)))

        ^ObservableList items-array-list (FXCollections/observableArrayList ^objects (into-array Object items))
        filtered-items-array (FilteredList. items-array-list)
        add-all (fn [elems] (.addAll items-array-list ^objects (into-array Object elems)))
        clear (fn [] (.clear items-array-list))
        table-data {:table-view tv
                    :table-view-pane box
                    :clear clear
                    :add-all add-all}]

    (when row-update-fn
      (.setRowFactory
       tv
       (proxy [javafx.util.Callback] []
         (call [tcol]
           (proxy [TableRow] []
             (updateItem [item empty?]
               (let [^TableRow this this]
                 (proxy-super updateItem item empty?)
                 (row-update-fn this item))))))))

    (.clear (.getColumns tv))
    (.addAll (.getColumns tv) ^objects (into-array Object columns))

    (.setItems tv filtered-items-array)
    (.setColumnResizePolicy tv (case resize-policy
                                 :unconstrained TableView/UNCONSTRAINED_RESIZE_POLICY
                                 :constrained TableView/CONSTRAINED_RESIZE_POLICY))
    (HBox/setHgrow search-field Priority/ALWAYS)
    (HBox/setHgrow tv Priority/ALWAYS)
    (VBox/setVgrow tv Priority/ALWAYS)

    (case selection-mode
      :multiple (.setSelectionMode table-selection SelectionMode/MULTIPLE)
      :single   (.setSelectionMode table-selection SelectionMode/SINGLE))

    (when search-predicate
      (.addListener (.textProperty search-field)
                    (proxy [ChangeListener] []
                      (changed [_ _ new-val]
                        (.setPredicate filtered-items-array
                                       (proxy [Predicate] []
                                         (test [item]
                                           (search-predicate item new-val))))))))

    (when on-enter
      (.setOnKeyPressed
       tv
       (event-handler
        [kev]
        (when (= "Enter" (.getName (.getCode kev)))
          (on-enter (selected-items))))))

    (when on-click
      (.setOnMouseClicked
       tv
       (event-handler
        [mev]
        (on-click mev (selected-items) table-data))))

    table-data))

(defn autocomplete-textfield
  "Creates a textfield with autocompletion.
  `completions-fn` should be a fn that will be called with no args when the text length
  changes from 0 to 1 which should to retrieve the autocompletion list as a collection of
  {:text \"...\" :on-select (fn [] ..)} objects. A max of 25 items is displayed.
  Returns a TextField."
  [completions-fn]
  (let [^TextField tf (TextField.)
        ^ContextMenu options-menu (ContextMenu.)
        options (atom nil)]
    (.addListener (.textProperty tf)
                  (proxy [ChangeListener] []
                    (changed [_ old-val new-val]

                      (when (= 0 (count new-val))
                        (reset! options nil))

                      (when (and (= 0 (count old-val))
                                 (pos? (count new-val)))
                        (reset! options (completions-fn)))

                      (let [new-items (reduce (fn [r {:keys [text on-select]}]
                                                (if (< (count r) 25)
                                                  (if (str/includes? text new-val)
                                                    (conj r (doto (MenuItem. text)
                                                              (.setOnAction (event-handler
                                                                             [_]
                                                                             (.setText tf "")
                                                                             (on-select)))))
                                                    r)
                                                  (reduced r)))
                                              []
                                              @options)
                            ^ObservableList menu-items (.getItems options-menu)]
                        (.clear menu-items)
                        (if (seq new-items)
                          (do
                            (.addAll menu-items ^objects (into-array Object new-items))
                            (.show options-menu tf Side/BOTTOM 0 0))
                          (.hide options-menu))))))
    tf))

(defn remove-newlines [s]
  (-> ^String s
      (.replaceAll "\\n" "")
      (.replaceAll "\\r" "")))

(defn code-area [{:keys [editable? text]}]
  (let [ca (proxy [CodeArea] []
             (computePrefHeight [w]
               (let [^CodeArea this this
                     ih (+ (-> this .getInsets .getTop)
                           (-> this .getInsets .getBottom))
                     ^ObservableList childs (.getChildren this)
                     p-cnt (.size (.getParagraphs this))]
                 (if (and (pos? (.size childs))
                          (pos? p-cnt))
                   (let [^VirtualFlow c (.get childs 0)]
                     (+ ih (->> (range p-cnt)
                                (map (fn [i] (-> c
                                                 (.getCell i)
                                                 .getNode
                                                 (.prefHeight w))))
                                (reduce + 0))))

                   ;; else
                   (+ ih (.getTotalHeightEstimate this))))))]
    (doto ca
      (.setEditable editable?)
      (.replaceText 0 0 text))))

(defn virtualized-scroll-pane [node {:keys [max-height]}]
  (doto (VirtualizedScrollPane. node)
    (.setMaxHeight max-height)))

(defn get-current-os-theme []
  (try
    (if (.isDark (OsThemeDetector/getDetector))
      :dark
      :light)
    (catch Exception e
      (log-error "Couldn't retrieve os theme, setting :light by default" e)
      :light)))

(defn set-clipboard [text]
  (let [str-sel (StringSelection. text)]
    (-> (Toolkit/getDefaultToolkit)
        .getSystemClipboard
        (.setContents str-sel str-sel))))

(defn copy-selected-frame-to-clipboard
  ([fn-ns fn-name] (copy-selected-frame-to-clipboard fn-ns fn-name nil))
  ([fn-ns fn-name args-vec]
   (let [fqfn (if fn-ns
                (format "%s/%s" fn-ns fn-name)
                fn-name)
         clip-text (if args-vec
                     (format "(apply %s (flow-storm.runtime.values/deref-val-id %d))" fqfn (:vid args-vec))
                     fqfn)]
     (set-clipboard clip-text))))

(defn ask-text-dialog [{:keys [header body]}]
  (let [tdiag (doto (TextInputDialog.)
                (.setHeaderText header)
                (.setContentText body))]
    (.showAndWait tdiag)
    (-> tdiag .getEditor .getText)))

(defn key-combo-match?
  "Return true if the keyboard event `kev` matches the `key-name` and `modifiers`.
  `key-name` should be a stirng with the key name.
  `modifiers` should be a collection of modifiers like :ctrl, :shift"
  [kev key-name modifiers]
  (let [mod-k->key-comb (fn [m]
                          (case m
                            :shift KeyCombination/SHIFT_DOWN
                            :ctrl  KeyCombination/CONTROL_DOWN))
        k (KeyCharacterCombination. key-name  (into-array KeyCombination$Modifier (mapv mod-k->key-comb modifiers)))]
    (.match k kev)))

(defn thread-label [thread-id thread-name]
  (if thread-name
    (format "[%d] %s" thread-id thread-name)
    (format "thread-%d" thread-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Node index ids builders ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-token-id [form-id coord]
  (format "form_token_%d_%s" form-id (hash coord)))

(defn flow-tab-id [flow-id]
  (format "flow_tab_%d" flow-id))

(defn thread-form-box-id [form-id]
  (format "form_box_%d" form-id))

(defn thread-form-paint-fn [form-id]
  (format "form_paint_fn_%d" form-id))

(defn thread-pprint-area-id [pane-id]
  (format "pprint_area_%s" pane-id))

(defn thread-pprint-type-lbl-id [pane-id]
  (format "pprint_type_lbl_%s" pane-id))

(defn thread-pprint-extra-lbl-id [pane-id]
  (format "pprint_extra_lbl_%s" pane-id))

(defn thread-pprint-def-btn-id [pane-id]
  (format "pprint_def_btn_id_%s" pane-id))

(defn thread-pprint-inspect-btn-id [pane-id]
  (format "pprint_inspect_btn_id_%s" pane-id))

(defn thread-pprint-tap-btn-id [pane-id]
  (format "pprint_tap_btn_id_%s" pane-id))

(defn thread-pprint-level-txt-id [pane-id]
  (format "pprint_level_txt_id_%s" pane-id))

(defn thread-pprint-meta-chk-id [pane-id]
  (format "pprint_meta_chk_id_%s" pane-id))

(defn thread-callstack-tree-cell [idx]
  (format "callstack_tree_cell_%d" idx))
