(ns flow-storm.debugger.ui.data-windows.visualizers
  (:require [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]))

(defonce *visualizers (atom {}))
(defonce *types->defaults (atom {}))

(defn register-visualizer [{:keys [id] :as viz}]
  (swap! *visualizers assoc id viz))

(defn visualizers []
  @*visualizers)

(defn visualizer [viz-id]
  (get @*visualizers viz-id))

(defn appliable-visualizers [val-data]
  (->> (vals (visualizers))
       (filter (fn [{:keys [pred]}] (pred val-data)))))

(defn make-visualizer-for-val [viz-id val]
  (let [{:keys [on-create]} (visualizer viz-id)
        viz-ctx (on-create val)]
    viz-ctx))

(defn set-default-visualizer [val-type-symb viz-id]
  (swap! *types->defaults assoc val-type-symb viz-id))

(defn default-visualizer [val-type-symb]
  (visualizer (get @*types->defaults val-type-symb)))

(register-visualizer
 {:id :preview
  :pred (fn [val] (contains? val :preview-str))
  :on-create (fn [{:keys [preview-str]}]
               {:fx/node (ui/text-area
                          :text preview-str
                          :editable? false
                          :class "value-pprint")})})

(register-visualizer
 {:id :map
  :pred (fn [val] (= :map (:flow-storm.runtime.values/kind val)))
  :on-create (fn [{:keys [map/keys-previews map/keys-refs map/vals-previews map/vals-refs flow-storm.debugger.ui.data-windows.data-windows/dw-id]}]
               (let [rows (mapv (fn [k-prev k-ref v-prev v-ref]
                                  [{:cell-type :key :k-prev k-prev :k-ref k-ref :stack-key (str k-prev "-KEY")}
                                   {:cell-type :val :v-prev v-prev :v-ref v-ref :stack-key k-prev}])
                           keys-previews
                           keys-refs
                           vals-previews
                           vals-refs)]
                 {:fx/node
                  (:table-view
                   (ui/table-view
                    :columns ["Key" "Val"]
                    :cell-factory (fn [_ {:keys [cell-type k-prev k-ref v-prev v-ref stack-key]}]
                                    (let [extras {:flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                                                  :flow-storm.debugger.ui.data-windows.data-windows/stack-key stack-key}]
                                      (case cell-type
                                        :key (ui/label :text k-prev :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id k-ref extras)))
                                        :val (ui/label :text v-prev :on-click (fn [_] (runtime-api/data-window-push-val-data rt-api dw-id v-ref extras))))))
                    :items rows))}))})

(register-visualizer {:id :vec
                      :pred (fn [val] (= :vec (:flow-storm.runtime.values/kind val)))
                      :on-create (fn [{:keys []}]
                                   {:fx/node (ui/label :text "A VEC")})})

(register-visualizer {:id :scope
                      :pred (fn [val] (= :number (:flow-storm.runtime.values/kind val)))
                      :on-create (fn [{:keys [val]}]
                                   {:fx/node (ui/label :text (str "Val" val))})
                      :on-update (fn [_ {:keys [fx/node]} {:keys [new-val]}]
                                   (ui-utils/set-text node (str "Val " new-val)))})

(set-default-visualizer "clojure.lang.PersistentArrayMap" :map)
