(ns flow-storm.api
  "This is the only namespace intended for users.
  Provides functionality to connect to the debugger and instrument forms."
  (:require [flow-storm.tracer :as tracer]
            [flow-storm.runtime.taps :as rt-taps]
            [flow-storm.utils :refer [log] :as utils]
            [hansel.api :as hansel]
            [hansel.instrument.runtime :refer [*runtime-ctx*]]
            [hansel.instrument.utils :as inst-utils]
            [flow-storm.runtime.debuggers-api :as dbg-api]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.mem-reporter :as mem-reporter]
            [flow-storm.json-serializer :as serializer]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.indexes.api :as index-api]
            [flow-storm.fn-sampler.core :as fn-sampler]
            [clojure.string :as str]
            [clojure.stacktrace :as stacktrace]))

;; TODO: build script
;; Maybe we can figure out this ns names by scanning (all-ns) so
;; we don't need to list them here
;; Also maybe just finding main is enough, we can add to it a fn
;; that returns the rest of the functions we need
(def debugger-main-ns 'flow-storm.debugger.main)

(def api-loaded?
  "Used for remote connections to check this ns has been loaded"
  true)

(defn stop
  ([] (stop {}))
  ([{:keys [skip-index-stop?]}]
   (let [stop-debugger (try
                         (resolve (symbol (name debugger-main-ns) "stop-debugger"))
                         (catch Exception _ nil))]

     ;; NOTE: The order here is important until we replace this code with
     ;; better component state management

     (when-not skip-index-stop?
       (index-api/stop))

     ;; if we are running in local mode and running a debugger stop it
     (when stop-debugger
       (stop-debugger))


     (rt-events/clear-subscription!)
     (rt-events/clear-pending-events!)

     (rt-taps/remove-tap!)

     (dbg-api/interrupt-all-tasks)

     (rt-values/clear-vals-ref-registry)

     (mem-reporter/stop-mem-reporter)

     ;; stop remote websocket client if needed
     (remote-websocket-client/stop-remote-websocket-client)

     (tracer/clear-breakpoints!)

     (log "System stopped"))))

(defn setup-runtime

  "Setup runtime based on jvm properties. Returns a config map."

  []
  (let [recording-prop (System/getProperty "flowstorm.startRecording")
        old-recording-prop (System/getProperty "clojure.storm.traceEnable")
        theme-prop (System/getProperty "flowstorm.theme")
        styles-prop (System/getProperty "flowstorm.styles")
        config (cond-> {}
                 theme-prop  (assoc :theme (keyword theme-prop))
                 styles-prop (assoc :styles styles-prop))]

    (when-let [tep (or recording-prop old-recording-prop)]
      (when old-recording-prop (log "WARNING: clojure.storm.traceEnable is deprecated, use flowstorm.startRecording instead !"))
      (tracer/set-recording (Boolean/parseBoolean tep)))

    config))

(defn- start-runtime [{:keys [skip-index-start? skip-debugger-start? events-dispatch-fn] :as config}]
  (let [config (merge config (setup-runtime))]

    ;; NOTE: The order here is important until we replace this code with
    ;; better component state management

    (when-not skip-index-start?
      (index-api/start))

    ;; start the debugger UI
    (when-not skip-debugger-start?
      (let [start-debugger (requiring-resolve (symbol (name debugger-main-ns) "start-debugger"))]
        (start-debugger config)))

    (rt-events/subscribe! events-dispatch-fn)

    (rt-values/clear-vals-ref-registry)

    (rt-taps/setup-tap!)

    (mem-reporter/run-mem-reporter)))

(defn local-connect

  "Start a debugger under this same JVM process and connect to it.

  This is the recommended way of using the debugger for debugging code that
  generates a lot of data since data doesn't need to serialize/deserialize it like
  in a remote debugging session case.

  `config` should be a map containing :

       - `:verbose?` to log more stuff for debugging the debugger
       - `:theme` can be one of `:light`, `:dark` or `:auto`
       - `:styles` a string path to a css file if you want to override some started debugger styles

  Use `flow-storm.api/stop` to shutdown the system nicely."

  ([] (local-connect {}))

  ([config]

   (let [enqueue-event! (requiring-resolve 'flow-storm.debugger.events-queue/enqueue-event!)
         config (assoc config
                       :local? true
                       :events-dispatch-fn enqueue-event!)]

     (start-runtime config))))

(defn remote-connect [config]

  ;; connect to the remote websocket
  (remote-websocket-client/start-remote-websocket-client
   (assoc config
          :api-call-fn dbg-api/call-by-name
          :on-connected (fn []
                          (let [config (assoc config
                                              :skip-debugger-start? true
                                              :events-dispatch-fn (fn [ev]
                                                                    (-> [:event ev]
                                                                        serializer/serialize
                                                                        remote-websocket-client/send)))]
                            (log "Connected to remote websocket")

                            (start-runtime config)

                            (log "Remote Clojure runtime initialized"))))))

(def jump-to-last-expression dbg-api/jump-to-last-expression-in-this-thread)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Clojure instrumentation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn instrument-var-clj

  "Instruments any var.

    Lets say you are interested in debugging clojure.core/interpose you can do :

  (instrument-var-clj clojure.core/interpose)

  Then you can call :

  (interpose :a [1 2 3])

  and it will show up in the debugger.

  Be careful instrumenting clojure.core functions or any functions that are being used
  by repl system code since can be called constantly and generate a lot of noise.

  Use `uninstrument-var-clj` to remove instrumentation.

  `opts` is a map that support :flow-id and :disable
  See `instrument-namespaces-clj` for :disable"

  ([var-symb] (instrument-var-clj var-symb {}))
  ([var-symb config]

   (dbg-api/instrument-var :clj var-symb config)))

(defn uninstrument-var-clj

  "Remove instrumentation given a var symbol.

  (uninstrument-var-clj var-symb)"

  [var-symb]

  (dbg-api/uninstrument-var :clj var-symb {}))

(defn instrument-namespaces-clj

  "Instrument all forms, in all namespaces that matches `prefixes`.

    `prefixes` is a set of ns prefixes like #{\"cljs.compiler\" \"cljs.analyzer\"}

  `opts` is a map containing :
       - :excluding-ns  a set of strings with namespaces that should be excluded
       - :disable a set containing any of #{:expr :binding :anonymous-fn}
                  useful for disabling unnecesary traces in code that generate too many traces
       - :verbose? when true show more logging
  "

  ([prefixes] (instrument-namespaces-clj prefixes {}))
  ([prefixes opts] (dbg-api/instrument-namespaces :clj prefixes opts)))

(defn uninstrument-namespaces-clj

  "Undo instrumentation made by `flow-storm.api/instrument-namespaces-clj`"

  ([prefixes] (uninstrument-namespaces-clj prefixes {}))
  ([prefixes opts]
   (dbg-api/uninstrument-namespaces :clj prefixes opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ClojureScript instrumentation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn instrument-var-cljs

  "Like `flow-storm.api/instrument-var-clj` but for using it from the shadow Clojure repl.

  Arguments are the same as the Clojure version but `config` also accepts a `:build-id`"

  ([var-symb] (instrument-var-clj var-symb {}))
  ([var-symb config]

   (dbg-api/instrument-var :cljs var-symb config)))

(defn uninstrument-var-cljs

  "Like `flow-storm.api/uninstrument-var-clj` but for using it from the shadow Clojure repl.

  Arguments are the same as the Clojure version but `config` needs a `:build-id`"

  [var-symb config]

  (dbg-api/uninstrument-var :cljs var-symb config))

(defn instrument-namespaces-cljs

  "Like `flow-storm.api/instrument-namespaces-clj` but for using it from the shadow Clojure repl.

  Arguments are the same as the Clojure version but `config` also accepts a `:build-id`"

  ([prefixes] (instrument-namespaces-clj prefixes {}))
  ([prefixes opts] (dbg-api/instrument-namespaces :cljs prefixes opts)))

(defn uninstrument-namespaces-cljs

  "Like `flow-storm.api/uninstrument-namespaces-clj` but for using it from the shadow Clojure repl.
  Arguments are the same as the Clojure version but `config` also accepts a `:build-id`"

  [prefixes config]
  (dbg-api/uninstrument-namespaces :cljs prefixes config))

(defn- runi* [{:keys [ns flow-id thread-trace-limit tracing-disabled? env] :as opts} form]
  ;; ~'flowstorm-runi is so it doesn't expand into flow-storm.api/flowstorm-runi which
  ;; doesn't work with fn* in clojurescript
  (let [wrapped-form `(fn* ~'flowstorm-runi ([] ~form))
        ns (or ns (when-let [env-ns (-> env :ns :name)]
                    (str env-ns)))
        hansel-config (tracer/hansel-config opts)
        {:keys [inst-form init-forms]} (hansel/instrument-form hansel-config wrapped-form)]
    `(let [flow-id# ~(or flow-id (-> form meta :flow-id) 0)
           curr-ns# ~(or ns `(when *ns* (str (ns-name *ns*))) (-> env :ns :name str))]
       (binding [*runtime-ctx* {:flow-id flow-id#
                                :thread-trace-limit ~thread-trace-limit
                                :tracing-disabled? ~tracing-disabled?}]
         (tracer/trace-flow-init-trace {:flow-id flow-id# :form-ns curr-ns# :form (quote (runi ~opts ~form))})

         ~@init-forms

         (~inst-form)))))

(defmacro runi

  "Run instrumented.

  (runi opts form)

  Instrument form and run it for tracing.

  Same as doing #rtrace `form`.

  `opts` is a map that support the same keys as `instrument-var`. "

  [opts form]

  (runi* (assoc opts :env &env) form))

(defn cli-run

  "Require `fn-symb` ns, instrument `ns-set` (excluding `excluding-ns`) and then call (apply `fn-symb` `fn-args`).

  `profile` (optional) should be :full (for full instrumentation) or :light for disable #{:expr :binding}

  `require-before` (optional) should be a set of namespaces you want to require before the instrumentation.

  `verbose?` (optional) when true show more logging.

  `styles` (optional) a file path containing styles (css) that will override default styles

  cli-run is designed to be used with clj -X like :

  clj -X:dbg:inst:dev:build flow-storm.api/cli-run :instrument-set '#{\"hf.depstar\"}' :fn-symb 'hf.depstar/jar' :fn-args '[{:jar \"flow-storm-dbg.jar\" :aliases [:dbg] :paths-only false :sync-pom true :version \"1.1.1\" :group-id \"com.github.jpmonettas\" :artifact-id \"flow-storm-dbg\"}]'

  if you want to package flow-storm-dbg with depstar traced.
  "

  [{:keys [instrument-ns excluding-ns require-before fn-symb fn-args profile verbose? styles theme flow-id excluding-fns] :as opts}]
  (let [valid-opts-keys #{:instrument-ns :excluding-ns :require-before :fn-symb :fn-args :profile :verbose? :styles :theme :debugger-host :port :flow-id :excluding-fns}]

    (assert (utils/contains-only? opts valid-opts-keys) (format "Invalid option key. Valid options are %s" valid-opts-keys))
    (assert (or (nil? instrument-ns) (set? instrument-ns)) "instrument-ns should be a set of namespaces prefixes")
    (assert (or (nil? excluding-ns) (set? excluding-ns)) "excluding-ns should be a set of namepsaces as string")
    (assert (or (nil? require-before) (set? require-before)) "require-before should be a set of namespaces as string")
    (assert (and (symbol? fn-symb) (namespace fn-symb)) "fn-symb should be a fully qualify symbol")
    (assert (vector? fn-args) "fn-args should be a vector")
    (assert (or (nil? profile) (#{:full :light} profile)) "profile should be :full or :light")

    (let [inst-opts {:disable (utils/disable-from-profile profile)
                     :excluding-ns excluding-ns
                     :excluding-fns excluding-fns
                     :verbose? verbose?}
          fn-ns-name (namespace fn-symb)
          _ (require (symbol fn-ns-name))
          _ (resolve fn-symb)]

      (when (seq require-before)
        (doseq [ns-name require-before]
          (log (format "Requiring ns %s" ns-name))
          (require (symbol ns-name))))

      (local-connect {:verbose? verbose? :styles styles :theme theme})

      (log (format "Instrumenting namespaces %s" instrument-ns))
      (time
       (instrument-namespaces-clj instrument-ns inst-opts))
      (log "Instrumentation done.")
      (time
       (if flow-id
         (eval (runi* {:flow-id flow-id} `(~fn-symb ~@fn-args)))
         (eval `(~fn-symb ~@fn-args))))
      (log "Execution done, processing traces...")
      (log "DONE"))))

(defn cli-doc

  "Document a code base by instrumenting, running and sampling it.

  - :instrument-ns Set of namespaces prefixes to instrument for documentation
  - :result-name A name for the output jar file
  - :fn-symb Fully qualified symbol of the fn to run for exercising the code base
  - :fn-args Arguments to be passed to the function defined by `:fn-symb`

  - :excluding-fns (optional) A set of fns as symbols to be excluded from instrumentation.
  - :excluding-ns (optional) A set of namespaces as string to be excluded from instrumentation.
  - :require-before (optional) A set of namespaces as a string. Useful when you need to load extra namespaces before instrumentation.
  - :verbose? (optional) Print extra log info.
  - :print-unsampled? (optional) After finishing, prints all uncovered functions (functions that where instrumented but weren't sampled)
  - :examples-pprint? (optional) Pretty print the values in the examples
  - :examples-print-length (optional) Print length for the examples values
  - :examples-print-level (optional) Print level for the examples values
  "

  [{:keys [instrument-ns excluding-ns require-before fn-symb fn-args verbose? excluding-fns
           result-name print-unsampled? examples-pprint? examples-print-length examples-print-level] :as opts}]

  (let [valid-opts-keys #{:instrument-ns :excluding-ns :require-before :fn-symb :fn-args
                          :verbose? :excluding-fns :result-name :print-unsampled?
                          :examples-pprint? :examples-print-length :examples-print-level}]

    (assert (utils/contains-only? opts valid-opts-keys) (format "Invalid option key. Valid options are %s" valid-opts-keys))
    (assert (or (nil? instrument-ns) (set? instrument-ns)) "instrument-ns should be a set of namespaces prefixes")
    (assert (or (nil? excluding-ns) (set? excluding-ns)) "excluding-ns should be a set of namepsaces as string")
    (assert (or (nil? require-before) (set? require-before)) "require-before should be a set of namespaces as string")
    (assert (and (symbol? fn-symb) (namespace fn-symb)) "fn-symb should be a fully qualify symbol")
    (assert (vector? fn-args) "fn-args should be a vector")

    (let [fn-ns-name (namespace fn-symb)
          _ (require (symbol fn-ns-name))
          _ (resolve fn-symb)]

      (when (seq require-before)
        (doseq [ns-name require-before]
          (log (format "Requiring ns %s" ns-name))
          (require (symbol ns-name))))

      (eval
       `(fn-sampler/sample
         ~{:result-name result-name
           :inst-ns-prefixes instrument-ns
           :excluding-fns excluding-fns
           :excluding-ns excluding-ns
           :verbose? verbose?
           :print-unsampled? print-unsampled?
           :uninstrument? false
           :examples-pprint? examples-pprint?
           :examples-print-length examples-print-length
           :examples-print-level examples-print-level}

         (~fn-symb ~@fn-args))))))

(defmacro instrument* [config form]
  (let [env &env
        compiler (inst-utils/compiler-from-env env)
        ;; full-instr-form contains (do (trace-init-form ...) instr-form)
        {:keys [inst-form init-forms]} (hansel/instrument-form (merge config
                                                                      (tracer/hansel-config config)
                                                                      {:env env
                                                                       :form-file *file*
                                                                       :form-line (:line (meta form))})
                                                               form)]

    (if (and (= compiler :clj)
             (inst-utils/expanded-defn-form? inst-form))

      ;; if we are in clojure and it is a (defn ...) or (def . (fn []))
      ;; add a watch to its var to track when it is being instrumented/uninstrumented
      (let [var-symb (second form)]
        `(do

           ~@init-forms
           ~inst-form

           (let [v# (var ~var-symb)
                 [vns# vname#] ((juxt namespace name) (symbol v#))]
             (rt-events/publish-event! (rt-events/make-var-instrumented-event vname# vns#))
             (add-watch v#
                        :flow-storm/var-redef
                        (fn [a1# a2# fn-before# fn-after#]
                          (cond

                            (and (:hansel/instrumented? (meta fn-before#))
                                 (not (:hansel/instrumented? (meta fn-after#))))
                            (rt-events/publish-event! (rt-events/make-var-uninstrumented-event vname# vns#))

                            (and (not (:hansel/instrumented? (meta fn-before#)))
                                 (:hansel/instrumented? (meta fn-after#)))
                            (rt-events/publish-event! (rt-events/make-var-instrumented-event vname# vns#))))))))


      `(do
         ~@init-forms
         ~inst-form))))

(defn show-doc [var-symb]
  (rt-events/publish-event! (rt-events/show-doc-event var-symb)))

(defn read-trace-tag [form]
  `(instrument* {} ~form))

(defn read-ctrace-tag [form]
  `(instrument* {:tracing-disabled? true} ~form))

(defn- read-rtrace-tag* [config form]
  (let [{:keys [flow-id] :as full-config} (merge config (meta form))]
    `(if (utils/storm-env?)
       (throw (ex-info "#rtrace and #trace can't be used with ClojureStorm, they aren't needed. All your configured compilations will be automatically instrumented. Please re-run the expression without it. Evaluation skipped." {}))

       (let [res# (runi ~full-config ~form)]
         (dbg-api/jump-to-last-expression-in-this-thread ~flow-id)
         res#))))

(defn read-rtrace-tag [form]  (read-rtrace-tag* {:flow-id 0} form))
(defn read-rtrace0-tag [form] (read-rtrace-tag* {:flow-id 0} form))
(defn read-rtrace1-tag [form] (read-rtrace-tag* {:flow-id 1} form))
(defn read-rtrace2-tag [form] (read-rtrace-tag* {:flow-id 2} form))
(defn read-rtrace3-tag [form] (read-rtrace-tag* {:flow-id 3} form))
(defn read-rtrace4-tag [form] (read-rtrace-tag* {:flow-id 4} form))
(defn read-rtrace5-tag [form] (read-rtrace-tag* {:flow-id 5} form))

(defn read-tap-tag [form]
  `(let [form-val# ~form]
     (tap> form-val#)
     form-val# ))

(defn current-stack-trace []
  (try (throw (Exception. "Dummy"))
       (catch Exception e (->> (with-out-str (stacktrace/print-stack-trace e))
                               (str/split-lines)
                               seq
                               (drop 3)))))

(defn read-tap-stack-trace-tag [form]
  `(do (tap> (flow-storm.api/current-stack-trace))
       ~form))

(defn break-at
  ([fq-fn-symb] (dbg-api/add-breakpoint! fq-fn-symb))
  ([fq-fn-symb args-pred] (dbg-api/add-breakpoint! fq-fn-symb args-pred)))

(defn remove-break [fq-fn-symb]
  (dbg-api/remove-breakpoint! fq-fn-symb))

(def unblock-thread dbg-api/unblock-thread)
(def clear-breaks dbg-api/clear-breakpoints!)

(defn start-recording [] (dbg-api/set-recording true))
(defn stop-recording [] (dbg-api/set-recording false))
