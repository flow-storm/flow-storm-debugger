(ns flow-storm.debugger.events-queue
  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.utils :as utils]
            [flow-storm.debugger.state :as dbg-state])
  (:import [java.util.concurrent ArrayBlockingQueue TimeUnit]))

(declare start-events-queue)
(declare stop-events-queue)
(declare events-queue)

(defstate events-queue
  :start (fn [_] (start-events-queue))
  :stop (fn [] stop-events-queue))

(def queue-poll-interval 500)
(def wait-for-system-started-interval 1000)

(defn enqueue-event! [e]
  (when-let [queue (:queue events-queue)]
    (.put queue e)))

(defn start-events-queue []
  (let [process-event (requiring-resolve 'flow-storm.debugger.events-processor/process-event)
        events-queue (ArrayBlockingQueue. 1000)
        ev-thread (Thread.
                   (fn []
                     (try
                       (loop [ev nil]
                         (when-not (.isInterrupted (Thread/currentThread))
                           (while (not (dbg-state/system-fully-started?))
                             (utils/log "Waiting for full system start before dispatching events")
                             (Thread/sleep wait-for-system-started-interval))
                           (when ev
                             (process-event ev))
                           (recur (.poll events-queue queue-poll-interval TimeUnit/MILLISECONDS))))
                       (catch java.lang.InterruptedException _
                         (utils/log "Events thread interrupted"))
                       (catch Exception e
                         (utils/log-error "Error" e))))
                   "FlowStorm Events Processor")
        interrupt-fn (fn [] (.interrupt ev-thread))]

    (.start ev-thread)
    {:interrupt-fn interrupt-fn
     :queue events-queue
     :thread ev-thread}))

(defn stop-events-queue []
  (when-let [stop-fn (:interrupt-fn events-queue)]
    (stop-fn)))
