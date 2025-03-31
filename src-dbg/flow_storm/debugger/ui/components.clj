(ns flow-storm.debugger.ui.components
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler]]
            [clojure.string :as str]
            [flow-storm.debugger.state :as dbg-state]
            [clojure.set :as set])
  (:import [javafx.scene.control Button Menu ContextMenu Label ListView SelectionMode ListCell MenuItem CheckMenuItem ScrollPane Tab
            Alert ButtonType Alert$AlertType ProgressIndicator ProgressBar TextField TextArea TableView TableColumn TableCell TableRow
            TabPane$TabClosingPolicy TabPane$TabDragPolicy TableColumn$CellDataFeatures TabPane Tooltip MenuButton CustomMenuItem
            ComboBox CheckBox TextInputDialog SplitPane TreeView ToolBar MenuBar DialogPane
            ToggleButton]
           [javafx.scene.input KeyCombination$Modifier KeyCodeCombination KeyEvent KeyCode]
           [javafx.scene.layout HBox VBox BorderPane Priority GridPane AnchorPane Pane]
           [javafx.geometry Side Orientation NodeOrientation]
           [javafx.collections.transformation FilteredList]
           [javafx.beans.value ChangeListener]
           [javafx.beans.value ObservableValue]
           [javafx.scene Node Scene]
           [javafx.beans.property ReadOnlyDoubleProperty]
           [javafx.util Duration]
           [java.util.function Predicate]
           [org.kordamp.ikonli.javafx FontIcon]
           [javafx.collections FXCollections ObservableList]
           [org.fxmisc.richtext CodeArea]
           [org.fxmisc.flowless VirtualFlow]
           [javafx.stage Stage]
           [javafx.scene.web WebView WebEngine]
           [javafx.concurrent Worker$State]))

(defn pane [& {:keys [childs classes]}]
  (let [p (Pane. (into-array Node childs))]
    (when classes
      (doseq [c classes]
        (.add (.getStyleClass p) c)))
    p))

(defn menu-item [{:keys [text on-click accel check-item? checked? disable?]}]
  (let [mi (if check-item?
             (CheckMenuItem. text)
             (MenuItem. text))]
    (.setOnAction mi (event-handler [_]
                       (if check-item?
                         (on-click (.isSelected mi))
                         (on-click))))

    (when check-item?
      (.setSelected mi (boolean checked?)))

    (when disable?
      (.setDisable mi true))
    (when accel
      (.setAccelerator mi (KeyCodeCombination.
                           (:key-code accel)
                           (into-array KeyCombination$Modifier (mapv ui-utils/mod-k->key-comb (:mods accel))))))
    mi))

(defn context-menu [& {:keys [items]}]
  (let [cm (ContextMenu.)
        cm-items (mapv menu-item items)]
    (-> cm
        .getItems
        (.addAll ^objects (into-array Object cm-items)))
    cm))

(defn menu [& {:keys [label items]}]
  (let [menu (Menu. label)
        menu-items (mapv menu-item items)]

    (.setMnemonicParsing menu true)

    (-> menu
        .getItems
        (.addAll ^objects (into-array Object menu-items)))
    menu))

(defn icon [& {:keys [name]}]
  (FontIcon. ^String name))

(defn tool-tip [& {:keys [text]}]
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

    (when tooltip (.setTooltip b (tool-tip :text tooltip)))

    b))

