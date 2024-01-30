(ns flow-storm.debugger.ui.flows.components
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler label h-box v-box button text-area check-box]]
            [flow-storm.debugger.ui.value-inspector :as value-inspector]
            [flow-storm.debugger.state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]])
  (:import [javafx.scene.control CheckBox TextField]
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
  (let [result-txt (text-area "" {:editable? false})
        print-meta-chk (doto (CheckBox.)
                         (.setSelected false))
        print-level-txt (doto (TextField. "5")
                          (.setPrefWidth 50)
                          (.setAlignment Pos/CENTER))
        print-wrap-chk (doto (check-box {:on-change (fn [selected?] (.setWrapText result-txt selected?))})
                             (.setSelected false))
        def-btn (button :label "def"
                        :classes ["def-btn" "btn-sm"]
                        :tooltip "Define a reference to this value so it can be used from the repl.")
        inspect-btn (button :label "ins"
                            :classes ["def-btn" "btn-sm"]
                            :tooltip "Open this value in the value inspector.")
        tap-btn (button :label "tap"
                        :classes ["def-btn" "btn-sm"]
                        :tooltip "Tap this value as with tap>. Useful to send it to other inspectors like portal, REBL, Reveal, etc")
        tools-box (doto (h-box [(label "*print-level*") print-level-txt
                                (label "*print-meta*") print-meta-chk
                                (label "*print-wrap*") print-wrap-chk
                                def-btn
                                inspect-btn
                                tap-btn])
                    (.setAlignment Pos/CENTER_RIGHT)
                    (.setSpacing 3.0))
        result-type-lbl (label "")
        extra-lbl (label "")
        header-box (doto (h-box [result-type-lbl extra-lbl])
                     (.setSpacing 3.0))
        box (v-box [tools-box
                    header-box
                    result-txt])]

    (VBox/setVgrow result-txt Priority/ALWAYS)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-type-lbl-id pane-id) result-type-lbl)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-extra-lbl-id pane-id) extra-lbl)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-area-id pane-id) result-txt)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-level-txt-id pane-id) print-level-txt)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-meta-chk-id pane-id) print-meta-chk)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-def-btn-id pane-id) def-btn)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-inspect-btn-id pane-id) inspect-btn)
    (store-obj flow-id thread-id (ui-utils/thread-pprint-tap-btn-id pane-id) tap-btn)
    box))

(defn update-pprint-pane [flow-id thread-id pane-id val opts]
  (let [[result-type-lbl] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-type-lbl-id pane-id))
        [result-txt] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-area-id pane-id))
        [print-level-txt] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-level-txt-id pane-id))
        [print-meta-chk]  (obj-lookup flow-id thread-id (ui-utils/thread-pprint-meta-chk-id pane-id))
        [def-btn] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-def-btn-id pane-id))
        [inspect-btn] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-inspect-btn-id pane-id))
        [tap-btn] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-tap-btn-id pane-id))
        {:keys [val-str val-type]} (when val
                                     (runtime-api/val-pprint rt-api val {:print-length 50
                                                                         :print-level (Integer/parseInt (.getText print-level-txt))
                                                                         :print-meta? (.isSelected print-meta-chk)
                                                                         :pprint? true}))]
    (.setOnAction def-btn (event-handler [_] (value-inspector/def-val val)))
    (.setOnAction inspect-btn (event-handler [_] (value-inspector/create-inspector val opts)))
    (.setOnAction tap-btn (event-handler [_] (runtime-api/tap-value rt-api val)))

    (.setText result-txt val-str)
    (.setText result-type-lbl (format "Type: %s" val-type))))

(defn update-return-pprint-pane [flow-id thread-id pane-id kind val opts]
  (let [[extra-lbl] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-extra-lbl-id pane-id))]
    (case kind
      :waiting (do
                 (.setText extra-lbl "Waiting")
                 (ui-utils/rm-class extra-lbl "fail")
                 (ui-utils/add-class extra-lbl "warning"))
      :unwind (do
                (.setText extra-lbl "Throwed")
                (ui-utils/rm-class extra-lbl "warning")
                (ui-utils/add-class extra-lbl "fail"))
      :return (do
                (ui-utils/rm-class extra-lbl "fail")
                (ui-utils/rm-class extra-lbl "warning")
                (.setText extra-lbl "")))
    (update-pprint-pane flow-id thread-id pane-id val opts)))
