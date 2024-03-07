(ns flow-storm.debugger.events-queue

  "Namespace for the sub-component that manages an events queue.

  This events are pushed by the runtime part (where the recordings live) via
  direct calling in the local mode or via websockets in remote mode for letting
  us (the debugger) know about interesting events on the runtime part of the world.

  Events will be dispatched after a dispatch-fn is set with `set-dispatch-fn`"

  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.utils :as utils :refer [log]])
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

(defn add-dispatch-fn [fn-key dispatch-fn]
  (swap! (:dispatch-fns events-queue) assoc fn-key dispatch-fn))

(defn rm-dispatch-fn [fn-key]
  (swap! (:dispatch-fns events-queue) dissoc fn-key))

(defn start-events-queue []
  (let [events-queue (ArrayBlockingQueue. 1000)
        dispatch-fns (atom {})
        dispatch-thread (Thread.
                         (fn []
                           (try
                             ;; don't do anything until we have a dispatch-fn
                             (while (and (not (.isInterrupted (Thread/currentThread)))
                                         (empty? @dispatch-fns))
                               (utils/log "Waiting for a dispatch-fn before dispatching events")
                               (Thread/sleep wait-for-system-started-interval))

                             ;; start the dispatch loop
                             (loop [ev nil]
                               (when-not (.isInterrupted (Thread/currentThread))
                                 (try
                                   (when ev

                                     (when (and (:debug-mode? (dbg-state/debugger-config))
                                                (not (= (first ev) :heap-info-update)))
                                       (log (format "Processing event: %s" ev)))

                                    (doseq [dispatch (vals @dispatch-fns)]
                                      (dispatch ev)))
                                   (catch Exception e
                                     (utils/log-error (str "Error dispatching event" ev) e)))
                                 (recur (.poll events-queue queue-poll-interval TimeUnit/MILLISECONDS))))
                             (catch java.lang.InterruptedException _
                               (utils/log "Events thread interrupted"))
                             (catch Exception e
                               (utils/log-error "Events queue thread error" e))))
                         "FlowStorm Events Processor")
        interrupt-fn (fn [] (.interrupt dispatch-thread))]

    (.start dispatch-thread)
    {:interrupt-fn interrupt-fn
     :queue events-queue
     :dispatch-fns dispatch-fns}))

(defn stop-events-queue []
  (when-let [stop-fn (:interrupt-fn events-queue)]
    (stop-fn)))