(defn icon-button [& {:keys [icon-name classes on-click disable tooltip mirrored?]}]
  (let [b (Button.)]
    (ui-utils/update-button-icon b icon-name)
    (when tooltip (.setTooltip b (tool-tip :text tooltip)))
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

(defn v-box [& {:keys [childs class spacing align paddings]}]
  (let [box (VBox. ^"[Ljavafx.scene.Node;" (into-array Node childs))]

    (when paddings
      (apply (partial ui-utils/set-padding box) paddings))

    (when align
      (.setAlignment box (ui-utils/alignment align)))

    (when spacing
      (.setSpacing box spacing))
    (when class
      (.add (.getStyleClass box) class))
    box))

(defn h-box [& {:keys [childs class spacing align paddings pref-height]}]
  (let [box (HBox. ^"[Ljavafx.scene.Node;" (into-array Node childs))]
    (when pref-height
      (.setPrefHeight box pref-height))
    (when paddings
      (apply (partial ui-utils/set-padding box) paddings))
    (when spacing
      (.setSpacing box spacing))
    (when class
      (.add (.getStyleClass box) class))
    (when align
      (.setAlignment box (ui-utils/alignment align)))
    box))

(defn toggle-button [& {:keys [label classes on-change disable]}]
  (let [tb (ToggleButton. label)]
    (when on-change
      (.setOnAction tb (event-handler
                           [_]
                         (on-change (.isSelected tb)))))

    (when classes
      (doseq [c classes]
        (.add (.getStyleClass tb) c)))

    (when disable
      (.setDisable tb true))

    tb))

(defn border-pane [& {:keys [center bottom top left right class paddings]}]
  (let [bp (BorderPane.)]
    (when paddings
      (apply (partial ui-utils/set-padding bp) paddings))
    (when center (.setCenter bp center))
    (when top (.setTop bp top))
    (when bottom (.setBottom bp bottom))
    (when left (.setLeft bp left))
    (when right (.setRight bp right))

    (when class
      (.add (.getStyleClass bp) class))

    bp))

(defn split [& {:keys [orientation childs sizes]}]
  (let [^SplitPane sp (SplitPane.)]
    (.setOrientation sp (case orientation
                          :vertical   (Orientation/VERTICAL)
                          :horizontal (Orientation/HORIZONTAL)))

    (ui-utils/observable-add-all (.getItems sp) childs)

    (->> sizes
         (map-indexed (fn [i perc]
                        (.setDividerPosition sp i perc)))
         doall)

    sp))

(defn label [& {:keys [text class pref-width on-click tooltip]}]
  (let [lbl (Label. text)]
    (when pref-width
      (.setPrefWidth lbl pref-width))
    (when class
      (ui-utils/add-class lbl class))

    (when tooltip
      (.setTooltip lbl (tool-tip :text tooltip)))

    (when on-click
      (.setOnMouseClicked lbl (event-handler [mev] (on-click mev))))

    lbl))

(defn text-area [& {:keys [text editable? on-change class] :or {editable? true}}]
  (let [ta (TextArea.)]

    (when class
      (ui-utils/add-class ta class))

    (when text
      (.setText ta text))

    (when on-change
      (-> ta
          .textProperty
          (.addListener (proxy [ChangeListener] []
                          (changed  [_ _ new-text]
                            (on-change new-text))))))

    (.setEditable ta editable?)

    ta))

(defn menu-button [& {:keys [title items disable? item-factory on-action class orientation]}]
  (let [mb (MenuButton. title)
        clear-items (fn [] (-> mb .getItems .clear))
        item-factory (or item-factory
                         (fn [{:keys [text tooltip]}]
                           (let [^Label mi-lbl (label :text text)]
                             (when tooltip
                               (.setTooltip mi-lbl (tool-tip :text tooltip)))
                             mi-lbl)))
        add-item (fn [{:keys [hide-on-click?] :as item}]
                   (let [mi (CustomMenuItem. (item-factory item))]
                     (.setHideOnClick mi (boolean hide-on-click?))
                     (.setOnAction mi
                                   (event-handler [_] (on-action item)))
                     (-> mb .getItems (.add mi))))
        set-items (fn [new-items]
                    (clear-items)
                    (doseq [item new-items]
                      (add-item item)))]

    (when orientation
      (.setNodeOrientation mb (case orientation
                                :left-to-right NodeOrientation/LEFT_TO_RIGHT
                                :right-to-left NodeOrientation/RIGHT_TO_LEFT)))
    (when class (ui-utils/add-class mb class))

    (when disable? (.setDisable mb true))
    (set-items items)

    {:menu-button mb
     :set-items set-items
     :clear-items clear-items
     :add-item add-item}))

(defn combo-box [& {:keys [items button-factory cell-factory on-change on-showing classes selected]}]
  (let [cb (ComboBox.)
        sel-model (.getSelectionModel cb)]
    (ui-utils/combo-box-set-items cb items)
    (ui-utils/selection-select-obj sel-model (or selected (first items)))

    (when (seq classes)
      (doseq [c classes]
        (ui-utils/add-class cb c)))

    (when on-change
      (-> cb
          .valueProperty
          (.addListener (proxy [ChangeListener] []
                          (changed [_ prev-val new-val]
                            (on-change prev-val new-val))))))

    (when cell-factory
      (.setCellFactory cb (proxy [javafx.util.Callback] []
                            (call [lv]
                              (ui-utils/list-cell-factory
                               (fn [^ListCell list-cell item]
                                 (.setGraphic list-cell (cell-factory cb item))))))))

    (when button-factory
      (.setButtonCell cb (ui-utils/list-cell-factory
                          (fn [^ListCell list-cell item]
                            (.setGraphic list-cell (button-factory cb item))))))

    (when on-showing
      (.setOnShowing
       cb
       (event-handler [_]
         (on-showing cb))))
    cb))

(defn check-box [& {:keys [on-change selected? focus-traversable?]}]
  (let [cb (CheckBox.)]

    (.setFocusTraversable cb (boolean focus-traversable?))
    (.setSelected cb (boolean selected?))

    (when on-change
      (-> cb
          .selectedProperty
          (.addListener (proxy [ChangeListener] []
                          (changed  [_ _ new-selected?]
                            (on-change new-selected?))))))
    cb))

(defn text-field [& {:keys [initial-text on-return-key on-change align prompt-text class pref-width tooltip]}]
  (let [tf (TextField. "")]
    (when tooltip
      (.setTooltip tf (tool-tip :text tooltip)))
    (when pref-width
      (.setPrefWidth tf pref-width))
    (when prompt-text
      (.setPromptText tf prompt-text))
    (when class
      (ui-utils/add-class tf class))
    (when initial-text
      (.setText tf initial-text))
    (when on-return-key
      (.setOnAction tf (event-handler [_] (on-return-key (.getText tf)))))
    (when align
      (.setAlignment tf (ui-utils/alignment align)))
    (when on-change
      (-> tf
          .textProperty
          (.addListener (proxy [ChangeListener] []
                          (changed  [_ _ new-text]
                            (on-change new-text))))))
    tf))

(defn tab [& {:keys [id text graphic class content on-selection-changed tooltip]}]
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

    (when tooltip (.setTooltip t (tool-tip :text tooltip)))

    t))

(defn alert-dialog [& {:keys [type message buttons center-on-stage width height]
                       :or {type :none}}]
  (let [btn-key->btn-type {:apply  ButtonType/APPLY
                           :close  ButtonType/CLOSE
                           :cancel ButtonType/CANCEL}
        btn-type->btn-key (set/map-invert btn-key->btn-type)
        alert-type (get {:error        Alert$AlertType/ERROR
                         :confirmation Alert$AlertType/CONFIRMATION
                         :information  Alert$AlertType/INFORMATION
                         :warning      Alert$AlertType/WARNING
                         :none         Alert$AlertType/NONE}
                        type)
        buttons-vec (->> buttons
                         (mapv btn-key->btn-type)
                         (into-array ButtonType))
        alert-width  (or width 700)
        alert-height (or height 100)
        alert (Alert. alert-type message buttons-vec)
        ^DialogPane dialog-pane (.getDialogPane alert)]


    (.setResizable alert true)

    (ui-utils/observable-add-all (.getStylesheets dialog-pane) (dbg-state/current-stylesheets))

    (when center-on-stage
      (let [{:keys [x y]} (ui-utils/stage-center-box center-on-stage alert-width alert-height)]

        (doto dialog-pane
          (.setPrefWidth alert-width)
          (.setPrefHeight alert-height))

        (doto alert
          (.setX x)
          (.setY y))))

    (let [btn (.orElse (.showAndWait alert) nil)]
      (btn-type->btn-key btn))))

(defn progress-indicator [& {:keys [size]}]
  (doto (ProgressIndicator.)
    (.setPrefSize size size)))

(defn progress-bar [& {:keys [width]}]
  (doto (ProgressBar.)
    (.setPrefWidth width)))

(defn tab-pane [& {:keys [tabs rotate? side closing-policy drag-policy on-tab-change class]
                   :or {closing-policy :unavailable
                        drag-policy :fixed
                        side :top
                        rotate? false}}]
  (let [tp (TabPane.)
        tabs-list (.getTabs tp)]

    (when class
      (ui-utils/add-class tp class))

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

(defn scroll-pane [& {:keys [class]}]
  (let [sp (ScrollPane.)]
    (when class
      (ui-utils/add-class sp class))
    sp))

(defn anchor-pane [& {:keys [childs]}]
  (let [ap (AnchorPane.)]
    (doseq [{:keys [top-anchor left-anchor right-anchor bottom-anchor node]} childs]
      (when top-anchor (AnchorPane/setTopAnchor node top-anchor))
      (when left-anchor (AnchorPane/setLeftAnchor node left-anchor))
      (when right-anchor (AnchorPane/setRightAnchor node right-anchor))
      (when bottom-anchor (AnchorPane/setBottomAnchor node bottom-anchor)))
    (-> ap
        .getChildren
        (.addAll (mapv :node childs)))
    ap))

(defn list-view [& {:keys [editable? cell-factory on-click on-enter on-selection-change selection-mode search-predicate]
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
        search-bar (h-box :childs [(doto (Label.) (.setGraphic (icon :name "mdi-magnify"))) search-field])

        box (v-box :childs (cond-> []
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

    (when cell-factory
      (.setCellFactory lv (proxy [javafx.util.Callback] []
                            (call [lv]
                              (ui-utils/list-cell-factory cell-factory)))))

    (.setEditable lv editable?)

    (when on-enter
      (.setOnKeyPressed
       lv
       (event-handler
        [^KeyEvent kev]
        (when (= "Enter" (.getName (.getCode kev)))
          (on-enter (selected-items))))))

    (when on-click
      (.setOnMouseClicked
       lv
       (event-handler
        [mev]
        (on-click mev (selected-items) list-view-data))))

    (when on-selection-change
      (-> list-selection
          .selectedItemProperty
          (.addListener (proxy [ChangeListener] []
                          (changed [_ old-val new-val]
                            (on-selection-change old-val new-val))))))

    list-view-data))

(defn table-view [& {:keys [columns cell-factory row-update items selection-mode search-predicate
                            on-click on-enter resize-policy columns-width-percs on-selection-change]
                     :or {selection-mode :multiple
                          resize-policy :unconstrained}}]

  (assert (every? #(= (count %) (count columns)) items) "Every item should have the same amount of elements as columns")

  (let [^TableView tv (TableView.)
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

                                                               (let [cell-graphic (cell-factory this item)]
                                                                 (doto this
                                                                   (.setText nil)
                                                                   (.setGraphic cell-graphic)))))))))))]
                        (when (seq columns-width-percs)
                          (.bind (.prefWidthProperty col)
                                 (let [^ReadOnlyDoubleProperty wp (.widthProperty tv)]
                                   (.multiply wp ^double (get columns-width-percs col-idx)))))
                        col))

        columns (map-indexed make-column columns)
        table-selection (.getSelectionModel tv)
        selected-items (fn [] (.getSelectedItems table-selection))
        search-field (TextField.)
        search-bar (h-box :childs [(doto (Label.) (.setGraphic (icon :name "mdi-magnify"))) search-field])

        box (v-box :childs (cond-> []
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

    (when row-update
      (.setRowFactory
       tv
       (proxy [javafx.util.Callback] []
         (call [tcol]
           (proxy [TableRow] []
             (updateItem [item empty?]
               (let [^TableRow this this]
                 (proxy-super updateItem item empty?)
                 (row-update this item))))))))

    (when on-selection-change
      (-> table-selection
          .selectedItemProperty
          (.addListener (proxy [ChangeListener] []
                          (changed [_ old-val new-val]
                            (on-selection-change old-val new-val))))))

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
        [^KeyEvent kev]
        (when (= "Enter" (.getName ^KeyCode (.getCode kev)))
          (on-enter (selected-items))))))

    (when on-click
      (.setOnMouseClicked
       tv
       (event-handler
        [mev]
        (on-click mev (selected-items) table-data))))

    table-data))

