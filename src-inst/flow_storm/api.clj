(ns flow-storm.api
  "This is the only namespace intended for users.
  Provides functionality to connect to the debugger and instrument forms."
  (:require [flow-storm.tracer :as tracer]
            [flow-storm.instrument.trace-types :as inst-trace-types]
            [flow-storm.utils :refer [log log-error] :as utils]
            [flow-storm.instrument.namespaces :as inst-ns]
            [flow-storm.instrument.forms :as inst-forms]
            [flow-storm.core :as fs-core]
            [flow-storm.json-serializer :as serializer])
  (:import [org.java_websocket.client WebSocketClient]
           [java.net URI]
           [org.java_websocket.handshake ServerHandshake]))

(def debugger-trace-processor-ns 'flow-storm.debugger.trace-processor)
(def debugger-main-ns 'flow-storm.debugger.main)

(defn local-connect

  "Start a debugger under this same JVM process and connect to it.

  This is the recommended way of using the debugger for debugging code that
  generates a lot of data since data doesn't need to serialize/deserialize it like
  in a remote debugging session case.

  `config` should be a map containing: `:verbose?`"

  ([] (local-connect {}))

  ([config]
   (require debugger-trace-processor-ns)
   (require debugger-main-ns)
   (let [config (assoc config :local? true)
         local-dispatch-trace (resolve (symbol (name debugger-trace-processor-ns) "local-dispatch-trace"))
         start-debugger       (resolve (symbol (name debugger-main-ns) "start-debugger"))]
     (start-debugger config)
     (tracer/start-trace-sender
      (assoc config
             :send-fn (fn local-send [trace]
                        (try
                          (local-dispatch-trace trace)
                          (catch Exception e
                            (log-error "Exception dispatching trace " e)))))))))

(def remote-websocket-client nil)

(defn close-remote-connection []
  (when remote-websocket-client
    (.close remote-websocket-client)))

(defn remote-connect

  "Connect to a remote debugger.
  Without arguments connects to localhost:7722.

  `config` is a map with `:host`, `:port`"

  ([] (remote-connect {:host "localhost" :port 7722}))

  ([{:keys [host port on-connected]
     :or {host "localhost"
          port 7722}
     :as config}]

   (close-remote-connection) ;; if there is any active connection try to close it first

   (let [uri-str (format "ws://%s:%s/ws" host port)
         ^WebSocketClient ws-client (proxy
                                        [WebSocketClient]
                                        [(URI. uri-str)]

                                      (onOpen [^ServerHandshake handshake-data]
                                        (log (format "Connection opened to %s" uri-str))
                                        (when on-connected (on-connected)))

                                      (onMessage [^String message]
                                        (let [[comm-id method args-map] (serializer/deserialize message)
                                              ret-packet (fs-core/run-command comm-id method args-map)
                                              ret-packet-ser (serializer/serialize ret-packet)]
                                          (.send remote-websocket-client ret-packet-ser)))

                                      (onClose [code reason remote?]
                                        (log (format "Connection with %s closed. code=%s reson=%s remote?=%s"
                                                     uri-str code reason remote?)))

                                      (onError [^Exception e]
                                        (log-error (format "WebSocket error connection %s" uri-str) e)))

         remote-dispatch-trace (fn remote-dispatch-trace [trace]
                                 (let [packet [:trace trace]
                                       ser (serializer/serialize packet)]
                                   ;; websocket library isn't clear about thread safty of send
                                   ;; lets synchronize just in case
                                   (locking ws-client
                                     (.send ^WebSocketClient ws-client ^String ser))))]

     (.setConnectionLostTimeout ws-client 0)
     (.connect ws-client)

     (tracer/start-trace-sender
      (assoc config
             :send-fn (fn remote-send [trace]
                        (try
                          (-> trace
                              inst-trace-types/ref-values!
                              remote-dispatch-trace)
                          (catch Exception e
                            (log-error "Exception dispatching trace " e))))))

     (alter-var-root #'remote-websocket-client (constantly ws-client)))))

(def instrument-var

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

  fs-core/instrument-var)

(def uninstrument-var

  "Remove instrumentation given a var symbol.

  (uninstrument-var var-symb)"

  fs-core/uninstrument-var)

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
       - :disable is a set containing any of #{:expr :binding :anonymous-fn}
                  useful for disabling unnecesary traces in code that generate too many traces
  "

  [prefixes opts]

  (inst-ns/instrument-files-for-namespaces prefixes (assoc opts :prefixes? true)))

(defn uninstrument-forms-for-namespaces

  "Undo instrumentation made by `flow-storm.api/instrument-forms-for-namespaces`"

  [prefixes]

  (inst-ns/uninstrument-files-for-namespaces prefixes {:prefixes? true}))


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

  [{:keys [instrument-ns excluding-ns require-before fn-symb fn-args profile verbose? styles
           host port] :as opts}]
  (let [valid-opts-keys #{:instrument-ns :excluding-ns :require-before :fn-symb :fn-args :profile :verbose? :styles :host :port}]

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
        (remote-connect {:host host :port port :verbose? verbose? :styles styles})
        (local-connect {:verbose? verbose? :styles styles}))
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

(defn read-rtrace-tag [form]
  `(runi {} ~form))
