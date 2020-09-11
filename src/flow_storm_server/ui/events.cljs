(ns flow-storm-server.ui.events
  (:require [re-frame.core :refer [reg-event-db]]
            [flow-storm-server.ui.db :as db]
            [cljs.tools.reader :as tools-reader]))

(reg-event-db ::init (fn [_ _] (db/initial-db)))

(reg-event-db ::prev (fn [db _] (update db :trace-idx dec)))
(reg-event-db ::next (fn [db _] (update db :trace-idx inc)))

(reg-event-db ::add-trace (fn [db [_ trace]]
                            (update db :traces (fn [traces]
                                                 (let [trace (update trace :result #(try
                                                                                      (tools-reader/read-string %)
                                                                                      (catch js/Error e
                                                                                        %)))]
                                                   (conj traces (select-keys trace [:coor :result])))))))

(reg-event-db ::init-trace (fn [db [_ data]]
                             (assoc db
                                    :form-id (:traced-form-id data)
                                    :form (-> (:form data)
                                              (tools-reader/read-string))
                                    :traces []
                                    :trace-idx 0)))
