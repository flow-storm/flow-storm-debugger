(ns flow-storm.debugger.trace-indexer.protos)

(defprotocol TraceIndexP

  (thread-timeline-count [_])
  (timeline-entry [_ idx])
  (frame-data-for-idx [_ idx])

  (add-fn-call-trace [_ fn-call-trace])
  (add-exec-trace [_ exec-trace])
  (add-bind-trace [_ bind-trace])

  (callstack-tree-root [_])
  (callstack-node-frame [_ node])
  (callstack-tree-childs [_ node])

  ;; TODO: Remove this two.
  ;; This shouldn't belong to this protocol, can probably
  ;; be implemented in terms of the others.
  (find-fn-frames [_ fn-ns fn-name form-id])
  (search-next-frame-idx [_ s from-idx print-level on-result-cb on-progress-cb]))

(defprotocol FormStoreP
  (add-form [_ form-id form-ns def-kind mm-dispatch-val form])
  (get-form [_ form-id]))
