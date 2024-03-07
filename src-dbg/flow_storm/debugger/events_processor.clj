(ns flow-storm.debugger.events-processor

  "Processing events the debugger receives from the runtime"

  (:require [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.state :as dbg-state]))

(defn- var-instrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-instrumentation-list (browser-screen/make-inst-var var-ns var-name))
   (ui-general/select-main-tools-tab :browser)))

(defn- var-uninstrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-instrumentation-list (browser-screen/make-inst-var var-ns var-name))
   (ui-general/select-main-tools-tab :browser)))

(defn- namespace-instrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-instrumentation-list (browser-screen/make-inst-ns ns-name))
   (ui-general/select-main-tools-tab :browser)))

(defn- namespace-uninstrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-instrumentation-list (browser-screen/make-inst-ns ns-name))
   (ui-general/select-main-tools-tab :browser)))

(defn- tap-event [{:keys [value]}]
  (ui-utils/run-later
   (taps-screen/add-tap-value value)))

(defn- flow-created-event [flow-info]
  (ui-utils/run-now
   (ui-main/create-flow flow-info)))

(defn- threads-updated-event [{:keys [flow-id]}]
  (ui-utils/run-now
   (flows-screen/update-threads-list flow-id)))

(defn- task-submitted-event [_]
  (ui-main/set-task-cancel-btn-enable true))

(defn- task-finished-event [_]
  (ui-main/set-task-cancel-btn-enable false))

(defn- heap-info-update-event [ev-args-map]
  (ui-main/update-heap-indicator ev-args-map))

(defn- goto-location-event [loc]
  (ui-utils/run-now
   (flows-screen/goto-location loc)))

(defn- show-doc-event [{:keys [var-symbol]}]
  (ui-utils/run-now
   (ui-general/select-main-tools-tab :docs)
   (docs-screen/show-doc var-symbol)))

(defn- break-installed-event [{:keys [fq-fn-symb]}]
  (ui-utils/run-later
   (browser-screen/add-to-instrumentation-list (browser-screen/make-inst-break fq-fn-symb))))

(defn- break-removed-event [{:keys [fq-fn-symb]}]
  (ui-utils/run-later
   (browser-screen/remove-from-instrumentation-list (browser-screen/make-inst-break fq-fn-symb))))

(defn- recording-updated-event [{:keys [recording?]}]
  (ui-main/set-recording-btn recording?))

(defn- function-unwinded-event [unwind-data]
  (dbg-state/add-fn-unwind unwind-data)
  (ui-utils/run-later
    (flows-screen/update-exceptions-combo)))

(defn process-event [[ev-type ev-args-map]]

  (case ev-type
    :var-instrumented (var-instrumented-event ev-args-map)
    :var-uninstrumented (var-uninstrumented-event ev-args-map)
    :namespace-instrumented (namespace-instrumented-event ev-args-map)
    :namespace-uninstrumented (namespace-uninstrumented-event ev-args-map)
    :flow-created (flow-created-event ev-args-map)
    :threads-updated (threads-updated-event ev-args-map)
    :tap (tap-event ev-args-map)

    :task-submitted (task-submitted-event ev-args-map)
    :task-finished (task-finished-event ev-args-map)

    :heap-info-update (heap-info-update-event ev-args-map)
    :goto-location (goto-location-event ev-args-map)
    :show-doc (show-doc-event ev-args-map)
    :break-installed (break-installed-event ev-args-map)
    :break-removed (break-removed-event ev-args-map)
    :recording-updated (recording-updated-event ev-args-map)
    :function-unwinded-event (function-unwinded-event ev-args-map)
    nil ;; events-processor doesn't handle every event, specially tasks processing
    ))
