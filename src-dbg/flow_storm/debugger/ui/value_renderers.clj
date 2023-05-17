(ns flow-storm.debugger.ui.value-renderers
  (:require [clojure.string :as str]
            [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [event-handler button label v-box table-view]])
  (:import [javafx.scene.layout HBox VBox Priority]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NOTE :                                                                                              ;;
;;                                                                                                     ;;
;; This is not implemented yet. I think a good renderers implementation should be                      ;;
;; compatible with Morse viewers since both are javafx.                                                ;;
;; For this to happen something like replicant should be implemented on FlowStorm,                     ;;
;; which means create types on the client side for maps, vectors, sets, etc that implement             ;;
;; Clojure protocols and interfaces and which transparently handle remote sub values retrieval.        ;;
;; Currently I can't just use replicant because it doesn't have ClojureScript support, and the current ;;
;; remove values implementation is not transparent, the user handling the values needs to know about   ;;
;; this shallow values format.                                                                         ;;
;; So we will probably end re-implementing replicant stuff here with ClojureScript support.            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def renderers-registry (atom {}))

(defn register-renderer [id-key browser? pred render-fn]
  (swap! renderers-registry assoc id-key {:browser? browser? :pred pred :render-fn render-fn}))

(defn renderers-for-val [v]
  (reduce-kv (fn [rr idk {:keys [pred] :as r}]
               (if (pred v)
                 (assoc rr idk r)
                 rr))
             {}
             @renderers-registry))

(defn renderer [id-key]
  (get @renderers-registry id-key))

(defn load-morse-renderers []
  nil)

(defn create-browsable-node [{:keys [val-txt] :as item} on-selected]
  (let [node-in-prev-pane? (fn [node]
                             (loop [n node]
                               (when n
                                 (if (= "prev-pane" (.getId n))
                                   true
                                   (recur (.getParent n))))))

        click-handler (event-handler
                       [mev]
                       (on-selected item (node-in-prev-pane? (.getSource mev))))
        lbl (doto (label val-txt "link-lbl")
              (.setOnMouseClicked click-handler))]
    {:node-obj lbl
     :click-handler click-handler}))

(defn make-node [{:keys [browsable-val? val-txt] :as item} on-selected]
  (if browsable-val?
    (create-browsable-node item on-selected)
    {:node-obj (label val-txt)}))

(defn create-map-browser-pane [amap on-selected]
  (let [{:keys [table-view-pane table-view]}
        (table-view {:columns ["Key" "Value"]
                     :cell-factory-fn (fn [item]
                                        (:node-obj (make-node item on-selected)))
                     :search-predicate (fn [[k-item v-item] search-str]
                                         (boolean
                                          (or (str/includes? (:val-txt k-item) search-str)
                                              (str/includes? (:val-txt v-item) search-str))))
                     :items amap})]
    (VBox/setVgrow table-view Priority/ALWAYS)
    (VBox/setVgrow table-view-pane Priority/ALWAYS)
    (HBox/setHgrow table-view-pane Priority/ALWAYS)
    table-view-pane))

(defn create-seq-browser-pane [seq-page load-next-page on-selected]
  (let [{:keys [list-view-pane add-all]} (ui-utils/list-view
                                          {:editable? false
                                           :cell-factory-fn (fn [list-cell item]
                                                              (let [{:keys [node-obj click-handler]} (make-node item on-selected)]
                                                                (.setText list-cell nil)
                                                                (.setGraphic list-cell node-obj)
                                                                ;; the node-obj will already handle the click
                                                                ;; but we also add the handler to the list-cell
                                                                ;; so one single click executes the action instead of
                                                                ;; selecting the cell
                                                                (.setOnMouseClicked list-cell click-handler)))
                                           :search-predicate (fn [item search-str]
                                                               (str/includes? (:val-txt item) search-str))})
        more-button (when load-next-page (button :label "More.."))
        arm-more-button (fn arm-more-button [load-next]
                          (doto more-button
                            (.setOnAction (event-handler
                                           [_]
                                           (let [next-page (load-next)]
                                             (add-all (:page next-page))
                                             (if-let [loadn (:load-next next-page)]
                                               (arm-more-button loadn)
                                               (doto more-button
                                                 (.setDisable true)
                                                 (.setOnAction (event-handler [_])))))))))

        container (v-box (cond-> [list-view-pane]
                           more-button (conj more-button)))]
    (VBox/setVgrow list-view-pane Priority/ALWAYS)
    (HBox/setHgrow container Priority/ALWAYS)
    (add-all seq-page)
    (when load-next-page (arm-more-button load-next-page))
    container))
