(ns flow-storm.debugger.ui.utils
  (:require [flow-storm.utils :refer [log-error]])
  (:import [javafx.scene.control Button ContextMenu Label ListView SelectionMode ListCell MenuItem ScrollPane Tab
            Alert ButtonType Alert$AlertType ProgressIndicator TextField
            TabPane$TabClosingPolicy TabPane]
           [javafx.scene.layout HBox VBox BorderPane]
           [javafx.geometry Side]
           [javafx.collections.transformation FilteredList]
           [javafx.beans.value ChangeListener]
           [javafx.scene Node]
           [javafx.scene.layout HBox Priority VBox]
           [java.util.function Predicate]
           [org.kordamp.ikonli.javafx FontIcon]
           [javafx.collections FXCollections]))

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
        (.addAll (into-array MenuItem cm-items)))
    cm))

(defn center-node-in-scroll-pane [^ScrollPane scroll-pane ^Node node]
  (let [h (-> scroll-pane .getContent .getBoundsInLocal .getHeight)
        y (/ (+ (-> node .getBoundsInParent .getMaxY)
                (-> node .getBoundsInParent .getMinY))
             2.0)
        v (-> scroll-pane .getViewportBounds .getHeight)]
    (.setVvalue scroll-pane (* (.getVmax scroll-pane)
                               (/ (- y (* v 0.5))
                                  (- h v))))))

(defn create-list-cell-factory [update-item-fn]
  (proxy [ListCell] []
    (updateItem [item empty?]
      (proxy-super updateItem item empty?)
      (if empty?
        (.setGraphic ^Node this nil)
        (update-item-fn this item)))))

(defn add-class [node class]
  (.add (.getStyleClass node) class))

(defn rm-class [node class]
  (.remove (.getStyleClass node) class))

(defn icon [icon-name]
  (FontIcon. icon-name))

(defn button
  ([lbl]
   (button lbl nil))
  ([lbl class]
   (let [b (Button. lbl)]
     (when class
       (.add (.getStyleClass b) class))
     b)))

(defn icon-button
  ([icon-name]
   (icon-button icon-name nil))
  ([icon-name class]
   (let [b (doto (Button.)
             (.setGraphic (FontIcon. icon-name)))]
     (when class
       (.add (.getStyleClass b) class))
     b)))

(defn v-box
  ([childs] (v-box childs nil))
  ([childs class]
   (let [box (VBox. (into-array Node childs))]
     (when class
       (.add (.getStyleClass box) class))
     box)))

(defn h-box
  ([childs] (h-box childs nil))
  ([childs class]
   (let [box (HBox. (into-array Node childs))]
     (when class
       (.add (.getStyleClass box) class))
     box)))

(defn border-pane [{:keys [center bottom top left right]}]
  (let [bp (BorderPane.)]
    (when center (.setCenter bp center))
    (when top (.setTop bp top))
    (when bottom (.setBottom bp bottom))
    (when left (.setLeft bp left))
    (when right (.setRight bp right))

    bp))

(defn label
  ([text] (label text nil))
  ([text class]
   (let [lbl (Label. text)]
     (when class
       (add-class lbl class))
     lbl)))

(defn tab [{:keys [text graphic class content on-selection-changed]}]
  (let [t (Tab.)]

    (if graphic
      (.setGraphic t graphic)
      (.setText t text))

    (.add (.getStyleClass t) class)
    (.setContent t content)

    (when on-selection-changed
      (.setOnSelectionChanged t on-selection-changed))
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

(defn tab-pane [{:keys [tabs rotate? side closing-policy]
                 :or {closing-policy :unavailable
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
                                 closing-policy)))

    (when tabs
      (.addAll tabs-list tabs))

    tp))

(defn list-view [{:keys [editable? cell-factory-fn on-click selection-mode search-predicate]
                  :or {editable? false
                       selection-mode :multiple}}]
  (let [observable-list (FXCollections/observableArrayList)
        ;; by default create a constantly true predicate
        observable-filtered-list (FilteredList. observable-list)
        lv (ListView. observable-filtered-list)
        list-selection (.getSelectionModel lv)
        add-all (fn [elems] (.addAll observable-list (into-array Object elems)))
        remove-all (fn [elems] (.removeAll observable-list (into-array Object elems)))
        clear (fn [] (.clear observable-list))
        get-all-items (fn [] (seq observable-list))
        selected-items (fn [] (.getSelectedItems list-selection))

        search-field (TextField.)
        search-bar (h-box [search-field (doto (Label.) (.setGraphic (icon "mdi-magnify")))])

        box (v-box (cond-> []
                     search-predicate (conj search-bar)
                     true (conj lv)))
        list-view-data {:list-view-pane box
                        :add-all add-all
                        :clear clear
                        :get-all-items get-all-items
                        :remove-all remove-all}]

    (HBox/setHgrow search-field Priority/ALWAYS)
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

    (when on-click
      (.setOnMouseClicked
       lv
       (event-handler
        [mev]
        (on-click mev (selected-items) list-view-data))))

    list-view-data)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NOT USING ALL THIS NOW !!! just experiments

;;-------------------------------------------------------------------------
;; Some experiments on reflective FX object creation
;;-------------------------------------------------------------------------

(def class-map {:Button "javafx.scene.control.Button"
                :Label  "javafx.scene.control.Label"})

(defn- class* [obj]
  (let [clazz (class obj)]
    (cond
      (= clazz java.lang.Long) java.lang.Long/TYPE
      (= clazz java.lang.Integer) java.lang.Integer/TYPE
      (= clazz java.lang.Double) java.lang.Double/TYPE
      :else clazz)))

;; TODO: memoize this
(defn- get-constructor [class-name ctor-vec]
  (let [clazz (clojure.lang.RT/classForName class-name
                                            false
                                            (.getContextClassLoader (Thread/currentThread)))]
    (.getConstructor clazz (into-array Class (mapv class* ctor-vec)))))

(defn- class-methods [clazz methods-sigs]
  (reduce-kv (fn [r mkey args-vec]
            (assoc r mkey (.getMethod clazz (name mkey) (into-array Class args-vec))))
   {}
   methods-sigs))

(defn- set-instance-props [obj props-map]
  (let [clazz (class obj)
        methods-sigs (reduce-kv
                      (fn [r mk args-vec]
                        (assoc r mk (mapv class* args-vec)))
                      {}
                      props-map)
        methods-map (class-methods clazz methods-sigs)]
    (doseq [mkey (keys props-map)]
      (let [method (get methods-map mkey)
            args-vec (get props-map mkey)]
        (.invoke method obj (into-array Object args-vec))))
    obj))

(defn ->fx [{:keys [type ctor] :as obj-desc}]
  (let [class-name (class-map type)
        ctor-obj (get-constructor class-name ctor)
        obj-instance (.newInstance ctor-obj (into-array Object ctor))]
    (set-instance-props obj-instance (dissoc obj-desc :type :ctor))))

(comment

  (def l (->fx {:type :Label
                :ctor ["Hello"]
                :setPrefWidth [(double 10)]}))

  ;; Meh, it works but it is worse than

  (def l (doto (Label. "Hello")
           (.setPrefWidth 10)))

  )
