(ns flow-storm.debugger.events-processor

  "Processing events the debugger receives from the runtime"

  (:require [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.state-vars :as ui-vars]
            [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [flow-storm.utils :as utils]))

(declare start-events-processor)
(declare stop-events-processor)

(defstate events-processor
  :start (start-events-processor)
  :stop (stop-events-processor))

(defn- var-instrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-var-instrumented-list var-ns var-name)))

(defn- var-uninstrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-var-instrumented-list var-ns var-name)))

(defn- namespace-instrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])))

(defn- namespace-uninstrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])))

(defn- tap-event [{:keys [value]}]
  (ui-utils/run-later
   (taps-screen/add-tap-value value)))

(defn- flow-created-event [{:keys [flow-id form-ns form timestamp]}]
  (dbg-state/create-flow flow-id form-ns form timestamp)
  (ui-utils/run-now (flows-screen/remove-flow flow-id))
  (ui-utils/run-now (flows-screen/create-empty-flow flow-id))
  (ui-utils/run-now (ui-main/select-main-tools-tab :flows)))

(defn- thread-created-event [{:keys [flow-id thread-id]}]
  (dbg-state/create-thread flow-id thread-id)
  (ui-utils/run-now
   (flows-screen/create-empty-thread flow-id thread-id)))

(defn- first-fn-call-event [{:keys [flow-id thread-id form-id]}]
  (dbg-state/set-idx flow-id thread-id 0)
  (ui-utils/run-now
   (flow-code/highlight-form flow-id thread-id form-id)))

(defn- task-result-event [{:keys [task-id result]}]
  (ui-vars/dispatch-task-event :result task-id result))

(defn- task-progress-event [{:keys [task-id progress]}]
  (ui-vars/dispatch-task-event :progress task-id progress))

(defn- process-event [[ev-type ev-args-map]]
  (case ev-type
    :var-instrumented (var-instrumented-event ev-args-map)
    :var-uninstrumented (var-uninstrumented-event ev-args-map)
    :namespace-instrumented (namespace-instrumented-event ev-args-map)
    :namespace-uninstrumented (namespace-uninstrumented-event ev-args-map)
    :flow-created (flow-created-event ev-args-map)
    :thread-created (thread-created-event ev-args-map)
    :first-fn-call (first-fn-call-event ev-args-map)
    :tap (tap-event ev-args-map)
    :task-result (task-result-event ev-args-map)
    :task-progress (task-progress-event ev-args-map)
    ))

(defn enqueue-event! [e]
  (async/>!! (:events-chan events-processor) e))

(defn start-events-processor []
  (let [events-chan (async/chan 100)
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

(defn stop-events-processor []
  (async/close! (:events-chan events-processor)))
