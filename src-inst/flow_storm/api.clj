(ns flow-storm.api
  "This is the only namespace intended for users.
  Provides functionality to connect to the debugger and instrument forms."
  (:require [flow-storm.tracer :as tracer]
            [flow-storm.runtime.taps :as rt-taps]
            [flow-storm.utils :refer [log] :as utils]
            [flow-storm.instrument.forms :as inst-forms]
            [flow-storm.instrument.namespaces :as inst-ns]
            [flow-storm.instrument.runtime :as inst-rt]
            [flow-storm.runtime.debuggers-api :as dbg-api]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]
            [flow-storm.json-serializer :as serializer]
            [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.indexes.api :as indexes-api]
            [clojure.repl :as clj.repl]))

;; TODO: build script
;; Maybe we can figure out this ns names by scanning (all-ns) so
;; we don't need to list them here
;; Also maybe just finding main is enough, we can add to it a fn
;; that returns the rest of the functions we need
(def debugger-main-ns 'flow-storm.debugger.main)

(def api-loaded?
  "Used for remote connections to check this ns has been loaded"
  true)

(defn stop []
  (let [stop-debugger (try
                        (resolve (symbol (name debugger-main-ns) "stop-debugger"))
                        (catch Exception _ nil))]

    ;; if we are running in local mode and running a debugger stop it
    (when stop-debugger
      (stop-debugger))


    (rt-events/clear-subscription!)
    (rt-events/clear-pending-events!)

    (indexes-api/stop)

    (rt-taps/remove-tap!)

    (dbg-api/interrupt-all-tasks)

    (rt-values/clear-values-references)

    ;; stop remote websocket client if needed
    (remote-websocket-client/stop-remote-websocket-client)

    (log "System stopped")))

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
   (require debugger-main-ns)
   (let [config (assoc config :local? true)
         start-debugger (resolve (symbol (name debugger-main-ns) "start-debugger"))]

     ;; start the debugger UI
     (start-debugger config)


     (rt-events/subscribe! (requiring-resolve 'flow-storm.debugger.events-queue/enqueue-event!))

     (rt-values/clear-values-references)

     (rt-taps/setup-tap!))))

(defn remote-connect [config]

  ;; connect to the remote websocket
  (remote-websocket-client/start-remote-websocket-client
   (assoc config :api-call-fn dbg-api/call-by-name))

  ;; push all events thru the websocket
  (rt-events/subscribe! (fn [ev]
                          (-> [:event ev]
                              serializer/serialize
                              remote-websocket-client/send)))

  (rt-values/clear-values-references)

  ;; setup the tap system so we send tap> to the debugger
  (rt-taps/setup-tap!)
  (println "Remote Clojure runtime initialized"))

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

   (let [form (some->> (clj.repl/source-fn var-symb)
                       (read-string {:read-cond :allow}))
         form-ns (find-ns (symbol (namespace var-symb)))]
     (if form

       (binding [*ns* form-ns]
         (inst-ns/instrument-and-eval-form form-ns form config)
         (log (format "Instrumented %s" var-symb))
         (rt-events/publish-event! (rt-events/make-var-instrumented-event (name var-symb)
                                                                          (namespace var-symb))))

       (log (format "Couldn't find source for %s" var-symb))))))

(defn uninstrument-var

  "Remove instrumentation given a var symbol.

  (uninstrument-var var-symb)"

  [var-symb]
  (let [ns-name (namespace var-symb)]
    (binding [*ns* (find-ns (symbol ns-name))]
      (let [form (some->> (clj.repl/source-fn var-symb)
                          (read-string {:read-cond :allow}))
            expanded-form (inst-forms/macroexpand-all macroexpand-1
                                                      (fn [symb] (symbol (resolve symb)))
                                                      form
                                                      ::original-form)]
        (if form

          (if (inst-forms/expanded-def-form? expanded-form)
            (let [[v vval] (inst-ns/expanded-defn-parse ns-name expanded-form)]
              (alter-var-root v (fn [_] (eval vval)))
              (log (format "Uninstrumented %s" v))
              (rt-events/publish-event! (rt-events/make-var-uninstrumented-event (name var-symb)
                                                                                 (namespace var-symb))))

            (log (format "Don't know howto untrace %s" (pr-str expanded-form))))

          (log (format "Couldn't find source for %s" var-symb)))))))

