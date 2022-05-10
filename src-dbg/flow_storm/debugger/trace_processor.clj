(ns flow-storm.debugger.trace-processor
  (:require [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.trace-indexer.protos :as indexer]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.debugger.trace-types :as dbg-trace-types]
            [flow-storm.debugger.ui.flows.screen :as flows-screen]
            [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.trace-indexer.mutable.impl :as mut-trace-indexer])
  (:import [flow_storm.trace_types FlowInitTrace FormInitTrace ExecTrace FnCallTrace BindTrace]))

(defprotocol ProcessTrace
  (process [_]))

(defn increment-trace-counter []
  (dbg-state/increment-trace-counter))

(defn first-exec-trace-init [flow-id thread-id form-id]
  (dbg-state/set-trace-idx flow-id thread-id 0)
  (ui-utils/run-now
   (flow-code/highlight-form flow-id thread-id form-id)))

(extend-protocol ProcessTrace
  FlowInitTrace
  (process [{:keys [flow-id form-ns form timestamp] :as trace}]
    (dbg-state/create-flow (-> trace meta :ws-conn) flow-id form-ns form timestamp)
    (ui-utils/run-now (flows-screen/remove-flow flow-id))
    (ui-utils/run-now (flows-screen/create-empty-flow flow-id)))

  FormInitTrace
  (process [{:keys [flow-id form-id thread-id form ns def-kind mm-dispatch-val]}]

    ;; if thread doesn't exist, create one
    (when-not (dbg-state/get-thread flow-id thread-id)
      (dbg-state/create-thread flow-id thread-id (mut-trace-indexer/make-indexer))
      (flows-screen/create-empty-thread flow-id thread-id))

    ;; add the form
    (indexer/add-form (dbg-state/thread-trace-indexer flow-id thread-id)
                      form-id
                      ns
                      def-kind
                      mm-dispatch-val ;; this only applies to defmethods forms, will be nil in all other cases
                      form))

  ExecTrace
  (process [{:keys [flow-id thread-id form-id] :as trace}]
    (let [indexer (dbg-state/thread-trace-indexer flow-id thread-id)]

      (when (zero? (indexer/thread-exec-count indexer))
        (first-exec-trace-init flow-id thread-id form-id))

      (indexer/add-exec-trace indexer trace)))

  FnCallTrace
  (process [{:keys [flow-id thread-id form-id] :as trace}]
    (let [indexer (dbg-state/thread-trace-indexer flow-id thread-id)]
      (dbg-state/update-fn-call-stats flow-id thread-id trace)

      (when (zero? (indexer/thread-exec-count indexer))
        (first-exec-trace-init flow-id thread-id form-id))

      (indexer/add-fn-call-trace indexer trace)))

  BindTrace
  (process [{:keys [flow-id thread-id] :as trace}]
    (let [indexer (dbg-state/thread-trace-indexer flow-id thread-id)]
      (indexer/add-bind-trace indexer trace))))

(defn dispatch-trace [trace]
  (when (and (= (:flow-id trace) dbg-state/orphans-flow-id)
             (not (dbg-state/get-flow dbg-state/orphans-flow-id)))
    ;; whenever we receive a trace for dbg-state/orphans-flow-id and it is not there create-it
    (dbg-state/create-flow (-> trace meta :ws-conn) dbg-state/orphans-flow-id nil nil 0)
    (ui-utils/run-now (flows-screen/create-empty-flow dbg-state/orphans-flow-id)))

  (process trace)
  (increment-trace-counter))

(defn local-dispatch-trace [trace]
  (-> trace
      dbg-trace-types/wrap-local-values
      dispatch-trace))

(defn remote-dispatch-trace [conn trace]
  (-> trace
      (dbg-trace-types/wrap-remote-values conn)
      (with-meta {:ws-conn conn})
      dispatch-trace))
