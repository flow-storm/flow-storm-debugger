(ns flow-storm.runtime.debuggers-api
  (:require [flow-storm.runtime.indexes.api :as indexes-api]            
            [flow-storm.utils :as utils :refer [log]]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as runtime-values :refer [reference-value! get-reference-value]]
            [clojure.core.async :as async]
            #?@(:clj [[hansel.api :as hansel]
                      [flow-storm.tracer :as tracer]
                      [hansel.instrument.utils :as hansel-inst-utils]])))

;; Utilities for long interruptible tasks

(def interruptible-tasks
  "A map from task-id -> interrupt-ch"
  (atom {}))

(defn- submit-interruptible-task! [f args]
  (let [task-id (str (utils/rnd-uuid))
        interrupt-ch (async/promise-chan)
        on-progress (fn [progress] (rt-events/publish-event! (rt-events/make-task-progress-event task-id progress)))]
    (swap! interruptible-tasks assoc task-id interrupt-ch)
    (async/go
      (let [task-result (async/<! (apply f (-> [] (into args) (into [interrupt-ch on-progress]))))]
        (log (utils/format "Task %s finished with : %s" task-id task-result))
        (rt-events/publish-event! (rt-events/make-task-result-event task-id task-result))))
    (log (utils/format "Submited interruptible task with task-id: %s" task-id))
    task-id))

(defn interrupt-task [task-id]
  (async/put! (get @interruptible-tasks task-id) true))

(defn interrupt-all-tasks []
  (doseq [int-ch (vals @interruptible-tasks)]
    (async/put! int-ch true)))

(defn val-pprint [vref opts]
  (let [v (get-reference-value vref)]
    (runtime-values/val-pprint v opts)))

(defn shallow-val [vref]
  (let [v (get-reference-value vref)]
    (runtime-values/shallow-val v)))

(def def-value runtime-values/def-value)
(def tap-value runtime-values/tap-value)

(defn get-form [flow-id thread-id form-id]
  (let [form (indexes-api/get-form flow-id thread-id form-id)]
    (if (:multimethod/dispatch-val form)
      (update form :multimethod/dispatch-val reference-value!)
      form)))

(def timeline-count indexes-api/timeline-count)

