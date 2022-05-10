(ns flow-storm.debugger.ui.flows.components
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [label]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.trace-types :refer [deref-ser]])
  (:import [javafx.scene.control TextArea]))

(defn def-kind-colored-label [text kind]
  (case kind
    :defmethod       (label text "defmethod")
    :extend-protocol (label text "extend-protocol")
    :extend-type     (label text "extend-type")
    :defn            (label text "defn")
    (label text "anonymous")))

(defn elide-string [s max-len]
  (let [len (count s)]
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
        val-str (when val (deref-ser val {:print-length 50 :print-level 7 :pprint? true}))]
    (.setText text-area val-str)))
