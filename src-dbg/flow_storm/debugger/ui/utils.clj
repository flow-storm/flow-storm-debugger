(ns flow-storm.debugger.ui.utils
  (:require [flow-storm.utils :refer [log-error]])
  (:import [javafx.scene.control Button ContextMenu Label ListCell MenuItem ScrollPane Tab]
           [javafx.scene.layout HBox VBox]
           [javafx.scene Node]
           [org.kordamp.ikonli.javafx FontIcon]))

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

(defn tab [text class]
  (let [t (Tab. text)]
    (.add (.getStyleClass t) class)
    t))

(defn add-class [node class]
  (.add (.getStyleClass node) class))

(defn rm-class [node class]
  (.remove (.getStyleClass node) class))

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

(defn label
  ([text] (label text nil))
  ([text class]
   (let [lbl (Label. text)]
     (when class
       (add-class lbl class))
     lbl)))
