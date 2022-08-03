(ns flow-storm.debugger.trace-processor
  (:require [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.trace-indexer.protos :as indexer]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.main :as ui-main]
            #_[flow-storm.debugger.trace-indexer.mutable.impl :as trace-indexer]
            [flow-storm.debugger.trace-indexer.mutable-frame-tree.impl :as trace-indexer]))

(defn first-exec-trace-init [flow-id thread-id form-id]
  (dbg-state/set-idx flow-id thread-id 0)
  (ui-utils/run-now
   (flow-code/highlight-form flow-id thread-id form-id)))

(defn process-flow-init-trace [{:keys [flow-id form-ns form timestamp]}]
  (dbg-state/create-flow flow-id form-ns form timestamp)
  (ui-utils/run-now (flows-screen/remove-flow flow-id))
  (ui-utils/run-now (flows-screen/create-empty-flow flow-id))
  (ui-utils/run-now (ui-main/select-main-tools-tab :flows)))

(defn process-form-init-trace [{:keys [flow-id form-id thread-id form ns def-kind mm-dispatch-val]}]
  ;; if thread doesn't exist, create one
  (when-not (dbg-state/get-thread flow-id thread-id)
    (dbg-state/create-thread flow-id thread-id (trace-indexer/make-indexer))
    (flows-screen/create-empty-thread flow-id thread-id))

  ;; add the form
  (indexer/add-form (dbg-state/thread-trace-indexer flow-id thread-id)
                    form-id
                    ns
                    def-kind
                    mm-dispatch-val ;; this only applies to defmethods forms, will be nil in all other cases
                    form))

(defn process-expr-exec-trace [{:keys [flow-id thread-id form-id] :as trace}]
  (let [indexer (dbg-state/thread-trace-indexer flow-id thread-id)]

    (when (zero? (indexer/thread-timeline-count indexer))
      (first-exec-trace-init flow-id thread-id form-id))

    (indexer/add-exec-trace indexer trace)))

(defn process-fn-call-trace [{:keys [flow-id thread-id form-id] :as trace}]
  (let [indexer (dbg-state/thread-trace-indexer flow-id thread-id)]
    (dbg-state/update-fn-call-stats flow-id thread-id trace)

    (when (zero? (indexer/thread-timeline-count indexer))
      (first-exec-trace-init flow-id thread-id form-id))

    (indexer/add-fn-call-trace indexer trace)))

(defn process-bind-trace [{:keys [flow-id thread-id] :as trace}]
  (let [indexer (dbg-state/thread-trace-indexer flow-id thread-id)]
    (indexer/add-bind-trace indexer trace)))

(defn process [{:keys [trace/type] :as trace}]
  (case type
    :flow-init (process-flow-init-trace trace)
    :form-init (process-form-init-trace trace)
    :expr-exec (process-expr-exec-trace trace)
    :fn-call   (process-fn-call-trace trace)
    :bind      (process-bind-trace trace)))

(defn dispatch-trace [trace]
  (when (and (= (:flow-id trace) dbg-state/orphans-flow-id)
             (not (dbg-state/get-flow dbg-state/orphans-flow-id)))
    ;; whenever we receive a trace for dbg-state/orphans-flow-id and it is not there create-it
    (dbg-state/create-flow dbg-state/orphans-flow-id nil nil 0)
    (ui-utils/run-now (flows-screen/create-empty-flow dbg-state/orphans-flow-id)))

  (process trace))
