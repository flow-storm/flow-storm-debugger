(ns flow-storm.debugger.ui.utils

  "Mostly javaFx Utilities for building the UI"

  (:require [flow-storm.utils :as utils :refer [log-error]]
            [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup]])
  (:import [javafx.scene.control ScrollPane ComboBox ListCell ButtonBase Labeled SelectionModel TabPane
            Tab CheckBox TextInputControl ContextMenu]
           [javafx.scene.input KeyCharacterCombination KeyCombination$Modifier KeyCombination MouseButton MouseEvent]
           [javafx.event Event]
           [javafx.scene.layout HBox Region Pane]
           [javafx.stage Screen Stage]
           [javafx.scene Node]
           [java.util.function Predicate]
           [org.kordamp.ikonli.javafx FontIcon]
           [javafx.collections FXCollections ObservableList]
           [javafx.geometry Insets Pos Rectangle2D]
           [com.jthemedetecor OsThemeDetector]
           [java.awt Toolkit]
           [java.awt.datatransfer StringSelection]
           [javafx.application Platform]))


(defn init-toolkit []
  (let [p (promise)]
    (try
      (Platform/startup (fn [] (deliver p true)))
      (catch Exception _ (deliver p false)))
    (if @p
      (utils/log "JavaFX toolkit initialized")
      (utils/log "JavaFX toolkit already initialized"))))

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

(defn mod-k->key-comb [m]
  (case m
    :shift KeyCombination/SHIFT_DOWN
    :ctrl  KeyCombination/CONTROL_DOWN))

(defn stage-screen-info [^Stage stage]
  (let [^Screen screen (first
                        (Screen/getScreensForRectangle (.getX stage)
                                                       (.getY stage)
                                                       (.getWidth stage)
                                                       (.getHeight stage)))
        ^Rectangle2D bounds (.getBounds screen)
        screen-width (.getWidth bounds)
        screen-height (.getHeight bounds)]
    {:screen-width screen-width
     :screen-height screen-height
     :screen-visual-center-x (+ (/ screen-width 2) (.getMinX bounds))
     :screen-visual-center-y (+ (/ screen-height 2) (.getMinY bounds))}))

(defn stage-center-box [^Stage reference-stg target-w target-h]
  (let [ref-x (.getX reference-stg)
        ref-y (.getY reference-stg)
        ref-w (.getWidth reference-stg)
        ref-h (.getHeight reference-stg)
        ref-center-x (+ ref-x (/ ref-w 2))
        ref-center-y (+ ref-y (/ ref-h 2))

        tgt-x (- ref-center-x (/ target-w 2))
        tgt-y (- ref-center-y (/ target-h 2))]

    {:x tgt-x
     :y tgt-y}))

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

(defn list-cell-factory [update-item-fn]
  (proxy [ListCell] []
    (updateItem [item empty?]
      (proxy-super updateItem item empty?)
      (if empty?
        (do
          (.setText ^ListCell this nil)
          (.setGraphic ^ListCell this nil))
        (update-item-fn ^ListCell this item)))))

(defn add-class [^Node node class]
  (.add (.getStyleClass node) class))

(defn rm-class [^Node node class]
  (.removeIf (.getStyleClass node)
             (proxy [Predicate] []
               (test [c]
                 (= c class)))))

(defn clear-classes [^Node node]
  (.clear (.getStyleClass node)))

(defn update-button-icon [btn icon-name]
  (doto btn
    (.setGraphic (if (string? icon-name)
                   (FontIcon. ^String icon-name)
                   (HBox. (into-array Node (mapv (fn [in] (FontIcon. ^String in)) icon-name)))))))

(defn observable-add-all [^ObservableList olist coll]
  (.addAll olist ^objects (into-array Object coll)))

(defn observable-clear [^ObservableList olist]
  (.clear olist))

(defn set-disable [^Node node x]
  (.setDisable node x))

(defn set-min-size-wrap-content [^Region node]
  (.setMinHeight node (Region/USE_PREF_SIZE))
  node)

