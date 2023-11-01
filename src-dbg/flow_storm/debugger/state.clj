(ns flow-storm.debugger.state

  "Sub component that manages the state of the debugger.
  This is the state for supporting the UI, not the runtime part, where the
  timelines are recorded.

  All the state is inside one atom `state` which is specified by the `::state` spec."

  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]))

(s/def ::timestamp                (s/nilable int?))
(s/def ::print-level int?)
(s/def ::print-length int?)

(s/def :flow-storm/timeline-entry map?)
(s/def :flow-storm/val-id         int?)
(s/def :flow-storm/val-ref        record? #_(s/keys :req-un [:flow-storm/val-id]))
(s/def :flow-storm/fn-name        string?)
(s/def :flow-storm/fn-ns          string?)
(s/def :flow-storm/form-id        int?)
(s/def :flow-storm/coord          (s/coll-of int?))

(s/def :flow-storm.frame/ret                :flow-storm/val-ref)
(s/def :flow-storm.frame/fn-call-idx        int?)
(s/def :flow-storm.frame/parent-fn-call-idx (s/nilable int?))
(s/def :flow-storm.frame/args-vec           any? #_(s/coll-of :flow-storm/val-ref)) ;; TODO: fix this https://clojure.atlassian.net/browse/CLJ-1975
(s/def :flow-storm.frame/expr-executions    (s/coll-of :flow-storm/timeline-entry)) ;; TODO: this could be refined, since they can only by :expr and :fn-return

(s/def :flow-storm/frame (s/keys :req-un [:flow-storm.frame/ret
                                          :flow-storm.frame/fn-call-idx
                                          :flow-storm.frame/parent-fn-call-idx
                                          :flow-storm/fn-name
                                          :flow-storm/fn-ns
                                          :flow-storm/form-id
                                          :flow-storm.frame/args-vec
                                          :flow-storm.frame/expr-executions]))

(s/def :thread/id int?)
(s/def :thread/name string?)
(s/def :thread/blocked? boolean?)
(s/def :thread/curr-timeline-entry (s/nilable :flow-storm/timeline-entry))
(s/def :thread/curr-frame :flow-storm/frame)

(s/def :thread.ui.callstack-tree-hidden-fns/ref (s/keys :req-un [:flow-storm/fn-name
                                                                 :flow-storm/fn-ns]))
(s/def :thread.ui/callstack-tree-hidden-fns (s/coll-of :thread.ui.callstack-tree-hidden-fns/ref))
(s/def :flow/thread (s/keys :req [:thread/id
                                  :thread/curr-timeline-entry]
                            :opt [:thread/curr-frame
                                  :thread.ui/callstack-tree-hidden-fns]))
(s/def :flow/threads (s/map-of :thread/id :flow/thread))

(s/def :flow/id (s/nilable int?))
(s/def :flow/flow (s/keys :req [:flow/id
                                :flow/threads
                                :flow/execution-expr]
                          :req-un [::timestamp]))

(s/def :flow/flows (s/map-of :flow/id :flow/flow))
(s/def :thread/info (s/keys :req [:flow/id
                                  :thread/id
                                  :thread/name]
                            :opt [:thread/blocked?]))
(s/def :flow/threads-info (s/map-of :flow/id :thread/info))

(s/def :printer/enable? boolean?)
(s/def :printer/expr any?)
(s/def :printer/printer (s/keys :req-un [:flow-storm/coord
                                         ::print-level
                                         ::print-length
                                         :printer/enable?
                                         :printer/expr]))
(s/def :printer/printers (s/map-of :flow-storm/form-id
                                   (s/map-of :flow-storm/coord
                                             :printer/printer)))
(s/def :ui/selected-flow-id :flow/id)
(s/def :ui/selected-font-size-style-idx int?)
(s/def :ui/selected-theme #{:light :dark})
(s/def :ui/extra-styles (s/nilable string?))
(s/def ::ws-ready? boolean?)
(s/def ::repl-ready? boolean?)
(s/def ::connection-status (s/keys :req-un [::ws-ready? ::repl-ready?]))

(s/def :ui.object/id string?)
(s/def :ui.object/node any?)

(s/def :ui.jfx-nodes-index/flow-id (s/nilable (s/or :flow-id :flow/id
                                                    :no-flow #{:no-flow})))

(s/def :ui/jfx-nodes-index (s/map-of (s/tuple :ui.jfx-nodes-index/flow-id
                                              (s/nilable :thread/id)
                                              :ui.object/id)
                                     (s/coll-of :ui.object/node)))

(s/def :task/event-key keyword?)
(s/def :task/id any?)
(s/def ::pending-tasks-subscriptions (s/map-of (s/tuple :task/event-key :task/id)
                                               fn?))
(s/def ::clojure-storm-env? boolean?)
(s/def ::local-mode? boolean?)

(s/def :config/env-kind #{:clj :cljs})
(s/def :config/storm?   boolean?)
(s/def :status/recording? boolean?)
(s/def :status/total-order-recording? boolean?)
(s/def :status/breakpoints (s/coll-of (s/tuple :flow-storm/fn-ns :flow-storm/fn-name)))

(s/def ::runtime-config (s/nilable
                         (s/keys :req-un [:config/env-kind
                                          :config/storm?
                                          :status/recording?
                                          :status/total-order-recording?
                                          :status/breakpoints])))

(s/def :repl/kind #{:nrepl})
(s/def :repl/type #{:shadow})
(s/def :repl/port int?)
(s/def :repl.cljs/build-id keyword?)

(s/def :config/repl (s/nilable
                     (s/keys :req [:repl/kind
                                   :repl/type
                                   :repl/port
                                   :repl.cljs/build-id])))
(s/def :config/debugger-host string?)
(s/def :config/runtime-host string?)
(s/def :config/debug-mode? boolean?)

(s/def ::debugger-config (s/keys :req-un [:config/repl
                                          :config/debugger-host
                                          :config/runtime-host
                                          :config/debug-mode?]))

(s/def ::state (s/keys :req-un [:flow/flows
                                :flow/threads-info
                                :printer/printers
                                :ui/selected-font-size-style-idx
                                :ui/selected-theme
                                :ui/extra-styles
                                :ui/jfx-nodes-index
                                ::pending-tasks-subscriptions
                                ::connection-status
                                ::local-mode?
                                ::runtime-config
                                ::debugger-config]
                       :opt-un [:ui/selected-flow-id]))

(defn initial-state [{:keys [theme styles local? port repl-type debugger-host runtime-host] :as config}]
  {:flows {}
   :selected-flow-id nil
   :printers {}
   :selected-font-size-style-idx 0
   :threads-info {}
   :selected-theme (case theme
                     :light :light
                     :dark  :dark
                     :auto  (ui-utils/get-current-os-theme)
                     :light)
   :local-mode? (boolean local?)
   :extra-styles styles
   :jfx-nodes-index {}
   :pending-tasks-subscriptions {}
   :runtime-config nil
   :connection-status {:ws-ready? false
                       :repl-ready? false}
   :debugger-config {:repl
                     (when port
                       (cond-> {:repl/kind :nrepl
                                :repl/type repl-type
                                :repl/port port}
                         (#{:shadow} repl-type) (assoc :repl.cljs/build-id (:build-id config))))
                     :debugger-host (or debugger-host "localhost")
                     :runtime-host (or runtime-host "localhost")
                     :debug-mode? false}})

(def register-and-init-stage!

  "Globally available function, setup by `flow-storm.debugger.ui.main/start-theming-system`
   to register stages so they are themed and listen to theme changes"

  nil)

;; so linter doesn't complain
(declare state)
(declare fn-call-stats-map)
(declare flow-thread-indexers)

(defstate state
  :start (fn [config] (atom (initial-state config)))
  :stop (fn [] nil))

(defn local-mode? []
  (:local-mode? @state))

(defn set-connection-status [conn-key status]
  (let [k ({:ws   :ws-ready?
            :repl :repl-ready?} conn-key)]
    (swap! state assoc-in [:connection-status k] status)))

(defn connection-status []
  (get @state :connection-status))

(defn env-kind []
  (let [state @state
        k (get-in state [:runtime-config :env-kind])]
    (if-not k
      ;; if we can determine if we are in :clj or :cljs because
      ;; we haven't connected to the runtime yet, lets guess from the debugger config
      (let [{:keys [repl]} (:debugger-config state)]
        (if (#{:shadow} (:repl/type repl))
          :cljs
          :clj))
      k)))

(defn repl-config []
  (get-in @state [:debugger-config :repl]))

(defn debugger-config []
  (get @state :debugger-config))

(defn toggle-debug-mode []
  (swap! state update-in [:debugger-config :debug-mode?] not))

(defn set-runtime-config [config]
  (swap! state assoc :runtime-config config))

(defn create-flow [flow-id form-ns form timestamp]
  ;; if a flow for `flow-id` already exist we discard it and
  ;; will be GCed

  (swap! state assoc-in [:flows flow-id] {:flow/id flow-id
                                          :flow/threads {}
                                          ;; the form that started the flow
                                          :flow/execution-expr {:ns form-ns
                                                                :form form}
                                          :timestamp timestamp}))

(defn remove-flow [flow-id]
  (swap! state update :flows dissoc flow-id))

(defn all-flows-ids []
  (keys (get @state :flows)))

(defn update-thread-info [thread-id info]
  (swap! state assoc-in [:threads-info thread-id] info))

(defn get-thread-info [thread-id]
  (get-in @state [:threads-info thread-id]))

(defn get-flow [flow-id]
  (get-in @state [:flows flow-id]))

(defn create-thread [flow-id thread-id]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id]
         {:thread/id thread-id
          :thread/curr-timeline-entry nil
          :thread/callstack-tree-hidden-fns #{}}))

(defn get-thread [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id]))

(defn current-timeline-entry [flow-id thread-id]
  (:thread/curr-timeline-entry (get-thread flow-id thread-id)))

(defn current-idx [flow-id thread-id]
  (:idx (current-timeline-entry flow-id thread-id)))

(defn set-current-timeline-entry [flow-id thread-id entry]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-timeline-entry] entry))

(defn set-current-frame [flow-id thread-id frame-data]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread/curr-frame] frame-data))

(defn current-frame [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id :thread/curr-frame]))

(defn callstack-tree-hide-fn [flow-id thread-id fn-name fn-ns]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns] conj {:name fn-name :ns fn-ns}))

(defn callstack-tree-hidden? [flow-id thread-id fn-name fn-ns]
  (let [hidden-set (get-in @state [:flows flow-id :flow/threads thread-id :thread/callstack-tree-hidden-fns])]
    (contains? hidden-set {:name fn-name :ns fn-ns})))

(defn add-printer [form-id coord printer-data]
  (swap! state assoc-in [:printers form-id coord] printer-data))

(defn printers []
  (get @state :printers))

(defn remove-printer [form-id coord]
  (swap! state update-in [:printers form-id] dissoc coord))

(defn update-printer [form-id coord k new-val]
  (swap! state assoc-in [:printers form-id coord k] new-val))

(def font-size-styles ["font-size-sm.css"
                       "font-size-md.css"
                       "font-size-lg.css"
                       "font-size-xl.css"])

(defn inc-font-size []
  (-> (swap! state update :selected-font-size-style-idx
             (fn [idx] (min (dec (count font-size-styles))
                            (inc idx))))
      :selected-font-size-style-idx))

(defn dec-font-size []
  (-> (swap! state update :selected-font-size-style-idx
             (fn [idx] (max 0 (dec idx))))
      :selected-font-size-style-idx))

(defn set-theme [theme]
  (swap! state assoc :selected-theme theme))

(defn rotate-theme []
  (swap! state update :selected-theme {:light :dark
                                       :dark :light}))


(defn current-stylesheets []
  (let [{:keys [selected-theme extra-styles selected-font-size-style-idx]} @state
        default-styles (str (io/resource "styles.css"))
        theme-base-styles (str (io/resource (case selected-theme
                                              :dark  "theme_dark.css"
                                              :light "theme_light.css")))
        font-size-style (-> (get font-size-styles selected-font-size-style-idx)
                            io/resource
                            str)
        extra-styles (when extra-styles
                       (str (io/as-url (io/file extra-styles))))]
    (cond-> [theme-base-styles
             default-styles]
      extra-styles (conj extra-styles)
      true (conj font-size-style))))

;;;;;;;;;;;;;;;;;;;;;;;
;; JFX objects index ;;
;;;;;;;;;;;;;;;;;;;;;;;

;; Because scene.lookup doesn't work if you lookup before a layout pass
;; So adding a node and looking it up quickly sometimes it doesn't work

;; ui objects references with a static application lifetime
;; objects stored here will not be collected ever

(defn store-obj

  "Store an object on an index by [flow-id, thread-id, object-id].
  Ment to be used to store javafx.scene.Node objects because
  scene.lookup doesn't work if you lookup before a layout pass so
  adding a node and looking it up quickly sometimes doesn't work.

  More than one object can be stored under the same key.

  Objects stored with `store-obj` can be retrived with `obj-lookup`."

  ([obj-id obj-ref]
   (store-obj :no-flow nil obj-id obj-ref))

  ([flow-id obj-id obj-ref]
   (store-obj flow-id nil obj-id obj-ref))

  ([flow-id thread-id obj-id obj-ref]
   (let [k [flow-id thread-id obj-id]]
     (swap! state update-in [:jfx-nodes-index k] conj obj-ref))))

(defn obj-lookup

  "Retrieve objects stored by `store-obj`.
  Returns a vector of all the objects stored under the requested key."

  ([obj-id]
   (obj-lookup :no-flow nil obj-id))

  ([flow-id obj-id]
   (obj-lookup flow-id nil obj-id))

  ([flow-id thread-id obj-id]
   (let [k [flow-id thread-id obj-id]]
     (get-in @state [:jfx-nodes-index k]))))

(defn clean-objs

  "Clear objects stored by `store-obj` under a specific flow or
  specific thread."

  ([] (clean-objs nil nil))
  ([flow-id]
   (swap! state (fn [s]
                  (update s :jfx-nodes-index
                          (fn [objs]
                            (reduce-kv (fn [ret [fid tid oid] o]
                                         (if (= fid flow-id)
                                           ret
                                           (assoc ret [fid tid oid] o)))
                                       {}
                                       objs))))))
  ([flow-id thread-id]
   (swap! state (fn [s]
                  (update s :jfx-nodes-index
                          (fn [objs]
                            (reduce-kv (fn [ret [fid tid oid] o]
                                         (if (and (= fid flow-id)
                                                  (= tid thread-id))
                                           ret
                                           (assoc ret [fid tid oid] o)))
                                       {}
                                       objs)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pending tasks sub-system ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn subscribe-to-task-event [event-key task-id callback]
  (swap! state assoc-in [:pending-tasks-subscriptions [event-key task-id]] callback))

(defn dispatch-task-event [event-key task-id data]
  (when-let [cb (get-in @state [:pending-tasks-subscriptions [event-key task-id]])]
    (cb data)))

;;;;;;;;;;;
;; Other ;;
;;;;;;;;;;;

(defn clojure-storm-env? []
  (get-in @state [:runtime-config :storm?]))
