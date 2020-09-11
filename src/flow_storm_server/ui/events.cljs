(ns flow-storm-server.ui.events
  (:require [re-frame.core :refer [reg-event-db]]
            [flow-storm-server.ui.db :as db]))

(reg-event-db ::init (fn [_ _] (db/initial-db)))

(reg-event-db ::prev (fn [db _] (update db :trace-idx dec)))
(reg-event-db ::next (fn [db _] (update db :trace-idx inc)))

(reg-event-db ::add-trace (fn [db [_ trace]] (update db :trace (fn [t] (conj t (select-keys trace [:coor :result]))))))

(reg-event-db ::init-trace (fn [db [_ data]] (assoc db
                                                    :form-id (:traced-form-id data)
                                                    :form (:form data)
                                                    :trace []
                                                    :trace-idx 0)))
