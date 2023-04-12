(ns flow-storm.debugger.ui.state-vars
  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [alert-dialog]]
            [clojure.string :as str]))

(def register-and-init-stage!

  "Globally available function, setup by `flow-storm.debugger.ui.main/start-theming-system`
   to register stages so they are themed and listen to theme changes"

  nil)

(def clojure-storm-env? nil)

(defn configure-environment [{:keys [clojure-storm-env?]}]
  (alter-var-root #'clojure-storm-env? (constantly clojure-storm-env?)))

;; so the linter doesn't complain
(declare ui-objs)

;; Because scene.lookup doesn't work if you lookup before a layout pass
;; So adding a node and looking it up quickly sometimes it doesn't work

;; ui objects references with a static application lifetime
;; objects stored here will not be collected ever
(defstate ui-objs
  :start (fn [_] (atom {}))
  :stop (fn []))

(defn store-obj

  ([obj-id obj-ref]
   (store-obj :no-flow :no-flow obj-id obj-ref))

  ([flow-id obj-id obj-ref]
   (store-obj flow-id nil obj-id obj-ref))

  ([flow-id thread-id obj-id obj-ref]
   (swap! ui-objs update [flow-id thread-id obj-id] conj obj-ref)))

(defn obj-lookup
  ([obj-id]
   (obj-lookup :no-flow :no-flow obj-id))

  ([flow-id obj-id]
   (obj-lookup flow-id nil obj-id))

  ([flow-id thread-id obj-id]
   (let [o (get @ui-objs [flow-id thread-id obj-id])]
     o)))

(defn clean-objs
  ([] (clean-objs nil nil))
  ([flow-id]
   (swap! ui-objs (fn [objs]
                    (reduce-kv (fn [ret [fid tid oid] o]
                                 (if (= fid flow-id)
                                   ret
                                   (assoc ret [fid tid oid] o)))
                               {}
                               objs))))
  ([flow-id thread-id]
   (swap! ui-objs (fn [objs]
                    (reduce-kv (fn [ret [fid tid oid] o]
                                 (if (and (= fid flow-id)
                                          (= tid thread-id))
                                   ret
                                   (assoc ret [fid tid oid] o)))
                               {}
                               objs)))))

;; HACKY : This is the only current way of
;; returning all ...
(defn form-tokens [flow-id thread-id form-id]
  (reduce-kv (fn [r [fid tid oid] objects]
               (if (and (= flow-id fid)
                        (= thread-id tid)
                        (str/starts-with? oid (str "form_token_" form-id)))
                 (into r objects)
                 r))
   []
   @ui-objs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interruptible tasks stuff ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare tasks-subscriptions)
(defstate tasks-subscriptions
  :start (fn [_] (atom {}))
  :stop (fn []))

(defn subscribe-to-task-event [event-key task-id callback]
  (swap! tasks-subscriptions assoc [event-key task-id] callback))

(defn dispatch-task-event [event-key task-id data]
  (when-let [cb (get @tasks-subscriptions [event-key task-id])]
    (cb data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for creating ui components ids ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-token-id [form-id coord]
  (format "form_token_%d_%s" form-id (hash coord)))

(defn flow-tab-id [flow-id]
  (format "flow_tab_%d" flow-id))

(defn thread-form-box-id [form-id]
  (format "form_box_%d" form-id))

(defn thread-pprint-text-area-id [pane-id]
  (format "pprint_text_area_%s" pane-id))

(defn thread-pprint-def-btn-id [pane-id]
  (format "pprint_def_btn_id_%s" pane-id))

(defn thread-pprint-inspect-btn-id [pane-id]
  (format "pprint_inspect_btn_id_%s" pane-id))

(defn thread-pprint-tap-btn-id [pane-id]
  (format "pprint_tap_btn_id_%s" pane-id))

(defn thread-pprint-level-txt-id [pane-id]
  (format "pprint_level_txt_id_%s" pane-id))

(defn thread-pprint-meta-chk-id [pane-id]
  (format "pprint_meta_chk_id_%s" pane-id))

(defn thread-callstack-tree-cell [idx]
  (format "callstack_tree_cell_%d" idx))

(defn show-message [msg msg-type]
  (try
    (ui-utils/run-later
     (let [dialog (alert-dialog {:type msg-type
                                 :message msg
                                 :buttons [:close]})]
       (.show dialog)))
    (catch Exception _)))
