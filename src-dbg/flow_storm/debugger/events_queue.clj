(ns flow-storm.debugger.events-queue
  (:require [clojure.core.async :as async]
            [flow-storm.state-management :refer [defstate]]
            [flow-storm.utils :as utils]))

(declare start-events-queue)
(declare stop-events-queue)
(declare events-queue)

(defstate events-queue
  :start (fn [_] (start-events-queue))
  :stop (fn [] stop-events-queue))

(defn enqueue-event! [e]
  (when-let [events-chan (:events-chan events-queue)]
    (async/>!! events-chan e)))

(defn start-events-queue []
  (let [events-chan (async/chan 100)
        process-event (requiring-resolve 'flow-storm.debugger.events-processor/process-event)
        ev-thread (Thread.
                   (fn []
                     (try
                       (loop [ev (async/<!! events-chan)]
                         (when ev
                           (process-event ev)
                           (recur (async/<!! events-chan))))
                       (catch java.lang.InterruptedException _
                         (utils/log "Events thread interrupted")))))]

    (.start ev-thread)
    {:events-chan events-chan
     :events-thread ev-thread}))

(defn stop-events-queue []
  (async/close! (:events-chan events-queue)))
