(ns flow-storm.debugger.state

  "Sub component that manages the state of the debugger.
  This is the state for supporting the UI, not the runtime part, where the
  timelines are recorded.

  All the state is inside one atom `state` which is specified by the `::state` spec."

  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.utils :refer [pop-n]]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s])
  (:import [javafx.stage Stage]))

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

(s/def :return/kind #{:waiting :unwind :return})

(s/def :flow-storm/frame (s/keys :req [:return/kind]
                                 :req-un [:flow-storm.frame/fn-call-idx
                                          :flow-storm.frame/parent-fn-call-idx
                                          :flow-storm/fn-name
                                          :flow-storm/fn-ns
                                          :flow-storm/form-id
                                          :flow-storm.frame/args-vec
                                          :flow-storm.frame/expr-executions]
                                 :opt-un [:flow-storm.frame/ret
                                          :flow-storm.frame/throwable]))

(s/def :thread/id int?)
(s/def :thread/name string?)
(s/def :thread/blocked? boolean?)
(s/def :thread/curr-timeline-entry (s/nilable :flow-storm/timeline-entry))
(s/def :thread/curr-frame :flow-storm/frame)

(s/def :thread.ui.callstack-tree-hidden-fns/ref (s/keys :req-un [:flow-storm/fn-name
                                                                 :flow-storm/fn-ns]))

(s/def :thread.ui/selected-functions-list-fn (s/nilable
                                              (s/keys :req-un [:flow-storm/fn-name
                                                               :flow-storm/fn-ns
                                                               :flow-storm/form-id])))

(s/def :thread.ui/callstack-tree-hidden-fns (s/coll-of :thread.ui.callstack-tree-hidden-fns/ref))

(s/def :navigation-history/history (s/coll-of :flow-storm/timeline-entry))
(s/def :thread/navigation-history (s/keys :req-un [:navigation-history/head-pos
                                                   :navigation-history/history]))

(s/def :thread.exception/ex-type string?)
(s/def :thread.exception/ex-message string?)
(s/def :thread/exception (s/keys :req-un [:flow-storm/fn-name
                                          :flow-storm/fn-ns
                                          :thread.exception/ex-type
                                          :thread.exception/ex-message]))
(s/def :flow/exceptions (s/coll-of :thread/exception))

(s/def :flow/thread (s/keys :req [:thread/id
                                  :thread/curr-timeline-entry
                                  :thread/navigation-history]
                            :opt [:thread/curr-frame
                                  :thread.ui/callstack-tree-hidden-fns
                                  :thread.ui/selected-functions-list-fn]))

(s/def :flow/threads (s/map-of :thread/id :flow/thread))

(s/def :flow/id int?)
(s/def :flow/flow (s/keys :req [:flow/id
                                :flow/threads
                                :flow/exceptions]
                          :req-un [::timestamp]))

(s/def :flow/flows (s/map-of :flow/id :flow/flow))
(s/def :thread/info (s/keys :req [:flow/id
                                  :thread/id
                                  :thread/name]
                            :opt [:thread/blocked?]))
(s/def :flow/threads-info (s/map-of :flow/id :thread/info))

(s/def :printer/enable? boolean?)
(s/def :printer/source-expr any?)
(s/def :printer/transform-expr-str string?)
(s/def :printer/printer (s/keys :req-un [:flow-storm/coord
                                         ::print-level
                                         ::print-length
                                         :printer/enable?
                                         :printer/transform-expr-str
                                         :printer/source-expr]))

(s/def :printer/flow-printers (s/map-of :flow-storm/form-id
                                   (s/map-of :flow-storm/coord
                                             :printer/printer)))

(s/def :printer/printers (s/map-of :flow/id :printer/flow-printers))

(s/def :ui/selected-font-size-style-idx int?)
(s/def :ui/selected-theme #{:light :dark})
(s/def :ui/selected-tool #{:tool-flows :tool-browser :tool-outputs :tool-docs})
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

(s/def ::jfx-stage #(instance? Stage %))
(s/def :ui/jfx-stages (s/coll-of ::jfx-stage))

(s/def :task/event-key keyword?)
(s/def :task/id any?)
(s/def ::pending-tasks-subscriptions (s/map-of (s/tuple :task/event-key :task/id)
                                               fn?))
(s/def ::clojure-storm-env? boolean?)
(s/def ::local-mode? boolean?)

(s/def :config/env-kind #{:clj :cljs})
(s/def :config/storm?      boolean?)
(s/def :config/flow-storm-nrepl-middleware? boolean?)
(s/def :status/recording?  boolean?)
(s/def :status/total-order-recording? boolean?)
(s/def :status/breakpoints (s/coll-of (s/tuple :flow-storm/fn-ns :flow-storm/fn-name)))

(s/def ::runtime-config (s/nilable
                         (s/keys :req-un [:config/env-kind
                                          :config/storm?
                                          :config/flow-storm-nrepl-middleware?
                                          :status/recording?
                                          :status/total-order-recording?
                                          :status/breakpoints])))

(s/def :repl/kind #{:nrepl})
(s/def :repl/type #{:shadow :clojure})
(s/def :repl/port int?)
(s/def :repl.cljs/build-id keyword?)

(s/def :config/repl (s/nilable
                     (s/keys :req [:repl/kind
                                   :repl/type
                                   :repl/port]
                             :opt [:repl.cljs/build-id])))

(s/def :config/debugger-host string?)
(s/def :config/debugger-ws-port int?)
(s/def :config/runtime-host string?)
(s/def :config/debug-mode? boolean?)

(s/def ::debugger-config (s/keys :req-un [:config/repl
                                          :config/debugger-host
                                          :config/debugger-ws-port
                                          :config/runtime-host
                                          :config/debug-mode?]))

(s/def :bookmark/id (s/tuple :flow/id :thread/id int?))
(s/def ::bookmarks (s/map-of :bookmark/id string?))


(s/def :data-window/breadcrums-box :ui.object/node)
(s/def :data-window/visualizers-combo-box :ui.object/node)
(s/def :data-window/val-box :ui.object/node)
(s/def :data-window/type-lbl :ui.object/node)

(s/def :visualizer/on-create  ifn?)
(s/def :visualizer/on-update  ifn?)
(s/def :visualizer/on-destroy ifn?)

(s/def :data-window.frame/val-data map?)
(s/def :data-window.frame/visualizer-combo :ui.object/node)
(s/def :data-window.frame/visualizer (s/keys :req-un [:visualizer/on-create]
                                             :opt-un [:visualizer/on-update
                                                      :visualizer/on-destroy]))
(s/def :fx/node :ui.object/node)
(s/def :data-window.frame/visualizer-val-ctx (s/keys :req [:fx/node]))
(s/def :data-window/frame (s/keys :req-un [:data-window.frame/val-data
                                           :data-window.frame/visualizer-combo
                                           :data-window.frame/visualizer
                                           :data-window.frame/visualizer-val-ctx]))

(s/def :data-window/stack (s/coll-of :data-window/frame))
(s/def :data-windows/data-window (s/keys :req-un [:data-window/breadcrums-box
                                                  :data-window/visualizers-combo-box
                                                  :data-window/val-box
                                                  :data-window/type-lbl
                                                  :data-window/stack]))
(s/def :data-window/id any?)
(s/def ::data-windows (s/map-of :data-window/id :data-windows/data-window))

(s/def ::state (s/keys :req-un [:flow/flows
                                :flow/threads-info
                                :printer/printers
                                :ui/selected-font-size-style-idx
                                :ui/selected-theme
                                :ui/selected-tool
                                :ui/extra-styles
                                :ui/jfx-nodes-index
                                :ui/jfx-stages
                                ::pending-tasks-subscriptions
                                ::connection-status
                                ::local-mode?
                                ::runtime-config
                                ::debugger-config
                                ::bookmarks
                                ::data-windows]))

(defn initial-state [{:keys [theme styles local? port repl-type debugger-host ws-port runtime-host] :as config}]
  {:flows {}
   :printers {}
   :selected-font-size-style-idx 0
   :threads-info {}
   :selected-theme (case theme
                     :light :light
                     :dark  :dark
                     :auto  ((requiring-resolve 'flow-storm.debugger.ui.utils/get-current-os-theme))
                     :light)
   :selected-tool :tool-flows
   :local-mode? (boolean local?)
   :extra-styles styles
   :jfx-nodes-index {}
   :jfx-stages []
   :pending-tasks-subscriptions {}
   :runtime-config nil
   :connection-status {:ws-ready? false
                       :repl-ready? false}
   :debugger-config {:repl
                     (when port
                       (cond-> {:repl/kind :nrepl
                                :repl/type (or repl-type :clojure)
                                :repl/port port}
                         (#{:shadow} repl-type) (assoc :repl.cljs/build-id (:build-id config))))
                     :debugger-host (or debugger-host "localhost")
                     :debugger-ws-port (or ws-port 7722)
                     :runtime-host (or runtime-host "localhost")
                     :debug-mode? false}
   :bookmarks {}
   :visualizers {}
   :data-windows {}})

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

(defn set-selected-tool [tool]
  (swap! state assoc :selected-tool tool))

(defn selected-tool []
  (get @state :selected-tool))

(defn set-runtime-config [config]
  (swap! state assoc :runtime-config config))

(defn create-flow [flow-id timestamp]
  ;; if a flow for `flow-id` already exist we discard it and
  ;; will be GCed

  (swap! state assoc-in [:flows flow-id] {:flow/id flow-id
                                          :flow/threads {}
                                          :flow/exceptions []
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
          :thread/callstack-tree-hidden-fns #{}
          :thread/navigation-history {:head-pos 0
                                      :history [{:fn-call-idx -1 ;; dummy entry
                                                 :idx         -1}]}}))

(defn get-thread [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id]))

(defn remove-thread [flow-id thread-id]
  (swap! state update-in [:flows flow-id :flow/thrteads] dissoc thread-id))

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

(defn add-printer [flow-id form-id coord printer-data]
  (swap! state assoc-in [:printers flow-id form-id coord] printer-data))

(defn printers [flow-id]
  (get-in @state [:printers flow-id]))

(defn remove-printer [flow-id form-id coord]
  (swap! state update-in [:printers flow-id form-id] dissoc coord))

(defn update-printer [flow-id form-id coord k new-val]
  (swap! state assoc-in [:printers flow-id form-id coord k] new-val))

(def font-size-styles ["flowstorm/styles/font-size-sm.css"
                       "flowstorm/styles/font-size-md.css"
                       "flowstorm/styles/font-size-lg.css"
                       "flowstorm/styles/font-size-xl.css"])

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
        default-styles (str (io/resource "flowstorm/styles/styles.css"))
        theme-base-styles (str (io/resource (case selected-theme
                                              :dark  "flowstorm/styles/theme_dark.css"
                                              :light "flowstorm/styles/theme_light.css")))
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

;;;;;;;;;;;;;;;
;; Bookmarks ;;
;;;;;;;;;;;;;;;

(defn add-bookmark [flow-id thread-id idx text]
  (swap! state assoc-in [:bookmarks [flow-id thread-id idx]] text))

(defn remove-bookmark [flow-id thread-id idx]
  (swap! state update-in [:bookmarks] dissoc [flow-id thread-id idx]))

(defn remove-bookmarks [flow-id]
  (swap! state update-in [:bookmarks]
         (fn [bookmarks]
           (reduce-kv (fn [bks [fid :as bkey] btext]
                        (if (= fid flow-id)
                          bks
                          (assoc bks bkey btext)))
                      {}
                      bookmarks))))

(defn all-bookmarks []
  (reduce-kv (fn [bks [flow-id thread-id idx] text]
               (conj bks
                     {:flow-id flow-id
                      :thread-id thread-id
                      :idx idx
                      :text text}))
             []
             (get-in @state [:bookmarks])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Navigation undo system ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *undo-redo-jump* false)

(defn update-nav-history

  "Add to nav history if we are jumping to a different frame,
  else update the head idx. If the head is not at the end, redo history
  will be discarded."

  [flow-id thread-id {:keys [fn-call-idx] :as tentry}]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/navigation-history]
         (fn [nav-hist]
           (let [{:keys [history head-pos] :as nav-hist} (if (and (not *undo-redo-jump*)
                                                                  (< (:head-pos nav-hist) (dec (count (:history nav-hist)))))
                                                           (-> nav-hist
                                                               (update :history subvec 0 (:head-pos nav-hist))
                                                               (update :head-pos dec))
                                                           nav-hist)
                 changing-frames? (not= fn-call-idx (get-in history [head-pos :fn-call-idx]))]
             (if changing-frames?
               (-> nav-hist
                   (update :history conj tentry)
                   (update :head-pos inc))

               (assoc-in nav-hist [:history head-pos] tentry))))))

(defn current-nav-history-entry [flow-id thread-id]
  (let [{:keys [history head-pos]} (get-in @state [:flows flow-id :flow/threads thread-id :thread/navigation-history])]
    (get history head-pos)))

(defn undo-nav-history

  "Move the nav history head back and return it's idx."

  [flow-id thread-id]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/navigation-history :head-pos]
         (fn [p]
           (if (> p 1)
             (dec p)
             p)))
  (current-nav-history-entry flow-id thread-id))

(defn redo-nav-history

  "Move the nav history head forward and return it's idx."

  [flow-id thread-id]
  (swap! state update-in [:flows flow-id :flow/threads thread-id :thread/navigation-history]
         (fn [{:keys [history head-pos] :as h}]
           (assoc h :head-pos (if (< (inc head-pos) (count history))
                                (inc head-pos)
                                head-pos))))
  (current-nav-history-entry flow-id thread-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Function unwind (throws) ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-fn-unwind [{:keys [flow-id ex-hash] :as unwind-data}]
  (swap! state update-in [:flows flow-id :flow/exceptions]
         (fn [exceptions]
           ;; just capture the exceptions at first fn unwind
           (if-not (some #(= (:ex-hash %) ex-hash) exceptions)
             (conj exceptions unwind-data)
             exceptions))))

(defn flow-exceptions [flow-id]
  (get-in @state [:flows flow-id :flow/exceptions]))

;;;;;;;;;;;;;;;;
;; JFX Stages ;;
;;;;;;;;;;;;;;;;

(defn jfx-stages []
  (get @state :jfx-stages))

(defn unregister-jfx-stage! [stg]
  (swap! state update :jfx-stages (fn [stgs] (filterv #(not= % stg) stgs))))

(defn main-jfx-stage []
  (-> @state :jfx-stages first))

(defn reset-theming []
  (let [stages (jfx-stages)
        new-stylesheets (current-stylesheets)]
    (doseq [stage stages]
      (let [scene (.getScene stage)
            scene-stylesheets (.getStylesheets scene)]
        (.clear scene-stylesheets)
        (.addAll scene-stylesheets new-stylesheets)))))

(defn register-jfx-stage! [stg]
  (swap! state update :jfx-stages conj stg)
  (reset-theming))

;;;;;;;;;;;;;;;;;;;;
;; Functions list ;;
;;;;;;;;;;;;;;;;;;;;

(defn set-selected-function-list-fn [flow-id thread-id fn-call]
  (swap! state assoc-in [:flows flow-id :flow/threads thread-id :thread.ui/selected-functions-list-fn] fn-call))

(defn get-selected-function-list-fn [flow-id thread-id]
  (get-in @state [:flows flow-id :flow/threads thread-id :thread.ui/selected-functions-list-fn]))

;;;;;;;;;;;;;;;;;;
;; Data Windows ;;
;;;;;;;;;;;;;;;;;;

(defn data-window-create [dw-id nodes-map]
  (swap! state assoc-in [:data-windows dw-id] (assoc nodes-map :stack ())))

(defn data-window [dw-id]
  (get-in @state [:data-windows dw-id]))

(defn data-window-current-val [dw-id]
  (-> @state :data-windows dw-id :stack first :val-data))

(defn data-window-remove [dw-id]
  (swap! state update :data-windows dissoc dw-id))

(defn data-windows []
  (:data-windows @state))

(defn data-window-push-frame [dw-id val-frame]
  (swap! state update-in [:data-windows dw-id :stack] conj val-frame))

(defn data-window-update-top-frame

  "Swaps the top frame of the dw-id data-window stack by new-frame-data.
  Returns the replaced (old top) frame."

  [dw-id new-frame-data]
  (let [prev-top (peek (:stack (data-window dw-id)))]
    (swap! state update-in [:data-windows dw-id :stack]
           (fn [stack]
             (conj (pop stack) (merge (peek stack) new-frame-data))))
    prev-top))

(defn data-window-pop-stack-to-depth

  "Pop the dw-id data-window stack so it is left with depth number of elements.
  Returns popped elements."

  [dw-id depth]
  (let [stack (:stack (data-window dw-id))
        pop-cnt (- (count stack) depth)
        popped (pop-n stack pop-cnt)]
    (swap! state update-in [:data-windows dw-id :stack] pop-n pop-cnt)
    popped))


;;;;;;;;;;;
;; Other ;;
;;;;;;;;;;;

(defn clojure-storm-env? []
  (get-in @state [:runtime-config :storm?]))

(defn flow-storm-nrepl-middleware-available? []
  (get-in @state [:runtime-config :flow-storm-nrepl-middleware?]))
