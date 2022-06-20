(ns flow-storm.debugger.ui.flows.components
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler label h-box v-box button]]
            [flow-storm.debugger.target-commands :as target-commands]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.trace-types :refer [deref-ser local-imm-value? remote-imm-value?]]
            [clojure.string :as str])
  (:import [javafx.scene.control CheckBox TextField TextArea TextInputDialog]
           [javafx.geometry Pos]))

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
                           (.setEditable false))
        print-meta-chk (doto (CheckBox.)
                         (.setSelected false))
        print-level-txt (doto (TextField. "5")
                          (.setPrefWidth 50)
                          (.setAlignment Pos/CENTER))
        def-btn (button "def" "def-btn")
        tools-box (doto (h-box [(label "*print-level*") print-level-txt
                                (label "*print-meta*") print-meta-chk
                                def-btn])
                    (.setAlignment Pos/CENTER_RIGHT)
                    (.setSpacing 3.0))
        box (v-box [tools-box result-text-area])]
    (store-obj flow-id (ui-vars/thread-pprint-text-area-id thread-id pane-id) result-text-area)
    (store-obj flow-id (ui-vars/thread-pprint-level-txt-id thread-id pane-id) print-level-txt)
    (store-obj flow-id (ui-vars/thread-pprint-meta-chk-id thread-id pane-id) print-meta-chk)
    (store-obj flow-id (ui-vars/thread-pprint-def-btn-id thread-id pane-id) def-btn)
    box))

(defn create-result-tree-pane [_ _]
  (label "TREE"))

(defn def-val [val]
  (let [tdiag (doto (TextInputDialog.)
                (.setHeaderText "Def var with name (in Clojure under user/ namespace and in ClojureScript under js/) ")
                (.setContentText "Var name :"))
        _ (.showAndWait tdiag)
        val-name (let [txt (-> tdiag .getEditor .getText)]
                   (if (str/blank? txt)
                     "val0"
                     txt))]
    (cond
      (local-imm-value? val)
      (target-commands/run-command :def-value {:val (:val val)
                                               :val-name val-name})

      (remote-imm-value? val)
      (target-commands/run-command :def-remote-value {:vid (:vid val)
                                                      :val-name val-name}))))

(defn update-pprint-pane [flow-id thread-id pane-id val]
  (let [[^TextArea text-area] (obj-lookup flow-id (ui-vars/thread-pprint-text-area-id thread-id pane-id))
        [print-level-txt] (obj-lookup flow-id (ui-vars/thread-pprint-level-txt-id thread-id pane-id))
        [print-meta-chk]  (obj-lookup flow-id (ui-vars/thread-pprint-meta-chk-id thread-id pane-id))
        [def-btn] (obj-lookup flow-id (ui-vars/thread-pprint-def-btn-id thread-id pane-id))
        val-str (when val (deref-ser val {:print-length 50
                                          :print-level (Integer/parseInt (.getText print-level-txt))
                                          :print-meta? (.isSelected print-meta-chk)
                                          :pprint? true}))]
    (.setOnAction def-btn (event-handler [_] (def-val val)))
    (.setText text-area val-str)))