(defn show-context-menu [& {:keys [^ContextMenu menu ^Node parent x y mouse-ev]}]
  (let [[^ContextMenu curr-menu] (obj-lookup "current_context_menu")
        ^double x (or x (when mouse-ev (.getScreenX mouse-ev)) 0)
        ^double y (or y (when mouse-ev (.getScreenY mouse-ev)) 0)]
    (when curr-menu (.hide curr-menu))
    (.show menu parent x y)
    (store-obj "current_context_menu" menu)))

(defn remove-newlines [s]
  (-> ^String s
      (.replaceAll "\\n" "")
      (.replaceAll "\\r" "")))

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



(defn key-combo-match?
  "Return true if the keyboard event `kev` matches the `key-name` and `modifiers`.
  `key-name` should be a stirng with the key name.
  `modifiers` should be a collection of modifiers like :ctrl, :shift"
  [kev key-name modifiers]
  (let [k (KeyCharacterCombination. key-name  (into-array KeyCombination$Modifier (mapv mod-k->key-comb modifiers)))]
    (.match k kev)))

(defn add-childrens-to-pane [^Pane pane childs]
  (observable-add-all (.getChildren pane) childs))

(defn set-button-action [^ButtonBase button f]
  (.setOnAction button (event-handler [_] (f))))

(defn set-text [^Labeled labeled ^String text]
  (.setText labeled text)
  labeled)

(defn set-text-input-text [^TextInputControl tic ^String text]
  (.setText tic text)
  tic)

(defn set-graphic [^Labeled labeled ^Node node]
  (.setGraphic labeled node)
  labeled)

(defn set-padding
  ([^Region region pad]
   (.setPadding region (Insets. pad)))
  ([^Region region pad-top pad-right pad-bottom pad-left]
   (.setPadding region (Insets. pad-top pad-right pad-bottom pad-left))))

(defn consume [^Event e]
  (.consume e))

(defn alignment [k]
  (case k
    :baseline-center Pos/BASELINE_CENTER
    :baseline-left Pos/BASELINE_LEFT
    :baseline-right Pos/BASELINE_RIGHT
    :bottom-center Pos/BOTTOM_CENTER
    :bottom-left Pos/BOTTOM_LEFT
    :bottom-right Pos/BOTTOM_RIGHT
    :center Pos/CENTER
    :center-left Pos/CENTER_LEFT
    :center-right Pos/CENTER_RIGHT
    :top-center Pos/TOP_CENTER
    :top-left Pos/TOP_LEFT
    :top-right Pos/TOP_RIGHT))

(defn mouse-primary? [^MouseEvent mev]
  (= MouseButton/PRIMARY (.getButton mev)))

(defn mouse-secondary? [^MouseEvent mev]
  (= MouseButton/SECONDARY (.getButton mev)))

(defn double-click? [^MouseEvent mev]
  (= 2 (.getClickCount mev)))

(defn selection-select-idx [^SelectionModel model idx]
  (.select model ^int idx))

(defn selection-select-obj [^SelectionModel model obj]
  (.select model obj))

(defn selection-select-first [^SelectionModel model]
  (.selectFirst model))

(defn combo-box-set-items [^ComboBox cbox items]
  (let [observable-list (FXCollections/observableArrayList)]
    (.clear observable-list)
    (.setItems cbox observable-list)
    (observable-add-all observable-list items)))

(defn combo-box-set-selected [^ComboBox cbox item]
  (selection-select-obj (.getSelectionModel cbox) item))

(defn combo-box-get-selected-item [^ComboBox cbox]
  (.getSelectedItem (.getSelectionModel cbox)))

(defn add-tab-pane-tab [^TabPane tp ^Tab t]
  (observable-add-all (.getTabs tp) [t]))

(defn rm-tab-pane-tab [^TabPane tp ^Tab t]
  (-> tp
      .getTabs
      (.remove t)))

(defn checkbox-checked? [^CheckBox cb]
  (.isSelected cb))

(defn pane-children [^Pane p]
  (.getChildren p))

(defn add-change-listener [])
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