(defn tree-view [& {:keys [cell-factory editable?]}]
  (let [tv (TreeView.)]
    (when cell-factory
      (.setCellFactory tv cell-factory))
    (.setEditable tv (boolean editable?))
    tv))

(defn autocomplete-textfield
  "Creates a textfield with autocompletion.
  `get-completions` should be a fn that will be called with no args when the text length
  changes from 0 to 1 which should to retrieve the autocompletion list as a collection of
  {:text \"...\" :on-select (fn [] ..)} objects. A max of 40 items is displayed.
  When `on-select-set-text?` is true, selection will will set the selected :text as the value of the text field.
  Returns a TextField."
  [& {:keys [get-completions on-select-set-text?]}]
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
                        (reset! options (get-completions)))

                      (let [new-items (reduce (fn [r {:keys [text on-select]}]
                                                (if (< (count r) 40)
                                                  (if (str/includes? text new-val)
                                                    (conj r (doto (MenuItem. text)
                                                              (.setOnAction (event-handler
                                                                             [_]
                                                                              (.setText tf (if on-select-set-text?
                                                                                             text
                                                                                             ""))
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

(defn code-area [& {:keys [editable? text]}]
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

(defn ask-text-dialog [& {:keys [header body width height center-on-stage]}]
  (let [^TextInputDialog tdiag (doto (TextInputDialog.)
                                 (.setHeaderText header)
                                 (.setContentText body))
        dialog-pane (.getDialogPane tdiag)]

    (ui-utils/observable-add-all (.getStylesheets dialog-pane) (dbg-state/current-stylesheets))

    (when (and width height)
      (.setPrefWidth dialog-pane width)
      (.setPrefHeight dialog-pane height))

    (when (and width height center-on-stage)
      (let [{:keys [x y]} (ui-utils/stage-center-box center-on-stage width height)]
        (.setX tdiag x)
        (.setY tdiag y)))

    (.showAndWait tdiag)

    (-> tdiag .getEditor .getText)))

(defn ask-text-and-bool-dialog [& {:keys [header body width height center-on-stage bool-msg]}]
  (let [^TextInputDialog tdiag (doto (TextInputDialog.)
                                 (.setHeaderText header)
                                 (.setContentText body))
        checkb (CheckBox.)
        dialog-pane (.getDialogPane tdiag)]

    (ui-utils/observable-add-all (.getStylesheets dialog-pane) (dbg-state/current-stylesheets))

    (when bool-msg
      (.add ^GridPane (.getContent ^DialogPane (.getDialogPane tdiag)) (label :text bool-msg) 0 2)
      (.add ^GridPane (.getContent ^DialogPane (.getDialogPane tdiag)) checkb 1 2))

    (when (and width height)
      (.setPrefWidth dialog-pane width)
      (.setPrefHeight dialog-pane height))

    (when (and width height center-on-stage)
      (let [{:keys [x y]} (ui-utils/stage-center-box center-on-stage width height)]
        (.setX tdiag x)
        (.setY tdiag y)))

    (.showAndWait tdiag)

    {:text (-> tdiag .getEditor .getText)
     :bool (ui-utils/checkbox-checked? checkb)}))

(defn ask-text-dialog+
  "bodies should be like :

  [{:key :name :label \"Name\" :init-text \"\"}
   {:key :age :label \"Age\" :init-text \"42\"}]

  returns like :

  {:name \"John\"
   :age \"42\"}"
  [& {:keys [header bodies width height center-on-stage]}]
  ;;
  (let [^TextInputDialog tdiag (doto (TextInputDialog.)
                                 (.setHeaderText header))
        bodies' (mapv (fn [{:keys [init-text tooltip] :as b} row]
                        (assoc b
                               :row row
                               :text-field (text-field :initial-text init-text
                                                       :tooltip tooltip)))
                      bodies
                      (range))
        dialog-pane (.getDialogPane tdiag)]

    (ui-utils/observable-add-all (.getStylesheets dialog-pane) (dbg-state/current-stylesheets))

    (doseq [b bodies']
      (.add ^GridPane (.getContent ^DialogPane (.getDialogPane tdiag)) (label :text (:label b)) 0 (:row b))
      (.add ^GridPane (.getContent ^DialogPane (.getDialogPane tdiag)) (:text-field b)          1 (:row b)))

    (when (and width height)
      (.setPrefWidth dialog-pane width)
      (.setPrefHeight dialog-pane height))

    (when (and width height center-on-stage)
      (let [{:keys [x y]} (ui-utils/stage-center-box center-on-stage width height)]
        (.setX tdiag x)
        (.setY tdiag y)))

    (.showAndWait tdiag)

    (reduce (fn [acc b]
              (assoc acc (:key b) (.getText (:text-field b))))
     {}
     bodies')))

(defn thread-label [thread-id thread-name]
  (if thread-name
    (format "(%d) %s" thread-id thread-name)
    (format "thread-%d" thread-id)))

(defn toolbar [& {:keys [childs]}]
  (let [tb (ToolBar. (into-array Node childs))]
    tb))

(defn menu-bar [& {:keys [menues]}]
  (let [^MenuBar mb (MenuBar.)]
    (ui-utils/observable-add-all (.getMenus mb) menues)

    mb))

(defn scene [& {:keys [root window-width window-height]}]
  (Scene. root window-width window-height))

(defn stage [& {:keys [scene title on-close-request x y show?]}]
  (let [^Stage stg (doto (Stage.)
                     (.setScene scene)
                     (.setTitle title)
                     (.setX x)
                     (.setY y))]

    (.setOnCloseRequest stg (event-handler [_]
                              (dbg-state/unregister-jfx-stage! stg)
                              (when on-close-request
                                (on-close-request))))

    (dbg-state/register-jfx-stage! stg)
    (when show? (.show stg))
    stg))

(defn web-view []
  (let [^WebView wv (WebView.)
        ^WebEngine web-engine (.getEngine wv)]
    {:web-view wv
     :set-html (fn [html] (.loadContent web-engine html))
     :load-url (fn [url] (.load web-engine url))
     :set-handlers (fn [handlers-map]
                     (-> web-engine
                         .getLoadWorker
                         .stateProperty
                         (.addListener (proxy [ChangeListener] []
                                         (changed [_ old-state new-state]
                                           (when (= new-state Worker$State/SUCCEEDED)
                                             (let [window (.executeScript web-engine "window")]
                                               (doseq [[method-name method-fn] handlers-map]
                                                 (.setMember window method-name method-fn)))))))))}))
