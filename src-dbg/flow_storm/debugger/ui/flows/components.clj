(ns flow-storm.debugger.ui.flows.components
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler label h-box v-box button]]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]])
  (:import [javafx.scene.control CheckBox TextField TextArea]
           [javafx.scene.layout VBox Priority]
           [javafx.geometry Pos]))

(defn def-kind-colored-label [text kind]
  (case kind
    :defmethod       (label text "defmethod")
    :extend-protocol (label text "extend-protocol")
    :extend-type     (label text "extend-type")
    :defn            (label text "defn")
    (label text "anonymous")))

(defn create-pprint-pane [flow-id thread-id pane-id]
  (let [result-text-area (doto (TextArea.)
                           (.setEditable false))
        print-meta-chk (doto (CheckBox.)
                         (.setSelected false))
        print-level-txt (doto (TextField. "5")
                          (.setPrefWidth 50)
                          (.setAlignment Pos/CENTER))
        def-btn (button :label "def"
                        :class "def-btn"
                        :tooltip "Define a reference to this value so it can be used from the repl.")
        inspect-btn (button :label "ins"
                            :class "def-btn"
                            :tooltip "Open this value in the value inspector.")
        tap-btn (button :label "tap"
                        :class "def-btn"
                        :tooltip "Tap this value as with tap>. Useful to send it to other inspectors like portal, REBL, Reveal, etc")
        tools-box (doto (h-box [(label "*print-level*") print-level-txt
                                (label "*print-meta*") print-meta-chk
                                def-btn
                                inspect-btn
                                tap-btn])
                    (.setAlignment Pos/CENTER_RIGHT)
                    (.setSpacing 3.0))
        box (v-box [tools-box result-text-area])]
    (VBox/setVgrow result-text-area Priority/ALWAYS)
    (store-obj flow-id thread-id (ui-vars/thread-pprint-text-area-id pane-id) result-text-area)
    (store-obj flow-id thread-id (ui-vars/thread-pprint-level-txt-id pane-id) print-level-txt)
    (store-obj flow-id thread-id (ui-vars/thread-pprint-meta-chk-id pane-id) print-meta-chk)
    (store-obj flow-id thread-id (ui-vars/thread-pprint-def-btn-id pane-id) def-btn)
    (store-obj flow-id thread-id (ui-vars/thread-pprint-inspect-btn-id pane-id) inspect-btn)
    (store-obj flow-id thread-id (ui-vars/thread-pprint-tap-btn-id pane-id) tap-btn)
    box))

(defn update-pprint-pane [flow-id thread-id pane-id val]
  (let [[^TextArea text-area] (obj-lookup flow-id thread-id (ui-vars/thread-pprint-text-area-id pane-id))
        [print-level-txt] (obj-lookup flow-id thread-id (ui-vars/thread-pprint-level-txt-id pane-id))
        [print-meta-chk]  (obj-lookup flow-id thread-id (ui-vars/thread-pprint-meta-chk-id pane-id))
        [def-btn] (obj-lookup flow-id thread-id (ui-vars/thread-pprint-def-btn-id pane-id))
        [inspect-btn] (obj-lookup flow-id thread-id (ui-vars/thread-pprint-inspect-btn-id pane-id))
        [tap-btn] (obj-lookup flow-id thread-id (ui-vars/thread-pprint-tap-btn-id pane-id))
        val-str (when val
                  (runtime-api/val-pprint rt-api val {:print-length 50
                                                      :print-level (Integer/parseInt (.getText print-level-txt))
                                                      :print-meta? (.isSelected print-meta-chk)
                                                      :pprint? true}))]
    (.setOnAction def-btn (event-handler [_] (value-inspector/def-val val)))
    (.setOnAction inspect-btn (event-handler [_] (value-inspector/create-inspector val)))
    (.setOnAction tap-btn (event-handler [_] (runtime-api/tap-value rt-api val)))
    (.setText text-area val-str)))
