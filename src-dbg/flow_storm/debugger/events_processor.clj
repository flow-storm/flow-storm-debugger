(ns flow-storm.debugger.events-processor

  "Processing events the debugger receives from the runtime"

  (:require [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.flows.general :as ui-general]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.code :as ui-code]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.config :refer [debug-mode]]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.utils :refer [log]]
            [flow-storm.debugger.ui.state-vars :as ui-vars]))

(defn- var-instrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-var-instrumented-list var-ns var-name)
   (ui-main/select-main-tools-tab :browser)))

(defn- var-uninstrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-var-instrumented-list var-ns var-name)
   (ui-main/select-main-tools-tab :browser)))

(defn- namespace-instrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])
   (ui-main/select-main-tools-tab :browser)))

(defn- namespace-uninstrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])
   (ui-main/select-main-tools-tab :browser)))

(defn- tap-event [{:keys [value]}]
  (ui-utils/run-later
   (taps-screen/add-tap-value value)
   (ui-main/select-main-tools-tab :taps)))

(defn- flow-created-event [flow-info]
  (ui-utils/run-now
   (ui-main/create-flow flow-info)))

(defn- threads-updated-event [{:keys [flow-id]}]
  (ui-utils/run-now
   (flows-screen/update-threads-list flow-id)))

(defn- task-submitted-event [_]
  (ui-main/set-task-cancel-btn-enable true))

(defn- task-result-event [{:keys [task-id result]}]
  (ui-vars/dispatch-task-event :result task-id result)
  (ui-main/set-task-cancel-btn-enable false))

(defn- task-progress-event [{:keys [task-id progress]}]
  (ui-vars/dispatch-task-event :progress task-id progress))

(defn- heap-info-update-event [ev-args-map]
  (ui-main/update-heap-indicator ev-args-map))

(defn- goto-location-event [{:keys [flow-id thread-id thread-name idx]}]
  (ui-utils/run-now
   (ui-main/select-main-tools-tab :flows)
   (flows-screen/create-thread {:flow-id flow-id
                                :thread-id thread-id
                                :thread-name thread-name})
   (ui-general/select-thread-tool-tab flow-id thread-id :code)
   (ui-code/jump-to-coord flow-id thread-id idx)))

(defn- show-doc-event [{:keys [var-symbol]}]
  (ui-utils/run-now
   (ui-main/select-main-tools-tab :docs)
   (docs-screen/show-doc var-symbol)))

(defn- break-installed-event [{:keys [fq-fn-symb]}]
  (ui-main/set-break fq-fn-symb))

(defn- break-cleared-event [_]
  (ui-main/clear-break))

(defn- recording-updated-event [{:keys [recording?]}]
  (ui-main/set-recording-btn recording?))

(defn process-event [[ev-type ev-args-map]]
  (when debug-mode (log (format "Processing event: %s" [ev-type ev-args-map])))
  (case ev-type
    :var-instrumented (var-instrumented-event ev-args-map)
    :var-uninstrumented (var-uninstrumented-event ev-args-map)
    :namespace-instrumented (namespace-instrumented-event ev-args-map)
    :namespace-uninstrumented (namespace-uninstrumented-event ev-args-map)
    :flow-created (flow-created-event ev-args-map)
    :threads-updated (threads-updated-event ev-args-map)
    :tap (tap-event ev-args-map)
    :task-submitted (task-submitted-event ev-args-map)
    :task-result (task-result-event ev-args-map)
    :task-progress (task-progress-event ev-args-map)
    :heap-info-update (heap-info-update-event ev-args-map)
    :goto-location (goto-location-event ev-args-map)
    :show-doc (show-doc-event ev-args-map)
    :break-installed (break-installed-event ev-args-map)
    :break-cleared (break-cleared-event ev-args-map)
    :recording-updated (recording-updated-event ev-args-map)))
