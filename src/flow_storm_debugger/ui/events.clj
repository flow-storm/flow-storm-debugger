(ns flow-storm-debugger.ui.events
  (:require [flow-storm-debugger.ui.events.flows :as events.flows]
            [flow-storm-debugger.ui.events.traces :as events.traces]
            [cljfx.api :as fx])
  (:import [javafx.stage FileChooser]
           [javafx.event ActionEvent]
           [javafx.scene Node]))

(defmulti dispatch-event :event/type)

(defmethod dispatch-event ::init [{:keys [fx/context]}]
  {:context context})

(defmethod dispatch-event ::selected-flow-prev [{:keys [fx/context]}]
  {:context (fx/swap-context context events.flows/selected-flow-prev)})

(defmethod dispatch-event ::selected-flow-next [{:keys [fx/context]}]
  {:context (fx/swap-context context events.flows/selected-flow-next)})

(defmethod dispatch-event ::select-flow [{:keys [fx/context flow-id]}]
  {:context (fx/swap-context context events.flows/select-flow flow-id)})

(defmethod dispatch-event ::remove-flow [{:keys [fx/context flow-id]}]
  {:context (fx/swap-context context events.flows/remove-flow flow-id)})

(defmethod dispatch-event ::remove-selected-flow [{:keys [fx/context]}]
  {:context (fx/swap-context context events.flows/remove-selected-flow)})

(defmethod dispatch-event ::remove-all-flows [{:keys [fx/context]}]
  {:context (fx/swap-context context events.flows/remove-all-flows)})

(defmethod dispatch-event ::set-current-flow-trace-idx [{:keys [fx/context trace-idx]}]
  {:context (fx/swap-context context events.flows/set-current-flow-trace-idx trace-idx)})

(defmethod dispatch-event ::load-flow [{:keys [fx/context ^ActionEvent fx/event]}]
  (let [window (.getWindow (.getScene ^Node (.getTarget event)))
        chooser (doto (FileChooser.)
                  (.setTitle "Open File"))]
    (when-let [file (.showOpenDialog chooser window)]
      {:context (fx/swap-context context events.flows/load-flow (read-string (slurp file)))})))

(defmethod dispatch-event ::set-result-panel [{:keys [fx/context content]}]
  {:context (fx/swap-context context events.flows/set-result-panel content)})

(defmethod dispatch-event ::set-result-panel-type [{:keys [fx/context panel-type]}]
  {:context (fx/swap-context context events.flows/set-result-panel-type panel-type)})

(defmethod dispatch-event ::open-dialog [{:keys [fx/context dialog]}]
  {:context (fx/swap-context context events.flows/open-dialog dialog)})

(defmethod dispatch-event ::save-selected-flow [{:keys [fx/context file-name]}]
  (let [selected-flow-id (fx/sub-val context :selected-flow-id)
        flow (-> (fx/sub-val context :flows)
                 (get selected-flow-id)
                 (select-keys [:forms :traces :trace-idx :bind-traces :errors])
                 (assoc :flow-id selected-flow-id)
                 pr-str)]
    (cond-> {:context (fx/swap-context context assoc :open-dialog nil)}
      file-name (assoc :save-file {:file-name file-name
                                   :file-content flow}))))
