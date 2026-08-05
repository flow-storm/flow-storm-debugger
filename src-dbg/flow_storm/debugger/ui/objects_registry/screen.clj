(ns flow-storm.debugger.ui.objects-registry.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows])
  (:import [javafx.scene.layout Priority VBox HBox]))

(defn- update-registry-data-window [val-ref]
  (runtime-api/data-window-push-val-data rt-api
                                         :objects-registry
                                         val-ref
                                         {:dw-id :objects-registry
                                          :stack-key "registry"
                                          :root? true}))

(defn set-register-enable [enable?]
  (ui-utils/run-later
    (let [[toggle] (obj-lookup "registry-toggle-btn")]
      (.setSelected toggle enable?))))

(defn update-registry [registry]
  (ui-utils/run-later
    (let [[{:keys [add-all clear]}] (obj-lookup "registry-list-view-data")
          last-ref (last registry)]
      (clear)
      (update-registry-data-window last-ref)
      (add-all registry))))

(defn clear-registry []
  (ui-utils/run-later
    (runtime-api/clear-objects-registry rt-api)
    (let [[{:keys [clear]}] (obj-lookup "registry-list-view-data")]
      (clear))))

(defn main-pane []
  (let [registry-toggle (ui/toggle-button {:label "Registry Enable"
                                           :on-change (fn [on?]
                                                        (runtime-api/turn-objects-registry rt-api on?))})
        registry-lv-data
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
                                      (update-registry-data-window val-ref))))
                      :selection-mode :single)

        registry-lv (:list-view registry-lv-data)

        val-dw-pane (ui/v-box :childs [(data-windows/data-window-pane {:data-window-id :objects-registry})]
                              :paddings [10 10 10 10]
                              :class "objects-registry-dw")

        registry-box (ui/v-box :childs [(ui/label :text "Objects registry") registry-lv]

                                :spacing 5)
        split-pane (ui/split :orientation :vertical
                             :childs [val-dw-pane
                                      registry-box]
                             :sizes [0.4 0.3 0.3])
        clear-btn (ui/icon-button :icon-name  "mdi-delete-forever"
                                  :tooltip "Clean all objects registry (Ctrl-l)"
                                  :on-click (fn [] (clear-registry)))
        controls (ui/h-box :childs [clear-btn registry-toggle]
                           :spacing 10
                           :paddings [10 10 10 10])
        main-p (ui/border-pane :top controls
                               :center split-pane)]

    (VBox/setVgrow registry-lv  Priority/ALWAYS)
    (HBox/setHgrow registry-lv  Priority/ALWAYS)

    (VBox/setVgrow split-pane Priority/ALWAYS)
    (VBox/setVgrow main-p Priority/ALWAYS)

    (store-obj "registry-list-view-data" registry-lv-data)
    (store-obj "registry-toggle-btn" registry-toggle)

    main-p))

(comment

  )
