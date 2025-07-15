(ns flow-storm.debugger.events-processor

  "Processing events the debugger receives from the runtime"

  (:require [flow-storm.debugger.ui.browser.screen :as browser-screen]
            [flow-storm.debugger.ui.outputs.screen :as outputs-screen]
            [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.bookmarks :as bookmarks]
            [flow-storm.debugger.ui.docs.screen :as docs-screen]
            [flow-storm.debugger.ui.flows.general :as ui-general :refer [show-message]]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.data-windows.data-windows :as data-windows]
            [flow-storm.utils :refer [log]]
            [flow-storm.jobs :refer [timeline-updates-check-interval]]
            [flow-storm.debugger.state :as dbg-state]))

(defn- vanilla-var-instrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-instrumentation-list (browser-screen/make-inst-var var-ns var-name))
   (ui-general/select-main-tools-tab "tool-browser")))

(defn- vanilla-var-uninstrumented-event [{:keys [var-ns var-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-instrumentation-list (browser-screen/make-inst-var var-ns var-name))
   (ui-general/select-main-tools-tab "tool-browser")))

(defn- vanilla-namespace-instrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/add-to-instrumentation-list (browser-screen/make-inst-ns ns-name))
   (ui-general/select-main-tools-tab "tool-browser")))

(defn- vanilla-namespace-uninstrumented-event [{:keys [ns-name]}]
  (ui-utils/run-later
   (browser-screen/remove-from-instrumentation-list (browser-screen/make-inst-ns ns-name))
   (ui-general/select-main-tools-tab "tool-browser")))

(defn- storm-instrumentation-updated-event [data]
  (ui-utils/run-later
    (browser-screen/update-storm-instrumentation data)
    (ui-general/select-main-tools-tab "tool-browser")))

(defn- tap-event [{:keys [value]}]
  (ui-utils/run-later
    (outputs-screen/add-tap-value value)))

(defn out-write-event [{:keys [msg]}]
  (ui-utils/run-later
    (outputs-screen/add-out-write msg)))

(defn err-write-event [{:keys [msg]}]
  (ui-utils/run-later
    (outputs-screen/add-err-write msg)))

(defn last-evals-update-event [{:keys [last-evals-refs]}]
  (ui-utils/run-later
    (outputs-screen/update-last-evals last-evals-refs)))

(defn- flow-created-event [flow-info]
  (ui-utils/run-now
    (ui-main/create-flow flow-info)))

(defn- flow-discarded-event [flow-info]
  (ui-utils/run-now
    (flows-screen/clear-debugger-flow (:flow-id flow-info))))

(defn- threads-updated-event [{:keys [flow-id flow-threads-info]}]
  (ui-utils/run-now
    (flows-screen/update-threads-list flow-id flow-threads-info)))

(defn- timeline-updated-event [{:keys [flow-id thread-id]}]
  ;; If there is a tab open for the thread already, update it
  (when (dbg-state/get-thread flow-id thread-id)
    (ui-utils/run-later
      (if (:auto-update-ui? (dbg-state/debugger-config))

        (let [start (System/currentTimeMillis)]
          (flows-screen/update-outdated-thread-ui flow-id thread-id)
          (when (> (- (System/currentTimeMillis) start)
                   timeline-updates-check-interval)
            (log "WARNING: UI updates are slow, disabling automatic updates.")
            (dbg-state/set-auto-update-ui false)))

        (flows-screen/make-outdated-thread flow-id thread-id)))))

(defn- task-submitted-event [_]
  (ui-main/set-task-cancel-btn-enable true))

(defn- task-finished-event [_]
  (ui-main/set-task-cancel-btn-enable false))

(defn- task-failed-event [{:keys [message]}]
  (ui-main/set-task-cancel-btn-enable false)
  (ui-general/show-message message :error))

(defn- heap-info-update-event [ev-args-map]
  (ui-main/update-heap-indicator ev-args-map))

(defn- goto-location-event [loc]
  (ui-utils/run-now
   (flows-screen/goto-location loc)))

(defn- show-doc-event [{:keys [var-symbol]}]
  (ui-utils/run-now
   (ui-general/select-main-tools-tab "tool-docs")
   (docs-screen/show-doc var-symbol)))

