(ns flow-storm.debugger.events-queue

  "Namespace for the sub-component that manages an events queue.

  This events are pushed by the runtime part (where the recordings live) via
  direct calling in the local mode or via websockets in remote mode for letting
  us (the debugger) know about interesting events on the runtime part of the world.

  Events will be dispatched after a dispatch-fn is set with `set-dispatch-fn`"

  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.utils :as utils])
  (:import [java.util.concurrent ArrayBlockingQueue TimeUnit]))

(declare start-events-queue)
(declare stop-events-queue)
(declare events-queue)

(defstate events-queue
  :start (fn [_] (start-events-queue))
  :stop (fn [] (stop-events-queue)))

(def queue-poll-interval 500)
(def wait-for-system-started-interval 1000)

(defn enqueue-event! [e]
  (when-let [queue (:queue events-queue)]
    (.put ^ArrayBlockingQueue queue e)))

(defn set-dispatch-fn [dispatch-fn]
  (reset! (:dispatch-fn events-queue) dispatch-fn))

(defn start-events-queue []
  (let [events-queue (ArrayBlockingQueue. 1000)
        dispatch-fn (atom nil)
        dispatch-thread (Thread.
                         (fn []
                           (try
                             ;; don't do anything until we have a dispatch-fn
                             (while (and (not (.isInterrupted (Thread/currentThread)))
                                         (not @dispatch-fn))
                               (utils/log "Waiting for dispatch-fn before dispatching events")
                               (Thread/sleep wait-for-system-started-interval))

                             ;; start the dispatch loop
                             (let [dispatch @dispatch-fn]
                               (loop [ev nil]
                                 (when-not (.isInterrupted (Thread/currentThread))
                                   (when ev
                                     (dispatch ev))
                                   (recur (.poll events-queue queue-poll-interval TimeUnit/MILLISECONDS)))))
                             (catch java.lang.InterruptedException _
                               (utils/log "Events thread interrupted"))
                             (catch Exception e
                               (utils/log-error "Events queue thread error" e))))
                         "FlowStorm Events Processor")
        interrupt-fn (fn [] (.interrupt dispatch-thread))]

    (.start dispatch-thread)
    {:interrupt-fn interrupt-fn
     :queue events-queue
     :dispatch-fn dispatch-fn}))

(defn stop-events-queue []
  (when-let [stop-fn (:interrupt-fn events-queue)]
    (stop-fn)))
