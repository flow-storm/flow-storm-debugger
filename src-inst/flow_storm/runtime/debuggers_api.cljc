(ns flow-storm.runtime.debuggers-api
  (:require [flow-storm.runtime.indexes.api :as indexes-api]            
            [flow-storm.utils :as utils :refer [log]]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as runtime-values :refer [reference-value! get-reference-value]]
            [clojure.core.async :as async]
            #?@(:clj [[flow-storm.instrument.forms :as inst-forms]
                      [flow-storm.instrument.namespaces :as inst-ns]
                      [cljs.repl :as cljs-repl]
                      [cljs.analyzer :as ana]
                      [cljs.analyzer.api :as ana-api]
                      [clojure.java.io :as io]                      
                      [clojure.set :as set]
                      [clojure.tools.namespace.parse :as tools-ns-parse]
                      [clojure.tools.namespace.file :as tools-ns-file]
                      [clojure.tools.namespace.dependency :as tools-ns-deps]]))
  #?(:clj (:import [java.io StringReader])))

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

(defn get-form [flow-id thread-id form-id]
  (let [form (indexes-api/get-form flow-id thread-id form-id)]
    (update form :multimethod/dispatch-val reference-value!)))

(def all-threads indexes-api/all-threads)
(def all-forms indexes-api/all-forms)
(def timeline-count indexes-api/timeline-count)

(defn- reference-frame-data! [frame-data]
  (-> frame-data
      (update :dispatch-val reference-value!)
      (update :args-vec reference-value!)
      (update :ret reference-value!)
      (update :bindings (fn [bs]
                          (mapv #(update % :value reference-value!) bs)))
      (update :expr-executions (fn [ee]
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
  (let [rnode (indexes-api/callstack-tree-root-node flow-id thread-id)]
    (reference-value! rnode)))

(defn callstack-node-childs [node-ref]  
  (let [node (get-reference-value node-ref)
        childs (indexes-api/callstack-node-childs node)]
    (mapv reference-value! childs)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Used by clojure only ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn instrument-namespaces [ns-prefixes opts]                                    
     (let [inst-namespaces (inst-ns/instrument-files-for-namespaces ns-prefixes (assoc opts
                                                                                       :prefixes? true))]
       (doseq [ns-symb inst-namespaces]
         (rt-events/publish-event! (rt-events/make-ns-instrumented-event (str ns-symb)))))))

#?(:clj
   (defn uninstrument-namespaces [ns-prefixes]
     (let [uninst-namespaces (inst-ns/instrument-files-for-namespaces ns-prefixes {:prefixes? true
                                                                                   :uninstrument? true})]
       (doseq [ns-symb uninst-namespaces]
         (rt-events/publish-event! (rt-events/make-ns-uninstrumented-event (str ns-symb)))))))

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
             :interrupt-task interrupt-task
             :interrupt-all-tasks interrupt-all-tasks
             :clear-values-references clear-values-references
             #?@(:clj
                 [:instrument-namespaces instrument-namespaces
                  :uninstrument-namespaces uninstrument-namespaces])})

(defn call-by-name [fun-key args]
  (let [f (get api-fn fun-key)]
    (apply f args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; This are helpers for the ClojureScript repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defmacro instrument* [config form]
     (inst-forms/instrument (assoc config :env &env) form)))

#?(:clj
    (defmacro cljs-get-all-ns []
      (mapv str (ana-api/all-ns))))

#?(:clj
   (defmacro cljs-get-ns-interns [ns-symb]
     (->> (ana-api/ns-interns ns-symb)
          keys
          (mapv str))))

#?(:clj
   (defmacro cljs-source-fn [symb]
     (cljs-repl/source-fn &env symb)))

#?(:clj
   (defn- cljs-file-forms [ns-symb file-url]     
     (let [found-ns (ana-api/find-ns ns-symb)]
       (try
         (binding [ana/*cljs-ns* ns-symb]           
           (let [file-str (slurp file-url)
                 file-forms (ana-api/forms-seq (StringReader. file-str))]
            (->> file-forms
                 rest ;; discard the (ns ...) form
                 (mapv str))))
         (catch Exception e
           (println "Error reading forms for " ns-symb file-url "after finding ns" found-ns)
           (.printStackTrace e))))))

#?(:clj
   (defmacro cljs-sorted-namespaces-sources

     "Given `ns-symbs` will return :
      [[\"ns-name-1\" [\"form-1\" \"form-2\"]]
       [\"ns-name-2\" [\"form-1\" \"form-2\"]]]

  Used by tools from the repl to retrieve namespaces forms to instrument/uninstrument"
     
     [ns-symbs]
     (let [all-ns-info (->> ns-symbs
                            (map (fn [ns-symb]
                                   (let [file-name (-> (ana-api/find-ns ns-symb) :meta :file)
                                         file (try (io/resource file-name) (catch Exception _ nil))
                                         ns-decl-form (try (tools-ns-file/read-file-ns-decl file) (catch Exception _ nil))
                                         deps (try (tools-ns-parse/deps-from-ns-decl ns-decl-form) (catch Exception _ nil))]
                                     [ns-symb {:ns-symb ns-symb
                                               :file-name file-name
                                               :file file
                                               :ns-decl-form ns-decl-form
                                               :deps deps}])))
                            (into {}))
           ns-graph (reduce (fn [g {:keys [deps ns-symb]}]
                              (reduce (fn [gg dep-ns-symb]
                                        (tools-ns-deps/depend gg ns-symb dep-ns-symb))
                                      g
                                      deps))
                            (tools-ns-deps/graph)
                            (vals all-ns-info))
           dependent-files-vec (->> (tools-ns-deps/topo-sort ns-graph)
                                    (keep (fn [ns-symb]
                                            (when-let [file (get-in all-ns-info [ns-symb :file])]
                                              [ns-symb file])))
                                    (into []))
           all-files-set (into #{} (map (fn [[ns-name {:keys [file]}]] [ns-name file]) all-ns-info))
           independent-files (set/difference all-files-set
                                             (into #{} dependent-files-vec))

           
           to-instrument-vec (into dependent-files-vec independent-files)]
       
       (mapv (fn [[ns-symb ns-file]]
               [(str ns-symb) (cljs-file-forms ns-symb ns-file)])
             to-instrument-vec))))

