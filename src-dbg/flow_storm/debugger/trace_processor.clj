(ns flow-storm.debugger.trace-processor
  (:require [flow-storm.debugger.state :as state :refer [dbg-state]]
            [flow-storm.debugger.trace-indexer.protos :as indexer]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.trace-indexer.mutable.impl :as mut-trace-indexer]
            flow-storm.tracer)
  (:import [flow_storm.trace_types FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace]))

(defprotocol ProcessTrace
  (process [_]))

(defn increment-trace-counter []
  (state/increment-trace-counter dbg-state))

(defn first-exec-trace-init [flow-id thread-id form-id]
  (state/set-trace-idx dbg-state flow-id thread-id 0)
  (ui-utils/run-now
   (flow-code/highlight-form flow-id thread-id form-id)))

(extend-protocol ProcessTrace
  FlowInitTrace
  (process [{:keys [flow-id form-ns form timestamp]}]
    (state/create-flow dbg-state flow-id form-ns form timestamp)
    (ui-utils/run-now (flows-screen/remove-flow flow-id))
    (ui-utils/run-now (flows-screen/create-empty-flow flow-id)))

  FormInitTrace
  (process [{:keys [flow-id form-id thread-id form ns def-kind mm-dispatch-val]}]

    ;; if thread doesn't exist, create one
    (when-not (state/get-thread dbg-state flow-id thread-id)
      (state/create-thread dbg-state flow-id thread-id
                           (mut-trace-indexer/make-indexer))
      (flows-screen/create-empty-thread flow-id thread-id))

    ;; add the form
    (indexer/add-form (state/thread-trace-indexer dbg-state flow-id thread-id)
                      form-id
                      ns
                      def-kind
                      mm-dispatch-val ;; this only applies to defmethods forms, will be nil in all other cases
                      form))

  ExecTrace
  (process [{:keys [flow-id thread-id form-id] :as trace}]
    (let [indexer (state/thread-trace-indexer dbg-state flow-id thread-id)]

      (when (zero? (indexer/thread-exec-count indexer))
        (first-exec-trace-init flow-id thread-id form-id))

      (indexer/add-exec-trace indexer trace)))

  FnCallTrace
  (process [{:keys [flow-id thread-id form-id] :as trace}]
    (let [indexer (state/thread-trace-indexer dbg-state flow-id thread-id)]
      (state/update-fn-call-stats dbg-state flow-id thread-id trace)

      (when (zero? (indexer/thread-exec-count indexer))
        (first-exec-trace-init flow-id thread-id form-id))

      (indexer/add-fn-call-trace indexer trace)))

  BindTrace
  (process [{:keys [flow-id thread-id] :as trace}]
    (let [indexer (state/thread-trace-indexer dbg-state flow-id thread-id)]
      (indexer/add-bind-trace indexer trace))))

(defn dispatch-trace [trace]
  (process trace)
  (increment-trace-counter))
