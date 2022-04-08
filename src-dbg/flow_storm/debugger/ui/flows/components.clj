(ns flow-storm.debugger.ui.flows.components
  (:require [clojure.pprint :as pp]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [label]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars])
  (:import [javafx.scene.control TextArea]))

(defn def-kind-colored-label [text kind]
  (case kind
    :defmethod       (label text "defmethod")
    :extend-protocol (label text "extend-protocol")
    :extend-type     (label text "extend-type")
    :defn            (label text "defn")
    (label text "anonymous")))

(defn format-value-short [v]
  (let [max-len 80
        s (binding [clojure.core/*print-level* 3
                    clojure.core/*print-length* 3]
            (pr-str v))
        len (count s)]
    (cond-> (subs s 0 (min max-len len))
      (> len max-len) (str " ... "))))

(defn create-pprint-pane [flow-id thread-id pane-id]
  (let [result-text-area (doto (TextArea.)
                           (.setEditable false))]
    (store-obj flow-id (ui-vars/thread-pprint-text-area-id thread-id pane-id) result-text-area)
    result-text-area))

(defn create-result-tree-pane [_ _]
  (label "TREE"))

(defn update-pprint-pane [flow-id thread-id pane-id val]
  ;; TODO: find and update the tree
  (let [[^TextArea text-area] (obj-lookup flow-id (ui-vars/thread-pprint-text-area-id thread-id pane-id))
        val-str (with-out-str
                  (binding [clojure.core/*print-level* 7
                            clojure.core/*print-length* 50]
                    (pp/pprint val)))]
    (.setText text-area val-str)))
