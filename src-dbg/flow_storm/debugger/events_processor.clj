(ns flow-storm.debugger.events-processor

  "Processing events the debugger receives from the runtime"

  (:require [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.taps.screen :as taps-screen]
            [flow-storm.debugger.ui.utils :as ui-utils]))


(defn var-instrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-var-instrumented-list var-ns var-name)))

(defn var-uninstrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-var-instrumented-list var-ns var-name)))

(defn namespace-instrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])))

(defn namespace-uninstrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-namespace-instrumented-list [(browser-screen/make-inst-ns ns-name)])))

(defn tap-event [{:keys [value]}]
  (ui-utils/run-later
   (taps-screen/add-tap-value value)))

(defn process-event [[ev-type ev-args-map]]
  (case ev-type
    :var-instrumented (var-instrumented-event ev-args-map)
    :var-uninstrumented (var-uninstrumented-event ev-args-map)
    :namespace-instrumented (namespace-instrumented-event ev-args-map)
    :namespace-uninstrumented (namespace-uninstrumented-event ev-args-map)
    :tap (tap-event ev-args-map)))
