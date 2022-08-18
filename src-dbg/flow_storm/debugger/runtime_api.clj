(ns flow-storm.debugger.runtime-api
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.debugger.nrepl :as dbg-nrepl]
            [flow-storm.debugger.websocket :as websocket]
            [clojure.string :as str]
            [clojure.repl :as clj-repl])
  (:import [java.io Closeable]))

(declare ->LocalRuntimeApi)
(declare ->RemoteRuntimeApi)

(defstate rt-api
  :start (let [{:keys [local?] :as config} (mount/args)]
           (if local?

             (->LocalRuntimeApi)

             (let [env-kind (if (#{:shadow} (:repl-type config))
                              :cljs
                              :clj)
                   {:keys [repl-eval close-connection]} (dbg-nrepl/connect config)]
               (->RemoteRuntimeApi repl-eval env-kind close-connection))))
  :stop (when-let [cc (:close-connection rt-api)]
          (cc)))

(defprotocol RuntimeApiP
  (val-pprint [_ v opts])
  (shallow-val [_ v])
  (get-form [_ flow-id thread-id form-id])
  (timeline-count [_ flow-id thread-id])
  (timeline-entry [_ flow-id thread-id idx])
  (frame-data [_ flow-id thread-id idx])
  (bindings [_ flow-id thread-id idx])
  (callstack-tree-root-node [_ flow-id thread-id])
  (callstack-node-childs [_ node])
  (callstack-node-frame [_ node])
  (fn-call-stats [_ flow-id thread-id])
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id])
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts])
  (discard-flow [_ flow-id])

  (get-var-form-str [_ var-ns var-name])
  (eval-form [_ form-str opts])
  (def-value [_ v-name v])
  (get-all-namespaces [_])
  (get-all-vars-for-ns [_ nsname])
  (get-var-meta [_ var-ns var-name])
  (instrument-namespaces [_ nsnames profile])
  (uninstrument-namespaces [_ nanames])
  (interrupt-all-tasks [_])
  (clear-values-references [_]))

