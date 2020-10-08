(ns flow-storm-debugger.ui.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]
            [flow-storm-debugger.ui.db :as db]
            [cljs.tools.reader :as tools-reader]
            [flow-storm-debugger.ui.utils :as utils]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]
            [flow-storm-debugger.ui.events.traces :as events.traces]
            [flow-storm-debugger.ui.events.flows :as events.flows]
            [flow-storm-debugger.ui.events.panels :as events.panels]
            [re-frame.std-interceptors :refer [trim-v]]))

(reg-event-db ::init (fn [_ _] (db/initial-db)))

(reg-event-db
 ::connected-clients-update
 [trim-v]
 (fn [db [data-map]]
   (assoc db :connected-clients (:count data-map))))

(reg-event-db ::add-trace [trim-v] events.traces/add-trace)
(reg-event-db ::add-bind-trace [trim-v] events.traces/add-bind-trace)
(reg-event-db ::init-trace [trim-v] events.traces/init-trace)

(reg-event-db ::selected-flow-prev [trim-v] events.flows/selected-flow-prev)
(reg-event-db ::selected-flow-next [trim-v] events.flows/selected-flow-next)
(reg-event-db ::select-flow [trim-v] events.flows/select-flow)
(reg-event-db ::remove-flow [trim-v] events.flows/remove-flow)
(reg-event-db ::set-current-flow-trace-idx [trim-v] events.flows/set-current-flow-trace-idx)
(reg-event-fx ::save-selected-flow [trim-v] events.flows/save-selected-flow)
(reg-event-db ::load-flow [trim-v] events.flows/load-flow)

(reg-event-db ::select-result-panel [trim-v] events.panels/select-result-panel)
(reg-event-db ::show-local [trim-v] events.panels/show-local)
(reg-event-db ::hide-modals [trim-v] events.panels/hide-modals)
(reg-event-db ::open-save-panel [trim-v] events.panels/open-save-panel)
