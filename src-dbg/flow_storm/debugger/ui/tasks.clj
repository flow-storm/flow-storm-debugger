(ns flow-storm.debugger.ui.tasks
  (:require [flow-storm.debugger.events-queue :as events-queue]
            [flow-storm.debugger.ui.utils :refer [run-later]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]))

(defn submit-task [rt-task-func rt-task-args {:keys [on-progress on-finished]}]
  (let [func-task-id (apply rt-task-func (into [rt-api] rt-task-args))]
    (events-queue/add-dispatch-fn
     func-task-id
     (fn [[ev-type {:keys [task-id] :as task-info}]]
       (when (= func-task-id task-id)
         (case ev-type
           :task-progress (run-later (when on-progress
                                       (on-progress task-info)))
           :task-finished (run-later
                           (when on-finished
                             (on-finished task-info))
                           (events-queue/rm-dispatch-fn func-task-id))
           nil))))
    (runtime-api/start-task rt-api func-task-id)))
