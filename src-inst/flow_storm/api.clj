(ns flow-storm.api
  "This is the only namespace intended for users.
  Provides functionality to connect to the debugger and instrument forms."
  (:require [flow-storm.tracer :as tracer]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.utils :refer [log log-error] :as utils]
            [flow-storm.instrument.namespaces :as inst-ns]
            [flow-storm.instrument.forms :as inst-forms]
            [flow-storm.core :as fs-core]
            [flow-storm.json-serializer :as serializer]
            [flow-storm.remote-websocket-client :as remote-websocket-client]))

;; TODO: build script
;; Maybe we can figure out this ns names by scanning (all-ns) so
;; we don't need to list them here
;; Also maybe just finding main is enough, we can add to it a fn
;; that returns the rest of the functions we need
(def debugger-events-processor-ns 'flow-storm.debugger.events-processor)
(def debugger-main-ns 'flow-storm.debugger.main)
(def debugger-trace-processor-ns 'flow-storm.debugger.trace-processor)

(defn stop []
  (let [stop-debugger (try
                        (resolve (symbol (name debugger-main-ns) "stop-debugger"))
                        (catch Exception _ nil))]

    ;; if we are running in local mode and running a debugger stop it
    (when stop-debugger
      (stop-debugger))

    ;; always stop the tracer
    (tracer/stop-tracer)

    ;; stop remote websocket client if needed
    (remote-websocket-client/stop-remote-websocket-client)))

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
   (require debugger-trace-processor-ns)
   (require debugger-main-ns)
   (let [config (assoc config :local? true)
         dispatch-trace (resolve (symbol (name debugger-trace-processor-ns) "dispatch-trace"))
         start-debugger (resolve (symbol (name debugger-main-ns) "start-debugger"))]

     ;; start the debugger UI
     (start-debugger config)

     ;; start the tracer
     (tracer/start-tracer
      (assoc config
             :send-fn (fn local-send [trace]
                        (try
                          (dispatch-trace (rt-values/wrap-trace-values! trace false))
                          (catch Exception e
                            (log-error "Exception dispatching trace " e)))))))))

(defn remote-connect

  "Connect to a remote debugger.
  Without arguments connects to localhost:7722.

  `config` is a map with `:host`, `:port`

  Use `flow-storm.api/stop` to shutdown the system nicely."

  ([] (remote-connect {:host "localhost" :port 7722}))

  ([config]

   ;; connect to the remote websocket
   (remote-websocket-client/start-remote-websocket-client
    (assoc config :run-command fs-core/run-command))

   ;; start the tracer
   (tracer/start-tracer
    (assoc config
           :send-fn (fn local-send [trace]
                      (try
                        (let [packet [:trace (rt-values/wrap-trace-values! trace true)]
                              ser (serializer/serialize packet)]
                          (remote-websocket-client/send ser))
                        (catch Exception e
                          (log-error "Exception dispatching trace " e))))))))

(defn send-event-to-debugger [ev]

  (let [packet [:event ev]]
    (if (remote-websocket-client/remote-connected?)

      ;; send the packet remotely
      (remote-websocket-client/send-event-to-debugger packet)

      ;; "send" locally (just call a function)
      (let [local-process-event (resolve (symbol (name debugger-events-processor-ns) "process-event"))]
        (local-process-event ev)))))

(defn instrument-var

  "Instruments any var.

  (instrument-var var-symb opts)

  Lets say you are interested in debugging clojure.core/interpose you can do :

  (instrument-var clojure.core/interpose {})

  #rtrace (interpose :a [1 2 3])

  Be careful instrumenting clojure.core functions or any functions that are being used
  by repl system code since can be called constantly and generate a lot of noise.

  Use `uninstrument-var` to remove instrumentation.

  `opts` is a map that support :flow-id and :disable
  See `instrument-forms-for-namespaces` for :disable"
  ([var-symb] (instrument-var var-symb {}))
  ([var-symb config]

   (fs-core/instrument-var var-symb config)

   (send-event-to-debugger [:var-instrumented {:var-name (name var-symb)
                                               :var-ns (namespace var-symb)}])))

(defn uninstrument-var

  "Remove instrumentation given a var symbol.

  (uninstrument-var var-symb)"

  [var-symb]

  (fs-core/uninstrument-var var-symb)

  (send-event-to-debugger [:var-uninstrumented {:var-name (name var-symb)
                                                :var-ns (namespace var-symb)}]))

(defn- runi* [{:keys [ns flow-id tracing-disabled?] :as opts} form]
  ;; ~'flowstorm-runi is so it doesn't expand into flow-storm.api/flowstorm-runi which
  ;; doesn't work with fn* in clojurescript
  (let [wrapped-form `(fn* ~'flowstorm-runi ([] ~form))]
    `(let [flow-id# ~(or flow-id (-> form meta :flow-id) 0)
           curr-ns# ~(or ns `(when *ns* (str (ns-name *ns*))))

           ]
       (binding [tracer/*runtime-ctx* (tracer/build-runtime-ctx {:flow-id flow-id#
                                                                 :tracing-disabled? ~tracing-disabled?})]
         (tracer/trace-flow-init-trace flow-id# curr-ns# (quote (runi ~opts ~form)))

         (~(inst-forms/instrument opts wrapped-form))))))

(defmacro runi

  "Run instrumented.

  (runi opts form)

  Instrument form and run it for tracing.

  Same as doing #rtrace `form`.

  `opts` is a map that support the same keys as `instrument-var`. "

  [opts form]

  (runi* (assoc opts :env &env) form))

(defn instrument-forms-for-namespaces

  "Instrument all forms, in all namespaces that matches `prefixes`.

  (instrument-forms-for-namespaces prefixes opts)

  `prefixes` is a set of ns prefixes like #{\"cljs.compiler\" \"cljs.analyzer\"}

  `opts` is a map containing :
       - :excluding-ns  a set of strings with namespaces that should be excluded
       - :disable a set containing any of #{:expr :binding :anonymous-fn}
                  useful for disabling unnecesary traces in code that generate too many traces
       - :verbose? when true show more logging
  "

  ([prefixes] (instrument-forms-for-namespaces prefixes {}))
  ([prefixes opts]

   (let [inst-namespaces (inst-ns/instrument-files-for-namespaces prefixes (assoc opts
                                                                                  :prefixes? true))]
     (doseq [ns-symb inst-namespaces]
       (send-event-to-debugger [:namespace-instrumented {:ns-name (str ns-symb)}])))))

(defn uninstrument-forms-for-namespaces

  "Undo instrumentation made by `flow-storm.api/instrument-forms-for-namespaces`"

  [prefixes]

  (let [uninst-namespaces (inst-ns/instrument-files-for-namespaces prefixes {:prefixes? true
                                                                             :uninstrument? true})]
    (doseq [ns-symb uninst-namespaces]
      (send-event-to-debugger [:namespace-uninstrumented {:ns-name (str ns-symb)}]))))


(defn cli-run

  "Require `fn-symb` ns, instrument `ns-set` (excluding `excluding-ns`) and then call (apply `fn-symb` `fn-args`).

  `profile` (optional) should be :full (for full instrumentation) or :light for disable #{:expr :binding :anonymous-fn}

  `require-before` (optional) should be a set of namespaces you want to require before the instrumentation.

  `verbose?` (optional) when true show more logging.

  `styles` (optional) a file path containing styles (css) that will override default styles

  `host` (optional) when a host is given traces are going to be send to a remote debugger

  `port` (optional) when a port is given traces are going to be send to a remote debugger

  cli-run is designed to be used with clj -X like :

  clj -X:dbg:inst:dev:build flow-storm.api/cli-run :instrument-set '#{\"hf.depstar\"}' :fn-symb 'hf.depstar/jar' :fn-args '[{:jar \"flow-storm-dbg.jar\" :aliases [:dbg] :paths-only false :sync-pom true :version \"1.1.1\" :group-id \"com.github.jpmonettas\" :artifact-id \"flow-storm-dbg\"}]'

  if you want to package flow-storm-dbg with depstar traced.
  "

  [{:keys [instrument-ns excluding-ns require-before fn-symb fn-args profile verbose? styles theme
           host port] :as opts}]
  (let [valid-opts-keys #{:instrument-ns :excluding-ns :require-before :fn-symb :fn-args :profile :verbose? :styles :theme :host :port}]

    (assert (utils/contains-only? opts valid-opts-keys) (format "Invalid option key. Valid options are %s" valid-opts-keys))
    (assert (or (nil? instrument-ns) (set? instrument-ns)) "instrument-ns should be a set of namespaces prefixes")
    (assert (or (nil? excluding-ns) (set? excluding-ns)) "excluding-ns should be a set of namepsaces as string")
    (assert (or (nil? require-before) (set? require-before)) "require-before should be a set of namespaces as string")
    (assert (and (symbol? fn-symb) (namespace fn-symb)) "fn-symb should be a fully qualify symbol")
    (assert (vector? fn-args) "fn-args should be a vector")
    (assert (or (nil? profile) (#{:full :light} profile)) "profile should be :full or :light")

    (let [inst-opts (-> (fs-core/disable-from-profile profile)
                        (assoc :excluding-ns excluding-ns
                               :verbose? verbose?))
          fn-ns-name (namespace fn-symb)
          _ (require (symbol fn-ns-name))
          _ (resolve fn-symb)
          ns-to-instrument (into #{fn-ns-name} instrument-ns)]

      (when (seq require-before)
        (doseq [ns-name require-before]
          (log (format "Requiring ns %s" ns-name))
          (require (symbol ns-name))))

      (if (or host port)
        (remote-connect {:host host :port port :verbose? verbose? :styles styles :theme theme})
        (local-connect {:verbose? verbose? :styles styles :theme theme}))
      (log (format "Instrumenting namespaces %s" ns-to-instrument))
      (instrument-forms-for-namespaces ns-to-instrument inst-opts)
      (log "Instrumentation done.")
      (eval (runi* {}
                   `(~fn-symb ~@fn-args))))))

(defmacro instrument* [config form]
  (inst-forms/instrument (assoc config :env &env) form))

(defn read-trace-tag [form]
  `(instrument* {} ~form))

(defn read-ctrace-tag [form]
  `(instrument* {:tracing-disabled? true} ~form))

(defn read-rtrace-tag [form]  `(runi {:flow-id 0} ~form))
(defn read-rtrace0-tag [form] `(runi {:flow-id 0} ~form))
(defn read-rtrace1-tag [form] `(runi {:flow-id 1} ~form))
(defn read-rtrace2-tag [form] `(runi {:flow-id 2} ~form))
(defn read-rtrace3-tag [form] `(runi {:flow-id 3} ~form))
(defn read-rtrace4-tag [form] `(runi {:flow-id 4} ~form))
(defn read-rtrace5-tag [form] `(runi {:flow-id 5} ~form))
