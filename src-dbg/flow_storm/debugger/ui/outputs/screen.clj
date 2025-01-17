(ns flow-storm.debugger.ui.outputs.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.tasks :as tasks]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows])
  (:import [javafx.scene.layout Priority VBox HBox]
           [javafx.scene.control ListView TextArea]))

(defn- update-outputs-data-window [val-ref stack-key]
  (runtime-api/data-window-push-val-data rt-api
                                         :outputs
                                         val-ref
                                         {:flow-storm.debugger.ui.data-windows.data-windows/dw-id :outputs
                                          :flow-storm.debugger.ui.data-windows.data-windows/stack-key stack-key
                                          :root? true}))

(defn update-last-evals [last-evals-refs]
  (let [[{:keys [add-all clear]}] (obj-lookup "last-vals-list-view-data")
        last-ref (last last-evals-refs)]
    (clear)
    (update-outputs-data-window last-ref "eval")
    (add-all last-evals-refs)))

(defn add-out-write [msg]
  (let [[^TextArea out-and-err-txt] (obj-lookup "out-and-err-text-area")]
    (.appendText out-and-err-txt msg)))

(defn add-err-write [msg]
  (let [[^TextArea out-and-err-txt] (obj-lookup "out-and-err-text-area")]
    (.appendText out-and-err-txt msg)))

(defn add-tap-value [val-ref]
  (let [[{:keys [add-all ^ListView list-view]}] (obj-lookup "taps-list-view-data")
        lv-size (count (.getItems list-view))]
    (update-outputs-data-window val-ref "tap")
    (add-all [val-ref])
    (.scrollTo list-view lv-size)))

(defn clear-outputs-ui []
  (ui-utils/run-later
    (let [[{:keys [clear]}] (obj-lookup "taps-list-view-data")]
      (clear))
    (let [[out-and-error-txt] (obj-lookup "out-and-err-text-area")]
      (.setText out-and-error-txt ""))
    (let [[{:keys [clear]}] (obj-lookup "last-vals-list-view-data")]
      (clear))))

(defn set-middleware-not-available []
  ;; This is kind of hacky but an easy way of letting the user know it
  ;; needs the middleware for this functionality
  (let [txt "This functionality is only available for Clojure and needs flow-storm nrepl middleware available."
        [out-and-error-txt] (obj-lookup "out-and-err-text-area")
        [last-vals-lv-data] (obj-lookup "last-vals-list-view-data")]
    (.setText out-and-error-txt txt)
    ((:add-all last-vals-lv-data) [(with-meta [] {:val-preview txt})])))

(defn find-and-jump-tap-val [vref]
  (tasks/submit-task runtime-api/find-expr-entry-task
                     [{:from-idx 0
                       :identity-val vref}]
                     {:on-finished (fn [{:keys [result]}]
                                     (when result
                                       (ui-general/select-main-tools-tab "tool-flows")
                                       (flows-screen/goto-location result)))}))

(defn clear-outputs []
  (runtime-api/clear-outputs rt-api)
  (clear-outputs-ui))

(defn main-pane []
  (let [last-evals-lv-data
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell val-ref]
                                      (let [val-list-text (-> val-ref meta :val-preview utils/remove-newlines)]
                                        (-> list-cell
                                            (ui-utils/set-text nil)
                                            (ui-utils/set-graphic (ui/label :text val-list-text)))))
                      :on-click (fn [mev sel-items _]
                                  (let [val-ref (first sel-items)]
                                    (cond
                                      (ui-utils/mouse-primary? mev)
                                      (update-outputs-data-window val-ref "eval"))))
                      :selection-mode :single)

        out-and-err-txt (ui/text-area :text ""
                                      :editable? false
                                      :class "monospaced")

        taps-lv-data
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell val-ref]
                                      (let [val-list-text (-> val-ref
                                                              meta
                                                              :val-preview
                                                              utils/remove-newlines)]
                                        (-> list-cell
                                            (ui-utils/set-text nil)
                                            (ui-utils/set-graphic (ui/label :text val-list-text)))))
                      :on-click (fn [mev sel-items {:keys [list-view]}]
                                  (let [val-ref (first sel-items)]
                                    (cond
                                      (ui-utils/mouse-primary? mev)
                                      (update-outputs-data-window val-ref "tap")

                                      (ui-utils/mouse-secondary? mev)
                                      (let [ctx-menu (ui/context-menu
                                                      :items [{:text "Search value on Flows"
                                                               :on-click (fn [] (find-and-jump-tap-val val-ref))}])]
                                        (ui-utils/show-context-menu :menu ctx-menu
                                                                    :parent list-view
                                                                    :mouse-ev mev)))))
                      :selection-mode :single)

        out-and-err-lv-pane (ui/v-box :childs [(ui/label :text "*out* and *err*")
                                               out-and-err-txt])
        last-evals-lv (:list-view last-evals-lv-data)
        taps-lv (:list-view taps-lv-data)

        val-dw-pane (ui/v-box :childs [(data-windows/data-window-pane {:data-window-id :outputs})]
                              :paddings [10 10 10 10]
                              :class "outputs-dw")

        last-vals-box (ui/v-box :childs [(ui/label :text "Last evals") last-evals-lv]

                                :spacing 5)
        taps-box (ui/v-box :childs [(ui/label :text "Taps") taps-lv]
                           :spacing 5)
        last-vals-and-taps (ui/h-box :childs [last-vals-box taps-box])

        split-pane (ui/split :orientation :vertical
                             :childs [val-dw-pane
                                      last-vals-and-taps
                                      out-and-err-lv-pane]
                             :sizes [0.4 0.3 0.3]
                             )
        clear-btn (ui/icon-button :icon-name  "mdi-delete-forever"
                                  :tooltip "Clean all outputs (Ctrl-l)"
                                  :on-click (fn [] (clear-outputs)))
        controls (ui/h-box :childs [clear-btn]
                           :paddings [10 10 10 10])
        main-p (ui/border-pane :top controls
                               :center split-pane)]

    (VBox/setVgrow out-and-err-txt Priority/ALWAYS)
    (VBox/setVgrow taps-lv        Priority/ALWAYS)
    (HBox/setHgrow taps-lv        Priority/ALWAYS)
    (VBox/setVgrow last-evals-lv  Priority/ALWAYS)
    (HBox/setHgrow last-evals-lv  Priority/ALWAYS)
    (HBox/setHgrow last-vals-and-taps  Priority/ALWAYS)
    (HBox/setHgrow last-vals-box  Priority/ALWAYS)
    (HBox/setHgrow taps-box       Priority/ALWAYS)

    (VBox/setVgrow split-pane Priority/ALWAYS)
    (VBox/setVgrow main-p Priority/ALWAYS)

    (store-obj "taps-list-view-data" taps-lv-data)
    (store-obj "out-and-err-text-area" out-and-err-txt)
    (store-obj "last-vals-list-view-data" last-evals-lv-data)

    main-p))

(comment
  (add-tap-value {:a 1 :b 2})
  (add-tap-value {:a 1 :b 3})
  )
