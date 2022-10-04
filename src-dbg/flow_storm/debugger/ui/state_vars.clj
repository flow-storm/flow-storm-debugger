(ns flow-storm.debugger.ui.state-vars
  (:require [flow-storm.utils :refer [log]]
            [mount.core :as mount :refer [defstate]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [alert-dialog]]))

(def register-and-init-stage!

  "Globally available function, setup by `flow-storm.debugger.ui.main/start-theming-system`
   to register stages so they are themed and listen to theme changes"

  nil)

;; so the linter doesn't complain
(declare ui-objs)
(declare flows-ui-objs)

;; Because scene.lookup doesn't work if you lookup before a layout pass
;; So adding a node and looking it up quickly sometimes it doesn't work

;; ui objects references with a static application lifetime
;; objects stored here will not be collected ever
(defstate ui-objs
  :start (atom {})
  :stop nil)

;; ui objects with a flow lifetime
;; when a flow is discarded all this objects should be collected after
;; `clean-flow-objs` is called
(defstate flows-ui-objs
  :start (atom {})
  :stop nil)

(defn store-obj

  ([obj-id obj-ref]
   (swap! ui-objs update obj-id conj obj-ref)
   #_(log (format "Stored obj at key: %s" obj-id)))

  ([flow-id obj-id obj-ref]
   (swap! flows-ui-objs update flow-id (fn [flow-objs]
                                         (update flow-objs obj-id conj obj-ref)))
   #_(log (format "Stored %d flow obj at key %s" flow-id obj-id))))

(defn obj-lookup
  ([obj-id]
   (let [all-objs @ui-objs
         objs (get all-objs obj-id)]
     (when-not objs
       (log (format "Object not found %s" obj-id))
       #_(log (keys all-objs)))
     objs))

  ([flow-id obj-id]

   (let [all-objs @flows-ui-objs
         objs (get-in all-objs [flow-id obj-id])]
     #_(when-not objs
       (log (format "Flow object not found flow-id: %d obj-id: %s" flow-id obj-id))
       (log (keys (get all-objs flow-id))))
     objs)))

(defn clean-flow-objs [flow-id]
  (swap! flows-ui-objs dissoc flow-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interruptible tasks stuff ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare tasks-subscriptions)
(defstate tasks-subscriptions
  :start (atom {})
  :stop nil)

(defn subscribe-to-task-event [event-key task-id callback]
  (swap! tasks-subscriptions assoc [event-key task-id] callback))

(defn dispatch-task-event [event-key task-id data]
  (let [cb (get @tasks-subscriptions [event-key task-id])]
    (cb data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for creating ui components ids ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-token-id [thread-id form-id coord]
  (format "form_token_%d_%d_%s" thread-id form-id (hash coord)))

(defn flow-tab-id [flow-id]
  (format "flow_tab_%d" flow-id))

(defn thread-tool-tab-pane-id [thread-id]
  (format "thread_tool_tab_pane_id_%d" thread-id))

(defn thread-curr-trace-tf-id [thread-id]
  (format "thread_curr_trace_tf_%d" thread-id))

(defn thread-trace-count-lbl-id [thread-id]
  (format "thread_trace_count_lbl_%d" thread-id))

(defn thread-forms-box-id [thread-id]
  (format "forms_box_%d" thread-id))

(defn thread-form-box-id [thread-id form-id]
  (format "form_box_%d_%d" thread-id form-id))

(defn thread-forms-scroll-id [thread-id]
  (format "forms_scroll_%d" thread-id))

(defn thread-pprint-text-area-id [thread-id pane-id]
  (format "pprint_text_area_%d_%s" thread-id pane-id))

(defn thread-pprint-def-btn-id [thread-id pane-id]
  (format "pprint_def_btn_id_%d_%s" thread-id pane-id))

(defn thread-pprint-inspect-btn-id [thread-id pane-id]
  (format "pprint_inspect_btn_id_%d_%s" thread-id pane-id))

(defn thread-pprint-level-txt-id [thread-id pane-id]
  (format "pprint_level_txt_id_%d_%s" thread-id pane-id))

(defn thread-pprint-meta-chk-id [thread-id pane-id]
  (format "pprint_meta_chk_id_%d_%s" thread-id pane-id))

(defn thread-locals-list-view-data [thread-id]
  (format "locals_list_%d" thread-id))

(defn thread-fns-list-view-data [thread-id]
  (format "instrument_list_%d" thread-id))

(defn thread-fn-calls-list-view-data [thread-id]
  (format "instrument_fn_calls_list_%d" thread-id))

(defn thread-callstack-tree-view-id [thread-id]
  (format "callstack_tree_view_%s" thread-id))

(defn thread-callstack-tree-cell [thread-id idx]
  (format "callstack_tree_cell_%d_%d" thread-id idx))

(defn show-message [msg msg-type]
  (try
    (ui-utils/run-later
     (let [dialog (alert-dialog {:type msg-type
                                 :message msg
                                 :buttons [:close]})]
       (.show dialog)))
    (catch Exception _)))
