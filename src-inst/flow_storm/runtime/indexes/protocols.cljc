(ns flow-storm.runtime.indexes.protocols)

(defprotocol BuildIndexP
  (add-form-init [_ trace])
  (add-fn-call [_ trace])
  (add-expr-exec [_ trace])
  (add-bind [_ trace]))

(defprotocol FrameIndexP
  (timeline-count [_])
  (timeline-entry [_ idx])
  (timeline-frame-seq [_])
  (timeline-seq [_])
  (frame-data [_ idx])
  
  (callstack-tree-root-node [_]))

(defprotocol TreeNodeP
  (get-frame [_])
  (get-node-immutable-frame [_])
  (has-childs? [_])
  (add-child [_ node])
  (get-childs [_]))

(defprotocol CallStackFrameP
  (get-immutable-frame [_])
  (get-expr-exec [_ idx])
  (add-binding-to-frame [_ bind-trace])
  (add-expr-exec-to-frame [_ idx exec-trace]))

(defprotocol FnCallStatsP
  (all-stats [_]))

(defprotocol FormRegistryP
  (register-form [_ form-id form])
  (all-forms [_])
  (get-form [_ form-id])
  (start-form-registry [_])
  (stop-form-registry [_]))

(defprotocol ThreadRegistryP
  (all-threads [_])
  (flow-threads-info [_ flow-id])
  (get-thread-indexes [_ flow-id thread-id])
  (flow-exists? [_ flow-id])
  (register-thread-indexes [_ flow-id thread-id thread-name form-id indexes])
  (discard-threads [_ flow-threads-ids])
  (start-thread-registry [_ callbacks])
  (stop-thread-registry [_]))
