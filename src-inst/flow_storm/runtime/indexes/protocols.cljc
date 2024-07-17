(ns flow-storm.runtime.indexes.protocols)

;;;;;;;;;;;;;;;;;;;;;;;;
;; Timeline protocols ;;
;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ThreadTimelineRecorderP
  (add-fn-call [_ fn-ns fn-name form-id args])
  (add-fn-return [_ coord ret-val])
  (add-fn-unwind [_ coord throwable])
  (add-expr-exec [_ coord expr-val])  
  (add-bind [_ coord symb-name symb-val]))

(defprotocol TreeBuilderP
  (reset-build-stack [_]))

(defprotocol TimelineP
  (thread-id [_ idx]))

(defprotocol TimelineEntryP
  (entry-type [_])
  (entry-idx [_])
  (fn-call-idx [_]))

(defprotocol CoordableTimelineEntryP
  (get-coord-vec [_])
  (get-coord-raw [_]))

(defprotocol ExpressionTimelineEntryP
  (get-expr-val [_]))

(defprotocol UnwindTimelineEntryP
  (get-throwable [_]))

(defprotocol TreeP
  (tree-root-index [_])
  (tree-childs-indexes [_ fn-call-idx]))

(defprotocol ImmutableP
  (as-immutable [_]))

(defprotocol ModifiableP
  (last-modified [_]))

(defprotocol TotalOrderTimelineP
  (tot-add-entry [_ th-timeline entry])
  (tot-clear-all [_]))

(defprotocol TotalOrderTimelineEntryP
  (tote-thread-timeline [_])
  (tote-thread-timeline-entry [_]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Function stats protocols ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol FnCallStatsP
  (all-stats [_]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Forms registry protocols ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol FormRegistryP
  (register-form [_ form-id form])
  (all-forms [_])
  (get-form [_ form-id])
  (start-form-registry [_])
  (stop-form-registry [_]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Thread registry protocols ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ThreadRegistryP
  (all-threads [_])
  (flow-threads-info [_ flow-id])
  (set-thread-blocked [_ flow-id thread-id breakpoint])
  (get-thread-indexes [_ flow-id thread-id])
  (flow-exists? [_ flow-id])
  (register-thread-indexes [_ flow-id thread-id thread-name form-id indexes])
  (record-total-order-entry [_ flow-id th-timeline entry])
  (total-order-timeline [_ flow-id])
  (discard-threads [_ flow-threads-ids])
  (start-thread-registry [_ callbacks])
  (stop-thread-registry [_]))

;;;;;;;;;;;;;;;;;;;;;;;
;; Entries protocols ;;
;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol FnCallTraceP
  (get-fn-name [_])
  (get-fn-ns [_])
  (get-form-id [_])
  (get-fn-args [_])
  (get-parent-idx [_])
  (set-parent-idx [_ idx])
  (get-ret-idx [_])
  (set-ret-idx [_ idx])    
  (add-binding [_ bind])
  (set-idx [_ idx])
  (bindings [_]))

(defprotocol BindTraceP
  (get-bind-sym-name [_])
  (get-bind-val [_]))
