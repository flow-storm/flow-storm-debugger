(ns flow-storm.debugger.ui.flows.components
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.ui.commons :refer [def-val]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows]
            [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup]])
  (:import [javafx.scene.layout VBox Priority]
           [javafx.scene.control TextArea TextField]))


(defn def-kind-colored-label [text kind]
  (case kind
    :defmethod       (ui/label :text text :class "defmethod")
    :extend-protocol (ui/label :text text :class "extend-protocol")
    :extend-type     (ui/label :text text :class "extend-type")
    :defn            (ui/label :text text :class "defn")
    #_else           (ui/label :text text :class "anonymous")))

(defn create-pprint-pane [flow-id thread-id pane-id]
  (let [^TextArea result-txt (ui/text-area
                              :text ""
                              :editable? false
                              :class "value-pprint")
        print-meta-chk (ui/check-box :selected? false)
        print-level-txt  (ui/text-field :initial-text "5"
                                        :align :center
                                        :pref-width 50)

        print-wrap-chk (ui/check-box :on-change (fn [selected?] (.setWrapText result-txt selected?))
                                     :selected? false)

        def-btn (ui/button :label "def"
                           :classes ["def-btn" "btn-sm"]
                           :tooltip "Define a reference to this value so it can be used from the repl.")
        inspect-btn (ui/button :label "ins"
                               :classes ["def-btn" "btn-sm"]
                               :tooltip "Open this value in the value inspector.")
        tap-btn (ui/button :label "tap"
                           :classes ["def-btn" "btn-sm"]
                           :tooltip "Tap this value as with tap>. Useful to send it to other inspectors like portal, REBL, Reveal, etc")
        tools-box (ui/h-box :childs [(ui/label :text "*print-level*") print-level-txt
                                     (ui/label :text "*print-meta*") print-meta-chk
                                     (ui/label :text "*print-wrap*") print-wrap-chk
                                     def-btn
                                     inspect-btn
                                     tap-btn]
                            :spacing 3
                            :align :center-right)

        result-type-lbl (ui/label :text "")
        extra-lbl (ui/label :text "")
        header-box (ui/h-box :childs [result-type-lbl extra-lbl]
                             :spacing 3)

        box (ui/v-box
             :childs [tools-box
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

(defn update-pprint-pane [flow-id thread-id pane-id {:keys [val-ref extra-text class]} _]
  (let [[result-type-lbl] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-type-lbl-id pane-id))
        [result-txt] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-area-id pane-id))
        [print-level-txt] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-level-txt-id pane-id))
        [print-meta-chk]  (obj-lookup flow-id thread-id (ui-utils/thread-pprint-meta-chk-id pane-id))
        [def-btn] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-def-btn-id pane-id))
        [inspect-btn] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-inspect-btn-id pane-id))
        [tap-btn] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-tap-btn-id pane-id))
        [extra-lbl] (obj-lookup flow-id thread-id (ui-utils/thread-pprint-extra-lbl-id pane-id))

        {:keys [val-str val-type]} (when val-ref
                                     (runtime-api/val-pprint rt-api val-ref {:print-length 50
                                                                             :print-level (Integer/parseInt (.getText ^TextField print-level-txt))
                                                                             :print-meta? (ui-utils/checkbox-checked? print-meta-chk)
                                                                             :pprint? (:pprint-previews? (dbg-state/debugger-config))}))]
    (ui-utils/set-button-action def-btn (fn [] (def-val val-ref)))
    (ui-utils/set-button-action inspect-btn (fn [] (data-windows/create-data-window-for-vref val-ref)))
    (ui-utils/set-button-action tap-btn (fn [] (runtime-api/tap-value rt-api val-ref)))

    (ui-utils/set-text extra-lbl (or extra-text ""))
    (case class
      :warning (do
                 (ui-utils/rm-class extra-lbl "fail")
                 (ui-utils/add-class extra-lbl "warning"))
      :fail (do
              (ui-utils/rm-class extra-lbl "warning")
              (ui-utils/add-class extra-lbl "fail"))
      (do
        (ui-utils/rm-class extra-lbl "fail")
        (ui-utils/rm-class extra-lbl "warning")))

    (ui-utils/set-text-input-text result-txt val-str)
    (ui-utils/set-text result-type-lbl (format "Type: %s" (or val-type "")))))
