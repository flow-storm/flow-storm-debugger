(ns flow-storm.runtime.indexes.protocols)

(defprotocol BuildIndexP
  (add-form-init [_ trace])
  (add-fn-call [_ trace])
  (add-fn-return [_ trace])
  (add-expr-exec [_ trace])  
  (add-bind [_ trace])
  (reset-build-stack [_]))

(defprotocol TimelineP
  (timeline-count [_])
  (timeline-entry [_ idx drift])
  (timeline-frames [_ from-idx to-idx pred])
  (timeline-raw-entries [_ from-idx to-idx])
  (timeline-find-entry [_ from-idx backwards? pred]))

(defprotocol TimelineEntryP
  (entry-type [_])
  (entry-idx [_])
  (fn-call-idx [_]))

(defprotocol TreeP
  (tree-root-index [_])
  (tree-childs-indexes [_ fn-call-idx])
  (tree-frame-data [_ fn-call-idx opts]))

(defprotocol ImmutableP
  (as-immutable [_]))

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
  (set-thread-blocked [_ flow-id thread-id breakpoint])
  (get-thread-indexes [_ flow-id thread-id])
  (flow-exists? [_ flow-id])
  (register-thread-indexes [_ flow-id thread-id thread-name form-id indexes])
  (discard-threads [_ flow-threads-ids])
  (start-thread-registry [_ callbacks])
  (stop-thread-registry [_]))