(defmacro local-call [f-symb & args]
  `((requiring-resolve '~(symbol "flow-storm.runtime.debuggers-api" (name f-symb))) ~@args))

(defrecord LocalRuntimeApi []

  RuntimeApiP

  (val-pprint [_ v opts] (local-call val-pprint v opts))
  (shallow-val [_ v] (local-call shallow-val v))
  (get-form [_ flow-id thread-id form-id] (local-call get-form flow-id thread-id form-id))
  (timeline-count [_ flow-id thread-id] (local-call timeline-count flow-id thread-id))
  (timeline-entry [_ flow-id thread-id idx] (local-call timeline-entry flow-id thread-id idx))
  (frame-data [_ flow-id thread-id idx] (local-call frame-data flow-id thread-id idx))
  (bindings [_ flow-id thread-id idx] (local-call bindings flow-id thread-id idx))
  (callstack-tree-root-node [_ flow-id thread-id] (local-call callstack-tree-root-node flow-id thread-id))
  (callstack-node-childs [_ node] (local-call callstack-node-childs node))
  (callstack-node-frame [_ node] (local-call callstack-node-frame node))
  (fn-call-stats [_ flow-id thread-id] (local-call fn-call-stats flow-id thread-id))
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id] (local-call find-fn-frames-light flow-id thread-id fn-ns fn-name form-id))
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts] (local-call search-next-frame-idx flow-id thread-id query-str from-idx opts))
  (discard-flow [_ flow-id] (local-call discard-flow flow-id))

  (get-var-form-str [_ var-ns var-name] (clj-repl/source-fn (symbol var-ns var-name)))
  (eval-form [_ form-str {:keys [instrument? instrument-options var-name ns]}]
    (let [ns-to-eval (find-ns (symbol ns))]
      (binding [*ns* ns-to-eval]

        (let [form (read-string
                    (if instrument?
                      (format "(flow-storm.runtime.debuggers-api/instrument* %s %s)" (pr-str instrument-options) form-str)
                      form-str))
              [v vmeta] (when var-name
                          (let [v (find-var (symbol ns var-name))]
                            [v (meta v)]))]

          ;; Don't eval the form in the same UI thread or
          ;; it will deadlock because the same thread can generate
          ;; a event that will be waiting for the UI thread
          (.start
           (Thread.
            (fn []
              (binding [*ns* ns-to-eval]
                (eval form)))))

          ;; when we evaluate a function from the repl we lose all meta
          ;; so when re-evaluating a var (probably a function) store and restore its meta
          (when v (reset-meta! v vmeta))))))

  (def-value [_ v-name vref] (local-call def-val-reference v-name vref))
  (get-all-namespaces [_] (mapv (comp str ns-name) (all-ns)))
  (get-all-vars-for-ns [_ nsname] (->> (ns-interns (symbol nsname)) keys (map str)))

  (get-var-meta [_ var-ns var-name]
    (-> (meta (resolve (symbol var-ns var-name)))
        (update :ns (comp str ns-name))))

  (instrument-namespaces [_ nsnames profile])
  (uninstrument-namespaces [_ nanames])

  (interrupt-all-tasks [_] (local-call interrupt-all-tasks))
  (clear-values-references [_] (local-call clear-values-references))
  )

(defmulti make-repl-expression (fn [env-kind fn-symb & _] [env-kind fn-symb]))

;;;;;;;;;;;;;;;;;;;;;;
;; For Clojure repl ;;
;;;;;;;;;;;;;;;;;;;;;;

(defmethod make-repl-expression [:clj 'get-var-form-str] [_ _ var-ns var-name] (format "(clojure.repl/source-fn (symbol %s %s))" (pr-str var-ns) (pr-str var-name)))
(defmethod make-repl-expression [:clj 'get-all-namespaces] [_ _] "(mapv (comp str ns-name) (all-ns))")
(defmethod make-repl-expression [:clj 'get-all-vars-for-ns] [_ _ nsname] (format "(->> (ns-interns '%s) keys (mapv str))" nsname))
(defmethod make-repl-expression '[:clj get-var-meta] [_ _ var-ns var-name] (format "(meta (var %s/%s))" var-ns var-name))

(defmethod make-repl-expression '[:clj instrument-namespaces]
  [_ _ nsnames profile]
  (let [nss-str (str/join " " nsnames)
        disable-set-str (if (= profile :light)
                          "#{:expr :anonymous-fn :binding}"
                          "#{}")]
    (format "(flow-storm.api/instrument-forms-for-namespaces #{%s} {:disable %s})" nss-str disable-set-str)))

(defmethod make-repl-expression '[:clj uninstrument-namespaces]
  [_ _ nsnames]
  (let [nss-str (str/join " " nsnames)]
    (format "(flow-storm.api/uninstrument-forms-for-namespaces #{%s})" nss-str)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; For ClojureScript repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod make-repl-expression [:cljs 'get-var-form-str] [_ _ var-ns var-name] (format "(dbg-api/cljs-source-fn %s/%s)" var-ns var-name))
(defmethod make-repl-expression [:cljs 'get-all-namespaces] [_ _] "(dbg-api/cljs-get-all-ns)")
(defmethod make-repl-expression [:cljs 'get-all-vars-for-ns] [_ _ nsname] (format "(dbg-api/cljs-get-ns-interns %s)" nsname))
(defmethod make-repl-expression [:cljs 'get-var-meta] [_ _ var-ns var-name] (format "(meta #'%s/%s)" var-ns var-name))

(defn- re-eval-all-ns-forms [eval-fn ns-names eval-opts]
  (let [all-ns-forms (eval-fn (format "(dbg-api/cljs-sorted-namespaces-sources %s)" (mapv symbol ns-names)))]
    (doseq [[ns-name ns-forms] all-ns-forms]
      (doseq [form-str ns-forms]
        (eval-form rt-api form-str (assoc eval-opts :ns ns-name))))))

(defrecord RemoteRuntimeApi [eval-str-expr env-kind close-connection]

  RuntimeApiP

  (val-pprint [_ ref-id opts] (websocket/sync-remote-api-request :val-pprint [ref-id opts]))
  (shallow-val [_ ref-id] (websocket/sync-remote-api-request :shallow-val [ref-id]))
  (get-form [_ flow-id thread-id form-id] (websocket/sync-remote-api-request :get-form [flow-id thread-id form-id]))
  (timeline-count [_ flow-id thread-id] (websocket/sync-remote-api-request :timeline-count [flow-id thread-id]))
  (timeline-entry [_ flow-id thread-id idx] (websocket/sync-remote-api-request :timeline-entry [flow-id thread-id idx]))
  (frame-data [_ flow-id thread-id idx] (websocket/sync-remote-api-request :frame-data [flow-id thread-id idx]))
  (bindings [_ flow-id thread-id idx] (websocket/sync-remote-api-request :bindings [flow-id thread-id idx]))
  (callstack-tree-root-node [_ flow-id thread-id] (websocket/sync-remote-api-request :callstack-tree-root-node [flow-id thread-id]))
  (callstack-node-childs [_ node] (websocket/sync-remote-api-request :callstack-node-childs [node]))
  (callstack-node-frame [_ node] (websocket/sync-remote-api-request :callstack-node-frame [node]))
  (fn-call-stats [_ flow-id thread-id] (websocket/sync-remote-api-request :fn-call-stats [flow-id thread-id]))
  (find-fn-frames-light [_ flow-id thread-id fn-ns fn-name form-id] (websocket/sync-remote-api-request :find-fn-frames-light [flow-id thread-id fn-ns fn-name form-id]))
  (search-next-frame-idx [_ flow-id thread-id query-str from-idx opts] (websocket/sync-remote-api-request :search-next-frame-idx [flow-id thread-id query-str from-idx opts]))
  (discard-flow [_ flow-id] (websocket/sync-remote-api-request :discard-flow [flow-id]))
  (def-value [_ v-name v] (websocket/sync-remote-api-request :def-value [v-name v]))

  (get-var-form-str [_ var-ns var-name] (eval-str-expr env-kind (make-repl-expression env-kind 'get-var-form-str var-ns var-name)))

  (eval-form [_ form-str {:keys [instrument? instrument-options ns]}]
    ;; TODO: figure out if it is a var and reset its meta
    (let [form-expr (if instrument?
                      (format "(flow-storm.runtime.debuggers-api/instrument* %s %s)" (pr-str instrument-options) form-str)
                      form-str)]
      (eval-str-expr env-kind form-expr ns)))


  (get-all-namespaces [_] (eval-str-expr env-kind (make-repl-expression env-kind 'get-all-namespaces )))
  (get-all-vars-for-ns [_ nsname] (eval-str-expr env-kind (make-repl-expression env-kind 'get-all-vars-for-ns nsname)))
  (get-var-meta [_ var-ns var-name] (eval-str-expr env-kind (make-repl-expression env-kind 'get-var-meta var-ns var-name)))

  (instrument-namespaces [this nsnames profile]
    (let [opts (if (= profile :light)
                 {:disable #{:expr :binding :anonymous-fn}}
                 {})]
      (case env-kind

        ;; for ClojureScript do it via the repl
        ;; TODO: generate instrumentation events
        :cljs (re-eval-all-ns-forms (partial eval-str-expr env-kind) nsnames {:instrument? true
                                                                              :instrument-options opts})
       :clj (websocket/sync-remote-api-request :instrument-namespaces [nsnames opts]))))

  (uninstrument-namespaces [this nsnames]
    (case env-kind
      ;; for ClojureScript do it via the repl
      ;; TODO: generate uninstrumentation events
      :cljs (re-eval-all-ns-forms (partial eval-str-expr env-kind) nsnames {:instrument? false})

      ;; for Clojure just call the api
      :clj (websocket/sync-remote-api-request :uninstrument-namespaces [nsnames])))

  (interrupt-all-tasks [_] (websocket/sync-remote-api-request :interrupt-all-tasks []))

  (clear-values-references [_] (websocket/sync-remote-api-request :clear-values-references []))


  java.io.Closeable
  (close [_] (close-connection))
  )

(defn get-and-eval-form [api var-ns var-name instrument?]
  (let [var-form-str (get-var-form-str api var-ns var-name)]
    (eval-form rt-api var-form-str {:instrument? instrument?
                                    :ns var-ns
                                    :var-name var-name})))