(defn- break-installed-event [{:keys [fq-fn-symb]}]
  (ui-utils/run-later
   (browser-screen/add-to-instrumentation-list (browser-screen/make-inst-break fq-fn-symb))))

(defn- break-removed-event [{:keys [fq-fn-symb]}]
  (ui-utils/run-later
   (browser-screen/remove-from-instrumentation-list (browser-screen/make-inst-break fq-fn-symb))))

(defn- recording-updated-event [{:keys [recording?]}]
  (flows-screen/set-recording-btn recording?))

(defn- multi-timeline-recording-updated-event [{:keys [recording?]}]
  (flows-screen/set-multi-timeline-recording-btn recording?))

(defn- function-unwinded-event [{:keys [flow-id] :as unwind-data}]
  (case (dbg-state/maybe-add-exception unwind-data)
    :ex-limit-reached (show-message "Too many exceptions throwed, showing only the first ones" :warning)
    (:ex-skipped :ex-limit-passed)  nil
    :ex-added (ui-utils/run-later
               (flows-screen/add-exception-to-menu unwind-data)
               ;; the first time we encounter an exception, navigate to that location
               (when (and (:auto-jump-on-exception? (dbg-state/debugger-config))
                          (= 1 (count (dbg-state/flow-exceptions flow-id))))
                 (flows-screen/goto-location unwind-data)))))

(defn expression-bookmark-event [{:keys [flow-id thread-id idx note source] :as bookmark-location}]
  (ui-utils/run-later
   (dbg-state/add-bookmark {:flow-id flow-id
                            :thread-id thread-id
                            :idx idx
                            :source (or source :bookmark.source/api)
                            :note note})
   (bookmarks/update-bookmarks)
   ;; jump to the first mark, unless, we've already jumped to an exception
   (when (and
           (= 1 (->> (dbg-state/flow-bookmarks flow-id)
                     (filter (fn [{:keys [source]}]
                               (= source :bookmark.source/api)))
                     count))
           (not (and
                  (:auto-jump-on-exception? (dbg-state/debugger-config))
                  (seq (dbg-state/flow-exceptions flow-id)))))
     (flows-screen/goto-location bookmark-location))))

(defn data-window-push-val-data-event [{:keys [dw-id val-data root? visualizer]}]
  (data-windows/push-val dw-id val-data {:root? root?, :visualizer visualizer}))

(defn data-window-update-event [{:keys [dw-id data]}]
  (data-windows/update-val dw-id data))

(defn process-event [[ev-type ev-args-map]]

  (case ev-type
    :vanilla-var-instrumented (vanilla-var-instrumented-event ev-args-map)
    :vanilla-var-uninstrumented (vanilla-var-uninstrumented-event ev-args-map)
    :vanilla-namespace-instrumented (vanilla-namespace-instrumented-event ev-args-map)
    :vanilla-namespace-uninstrumented (vanilla-namespace-uninstrumented-event ev-args-map)
    :storm-instrumentation-updated-event (storm-instrumentation-updated-event ev-args-map)
    :flow-created (flow-created-event ev-args-map)
    :flow-discarded (flow-discarded-event ev-args-map)
    :threads-updated (threads-updated-event ev-args-map)
    :timeline-updated (timeline-updated-event ev-args-map)

    :tap (tap-event ev-args-map)
    :out-write (out-write-event ev-args-map)
    :err-write (err-write-event ev-args-map)
    :last-evals-update (last-evals-update-event ev-args-map)

    :task-submitted (task-submitted-event ev-args-map)
    :task-finished (task-finished-event ev-args-map)
    :task-failed (task-failed-event ev-args-map)

    :heap-info-update (heap-info-update-event ev-args-map)
    :goto-location (goto-location-event ev-args-map)
    :show-doc (show-doc-event ev-args-map)
    :break-installed (break-installed-event ev-args-map)
    :break-removed (break-removed-event ev-args-map)
    :recording-updated (recording-updated-event ev-args-map)
    :multi-timeline-recording-updated (multi-timeline-recording-updated-event ev-args-map)
    :function-unwinded-event (function-unwinded-event ev-args-map)

    :expression-bookmark-event (expression-bookmark-event ev-args-map)

    :data-window-push-val-data (data-window-push-val-data-event ev-args-map)
    :data-window-update (data-window-update-event ev-args-map)
    nil ;; events-processor doesn't handle every event, specially tasks processing
    ))
