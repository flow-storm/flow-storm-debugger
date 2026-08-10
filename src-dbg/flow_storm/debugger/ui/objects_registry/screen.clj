(ns flow-storm.debugger.ui.objects-registry.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows]
            [clojure.string :as str])
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
    (let [[{:keys [add-all clear]}] (obj-lookup "registry-table-view-data")
          {:keys [obj-ref]} (last registry)
          rows (mapv (fn [obj-data]
                       [(assoc obj-data :cell-type :object)
                        (assoc obj-data :cell-type :form)
                        (assoc obj-data :cell-type :file)
                        (assoc obj-data :cell-type :line)])
                     registry)]
      (clear)
      (update-registry-data-window obj-ref)
      (add-all rows))))

(defn clear-registry []
  (ui-utils/run-later
    (runtime-api/clear-objects-registry rt-api)
    (let [[{:keys [clear]}] (obj-lookup "registry-table-view-data")]
      (clear))))

(defn main-pane []
  (let [registry-toggle (ui/toggle-button {:label "Registry Enable"
                                           :on-change (fn [on?]
                                                        (runtime-api/turn-objects-registry rt-api on?))})
        registry-tv-data
        (ui/table-view
         :columns ["Object" "Form" "File" "Line"]
         :cell-factory (fn [_ {:keys [cell-type obj-ref obj-sub-form file line]}]
                         (let [obj-str (some-> obj-ref meta :val-preview utils/remove-newlines)
                               cell-obj (case cell-type
                                          :object (ui/label :text obj-str :class "link-lbl")
                                          :form (ui/label :text (str obj-sub-form) :class "link-lbl")
                                          :file (ui/label :text file :class "link-lbl")
                                          :line (ui/label :text (str line) :class "link-lbl"))]
                           cell-obj))
         :on-click (fn [mev sel-items _]
                     (when (ui-utils/mouse-primary? mev)
                       (let [{:keys [obj-ref]} (-> sel-items ffirst)]
                         (update-registry-data-window obj-ref))))
         :selection-mode :single
         :columns-width-percs [0.4 0.3 0.2 0.1]
         :resize-policy :constrained
         :items []
         :search-predicate (fn [{:keys [obj-ref obj-sub-form]} search-str]
                             (let [obj-str (-> obj-ref meta :val-preview utils/remove-newlines)]
                               (or (str/includes? obj-str search-str)
                                   (str/includes? obj-sub-form search-str)))))

        registry-tv (:table-view registry-tv-data)

        val-dw-pane (ui/v-box :childs [(data-windows/data-window-pane {:data-window-id :objects-registry})]
                              :paddings [10 10 10 10]
                              :class "objects-registry-dw")

        registry-box (ui/v-box :childs [(ui/label :text "Objects registry") registry-tv]
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

    (VBox/setVgrow registry-tv  Priority/ALWAYS)
    (HBox/setHgrow registry-tv  Priority/ALWAYS)

    (VBox/setVgrow split-pane Priority/ALWAYS)
    (VBox/setVgrow main-p Priority/ALWAYS)

    (store-obj "registry-table-view-data" registry-tv-data)
    (store-obj "registry-toggle-btn" registry-toggle)

    main-p))

(comment

  )