(defn- reference-frame-data! [{:keys [dispatch-val] :as frame-data}]
  (cond-> frame-data
    true         (dissoc :frame)
    dispatch-val (update :dispatch-val reference-value!)
    true         (update :args-vec reference-value!)
    true         (update :ret reference-value!)
    true         (update :bindings (fn [bs]
                                     (mapv #(update % :value reference-value!) bs)))
    true         (update :expr-executions (fn [ee]
                                            (mapv #(update % :result reference-value!) ee)))))

(defn timeline-entry [flow-id thread-id idx]
  (let [entry (indexes-api/timeline-entry flow-id thread-id idx)
        ref-entry (case (:timeline/type entry)
                    :frame (reference-frame-data! entry)
                    :expr (update entry :result reference-value!))]
    ref-entry)) 

(defn frame-data [flow-id thread-id idx]
  (let [frame-data (indexes-api/frame-data flow-id thread-id idx)]
    (reference-frame-data! frame-data)))

(defn bindings [flow-id thread-id idx]
  (let [bs-map (indexes-api/bindings flow-id thread-id idx)]
    (reduce-kv (fn [bs s v]
                 (assoc bs s (reference-value! v)))
               {}
               bs-map)))

(defn callstack-tree-root-node [flow-id thread-id]
  (let [rnode (indexes-api/callstack-tree-root-node flow-id thread-id)
        node-id (reference-value! rnode)]
    node-id))

(defn callstack-node-childs [node-ref]
  (let [node (get-reference-value node-ref)
        childs (indexes-api/callstack-node-childs node)
        childs-ids (mapv reference-value! childs)]
    childs-ids))

(defn callstack-node-frame [node-ref]
  (let [node (get-reference-value node-ref)
        frame-data (indexes-api/callstack-node-frame node)]
    (reference-frame-data! frame-data)))

(defn fn-call-stats [flow-id thread-id]
  (let [stats (indexes-api/fn-call-stats flow-id thread-id)]
    (->> stats
         (mapv (fn [fstats]
                 (update fstats :dispatch-val reference-value!))))))

(def find-fn-frames indexes-api/find-fn-frames)

(defn find-fn-frames-light [flow-id thread-id fn-ns fn-name form-id]
  (let [fn-frames (indexes-api/find-fn-frames flow-id thread-id fn-ns fn-name form-id)]
    (->> fn-frames
         (mapv (fn [fr]
                 (-> fr
                     (dissoc :bindings :expr-executions)
                     reference-frame-data!))))))

(defn search-next-frame-idx* [flow-id thread-id query-str from-idx params interrupt-ch on-progress]
  (async/go
    (some-> (async/<! (indexes-api/search-next-frame-idx flow-id thread-id query-str from-idx params interrupt-ch on-progress))
            (update :frame-data (fn [fd]
                                  (-> fd
                                      reference-frame-data!
                                      (dissoc :bindings :expr-executions)))))))

(defn search-next-frame-idx [& args]
  (submit-interruptible-task! search-next-frame-idx* args))

(def discard-flow indexes-api/discard-flow)

(def clear-values-references runtime-values/clear-values-references)

(def flow-threads-info indexes-api/flow-threads-info)

(defn ping [] :pong)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; To be used form the repl connections ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn publish-event! [kind ev config]
     ;; do it on a different thread since we
     ;; don't want to block for event publication
     (future
       (case kind
         :clj (rt-events/publish-event! ev)

         ;; sending a event for ClojureScript from the Clojure (shadow) side is kind of
         ;; hacky. We evaluate the publish-event! expresion in the cljs runtime.
         :cljs (hansel-inst-utils/eval-in-ns-fn-cljs 'cljs-user
                                                     `(flow-storm.runtime.events/publish-event! ~ev)
                                                     config)))
     nil))

#?(:clj
   (defn instrument-var [kind var-symb {:keys [disable-events?] :as config}]
     (let [inst-fn (case kind
                     :clj  hansel/instrument-var-clj
                     :cljs hansel/instrument-var-shadow-cljs)]
       
       (log (format "Instrumenting var %s %s" var-symb config))

       (inst-fn var-symb (merge (tracer/hansel-config config)
                                config))
       
       (when-not disable-events?
         (publish-event! kind (rt-events/make-var-instrumented-event (name var-symb) (namespace var-symb)) config)))))

#?(:clj
   (defn uninstrument-var [kind var-symb {:keys [disable-events?] :as config}]
     (let [inst-fn (case kind
                     :clj  hansel/uninstrument-var-clj
                     :cljs hansel/uninstrument-var-shadow-cljs)]

       (log (format "Uninstrumenting var %s %s" var-symb config))
       (inst-fn var-symb (merge (tracer/hansel-config config)
                                config))
       (when-not disable-events?
         (publish-event! kind (rt-events/make-var-uninstrumented-event (name var-symb) (namespace var-symb)) config)))))

#?(:clj
   (defn instrument-namespaces [kind ns-prefixes {:keys [disable-events?] :as config}]
     (log (format "Instrumenting namespaces %s" (pr-str ns-prefixes)))
     (let [inst-fn (case kind
                     :clj  hansel/instrument-namespaces-clj
                     :cljs hansel/instrument-namespaces-shadow-cljs)
           {:keys [affected-namespaces]} (inst-fn ns-prefixes (merge (tracer/hansel-config config)
                                                                     config))]
       
       (when-not disable-events?
         (doseq [ns-symb affected-namespaces]
           (publish-event! kind (rt-events/make-ns-instrumented-event (name ns-symb)) config))))))

#?(:clj
   (defn uninstrument-namespaces [kind ns-prefixes {:keys [disable-events?] :as config}]
     (log (format "Uninstrumenting namespaces %s" (pr-str ns-prefixes)))
     (let [inst-fn (case kind
                     :clj  hansel/uninstrument-namespaces-clj
                     :cljs hansel/uninstrument-namespaces-shadow-cljs)
           {:keys [affected-namespaces]} (inst-fn ns-prefixes (merge (tracer/hansel-config config)
                                                                     config))]
       
       (when-not disable-events?
         (doseq [ns-symb affected-namespaces]
           (publish-event! kind (rt-events/make-ns-uninstrumented-event (name ns-symb)) config))))))

#?(:clj
   (defn all-namespaces
     ([kind] (all-namespaces kind nil))
     ([kind build-id]
      (case kind
        :clj (mapv (comp str ns-name) (all-ns))
        :cljs (hansel-inst-utils/cljs-get-all-ns build-id)))))

#?(:clj
   (defn all-vars-for-namespace
     ([kind ns-name] (all-vars-for-namespace kind ns-name nil))
     ([kind ns-name build-id]
      (case kind
        :clj (->> (ns-interns (symbol ns-name)) keys (mapv str))
        :cljs (hansel-inst-utils/cljs-get-ns-interns (symbol ns-name) build-id)))))

#?(:clj
   (defn get-var-meta
     ([kind var-symb] (get-var-meta kind var-symb {}))
     ([kind var-symb {:keys [build-id]}]
      (case kind
        :clj (-> (meta (find-var var-symb)) (update :ns (comp str ns-name)))
        :cljs (hansel-inst-utils/eval-in-ns-fn-cljs 'cljs.user `(meta (var ~var-symb)) {:build-id build-id})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils for calling by name, used by the websocket api calls ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def api-fn {:val-pprint val-pprint
             :shallow-val shallow-val
             :get-form get-form
             :timeline-count timeline-count
             :timeline-entry timeline-entry
             :frame-data frame-data
             :bindings bindings
             :callstack-tree-root-node callstack-tree-root-node
             :callstack-node-childs callstack-node-childs
             :callstack-node-frame callstack-node-frame
             :fn-call-stats fn-call-stats
             :find-fn-frames-light find-fn-frames-light
             :search-next-frame-idx search-next-frame-idx
             :discard-flow discard-flow
             :def-value def-value
             :tap-value tap-value
             :interrupt-task interrupt-task
             :interrupt-all-tasks interrupt-all-tasks
             :clear-values-references clear-values-references
             :flow-threads-info flow-threads-info
             :ping ping
             #?@(:clj
                 [:all-namespaces all-namespaces
                  :all-vars-for-namespace all-vars-for-namespace
                  :get-var-meta get-var-meta
                  :instrument-var instrument-var
                  :uninstrument-var uninstrument-var
                  :instrument-namespaces instrument-namespaces
                  :uninstrument-namespaces uninstrument-namespaces])})

(defn call-by-name [fun-key args]  
  (let [f (get api-fn fun-key)]
    (apply f args)))