(defn- runi* [{:keys [ns flow-id tracing-disabled? env] :as opts} form]
  ;; ~'flowstorm-runi is so it doesn't expand into flow-storm.api/flowstorm-runi which
  ;; doesn't work with fn* in clojurescript
  (let [wrapped-form `(fn* ~'flowstorm-runi ([] ~form))
        ns (or ns (when-let [env-ns (-> env :ns :name)]
                    (str env-ns)))]
    `(let [flow-id# ~(or flow-id (-> form meta :flow-id) 0)
           curr-ns# ~(or ns `(when *ns* (str (ns-name *ns*))) (-> env :ns :name str))]
       (binding [inst-rt/*runtime-ctx* {:flow-id flow-id#
                                        ::tracing-disabled? ~tracing-disabled?}]
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
  ([prefixes opts] (dbg-api/instrument-namespaces prefixes opts true)))

(defn uninstrument-forms-for-namespaces

  "Undo instrumentation made by `flow-storm.api/instrument-forms-for-namespaces`"

  [prefixes]
  (dbg-api/uninstrument-namespaces prefixes true))


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
          _ (resolve fn-symb)
          ns-to-instrument (into #{fn-ns-name} instrument-ns)]

      (when (seq require-before)
        (doseq [ns-name require-before]
          (log (format "Requiring ns %s" ns-name))
          (require (symbol ns-name))))

      (local-connect {:verbose? verbose? :styles styles :theme theme})

      (log (format "Instrumenting namespaces %s" ns-to-instrument))
      (instrument-forms-for-namespaces ns-to-instrument inst-opts)
      (log "Instrumentation done.")
      (if flow-id
        (eval (runi* {:flow-id flow-id} `(~fn-symb ~@fn-args)))
        (eval `(~fn-symb ~@fn-args))))))

(defn read-trace-tag [form]
  `(dbg-api/instrument* {} ~form))

(defn read-ctrace-tag [form]
  `(dbg-api/instrument* {:tracing-disabled? true} ~form))

(defn read-rtrace-tag [form]  `(runi {:flow-id 0} ~form))
(defn read-rtrace0-tag [form] `(runi {:flow-id 0} ~form))
(defn read-rtrace1-tag [form] `(runi {:flow-id 1} ~form))
(defn read-rtrace2-tag [form] `(runi {:flow-id 2} ~form))
(defn read-rtrace3-tag [form] `(runi {:flow-id 3} ~form))
(defn read-rtrace4-tag [form] `(runi {:flow-id 4} ~form))
(defn read-rtrace5-tag [form] `(runi {:flow-id 5} ~form))

(defn read-tap-tag [form]
  `(let [form-val# ~form]
     (tap> form-val#)
     form-val# ))

(comment

  #rtrace (+ 1 2 (* 3 4))

  #trace (defn factorial [n] (if (zero? n) 1 (* n (factorial (dec n)))))
  #rtrace (factorial 5)

  (indexes-api/start)

  (indexes-api/all-threads)

  (do
    (def flow-id (-> (indexes-api/all-threads) first first))
    (def thread-id (-> (indexes-api/all-threads) first second)))

  (indexes-api/all-forms flow-id thread-id)

  (indexes-api/timeline-count flow-id thread-id)

  (indexes-api/timeline-entry flow-id thread-id 2)

  (indexes-api/frame-data flow-id thread-id 2)

  (indexes-api/bindings flow-id thread-id 52)

  (def r-node (indexes-api/callstack-tree-root-node flow-id thread-id))

  (-> r-node
      indexes-api/callstack-node-childs
      (nth 0)
      indexes-api/callstack-node-frame)

  (indexes-api/callstack-node-childs r-node)

  (indexes-api/fn-call-stats flow-id thread-id)

  (indexes-api/find-fn-frames flow-id thread-id "flow-storm.api" "factorial" 71712880)

  (indexes-api/search-next-frame-idx flow-id thread-id "3" 0 {})

  (indexes-api/discard-flow flow-id)
  )
