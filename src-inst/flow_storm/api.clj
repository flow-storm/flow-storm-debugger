(ns flow-storm.api

  "API intended for users.
  Provides functionality to start the debugger and instrument forms."

  (:require [flow-storm.tracer :as tracer]
            [flow-storm.utils :refer [log] :as utils]
            [flow-storm.ns-reload-utils :as reload-utils]
            [hansel.api :as hansel]
            [hansel.instrument.utils :as inst-utils]
            [flow-storm.runtime.debuggers-api :as dbg-api]
            [flow-storm.runtime.events :as rt-events]
            [flow-storm.runtime.values :as rt-values]
            [clojure.string :as str]
            [clojure.stacktrace :as stacktrace]))

;; TODO: build script
;; Maybe we can figure out this ns names by scanning (all-ns) so
;; we don't need to list them here
;; Also maybe just finding main is enough, we can add to it a fn
;; that returns the rest of the functions we need
(def debugger-main-ns 'flow-storm.debugger.main)

(defn start-debugger-ui

  "Start the debugger UI when available on the classpath.
  Returns true when available, false otherwise."

  [config]

  (if-let [start-debugger (requiring-resolve (symbol (name debugger-main-ns) "start-debugger"))]
    (do
      (start-debugger config)
      true)
    (do
      (log "It looks like the debugger UI isn't present on the classpath.")
      false)))

(defn stop-debugger-ui

  "Stop the debugger UI if it has been started."

  []
  (if-let [stop-debugger (requiring-resolve (symbol (name debugger-main-ns) "stop-debugger"))]
    (stop-debugger)
    (log "It looks like the debugger UI isn't present on the classpath.")))


(defn stop

  "Stop the flow-storm runtime part gracefully.
  If working in local mode will also stop the UI."

  []

  (rt-events/clear-dispatch-fn!)

  ;; if we are running in local mode and running a debugger stop it
  (stop-debugger-ui)

  (dbg-api/stop-runtime)

  (log "System fully stopped"))

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
         config (assoc config :local? true)]

     (dbg-api/start-runtime)

     (start-debugger-ui config)

     (rt-events/set-dispatch-fn enqueue-event!))))


(def jump-to-last-expression dbg-api/jump-to-last-expression-in-this-thread)

(defn set-thread-trace-limit

  "Set a trace limit to all threads. When the limit is positive, if any thread timeline goes
  beyond the limit the thread code will throw an exception."

  [limit]
  (dbg-api/set-thread-trace-limit limit))

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
   (utils/ensure-vanilla)
   (dbg-api/vanilla-instrument-var :clj var-symb config)))

(defn uninstrument-var-clj

  "Remove instrumentation given a var symbol.

  (uninstrument-var-clj var-symb)"

  [var-symb]

  (utils/ensure-vanilla)
  (dbg-api/vanilla-uninstrument-var :clj var-symb {}))

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
  ([prefixes opts]
   (utils/ensure-vanilla)
   (dbg-api/vanilla-instrument-namespaces :clj prefixes opts)))

(defn uninstrument-namespaces-clj

  "Undo instrumentation made by `flow-storm.api/instrument-namespaces-clj`"

  ([prefixes] (uninstrument-namespaces-clj prefixes {}))
  ([prefixes opts]
   (utils/ensure-vanilla)
   (dbg-api/vanilla-uninstrument-namespaces :clj prefixes opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ClojureScript instrumentation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn instrument-var-cljs

  "Like `flow-storm.api/vanilla-instrument-var-clj` but for using it from the shadow Clojure repl.

  Arguments are the same as the Clojure version but `config` also accepts a `:build-id`"

  ([var-symb] (instrument-var-cljs var-symb {}))
  ([var-symb config]

   (dbg-api/vanilla-instrument-var :cljs var-symb config)))

(defn uninstrument-var-cljs

  "Like `flow-storm.api/uninstrument-var-clj` but for using it from the shadow Clojure repl.

  Arguments are the same as the Clojure version but `config` needs a `:build-id`"

  [var-symb config]

  (dbg-api/vanilla-uninstrument-var :cljs var-symb config))

(defn instrument-namespaces-cljs

  "Like `flow-storm.api/instrument-namespaces-clj` but for using it from the shadow Clojure repl.

  Arguments are the same as the Clojure version but `config` also accepts a `:build-id`"

  ([prefixes] (instrument-namespaces-clj prefixes {}))
  ([prefixes opts] (dbg-api/vanilla-instrument-namespaces :cljs prefixes opts)))

(defn uninstrument-namespaces-cljs

  "Like `flow-storm.api/uninstrument-namespaces-clj` but for using it from the shadow Clojure repl.
  Arguments are the same as the Clojure version but `config` also accepts a `:build-id`"

  [prefixes config]
  (dbg-api/vanilla-uninstrument-namespaces :cljs prefixes config))

(defn- runi* [{:keys [ns flow-id env] :as opts} form]
  ;; ~'flowstorm-runi is so it doesn't expand into flow-storm.api/flowstorm-runi which
  ;; doesn't work with fn* in clojurescript
  (utils/ensure-vanilla)
  (let [wrapped-form `(fn* ~'flowstorm-runi ([] ~form))
        ns (or ns (when-let [env-ns (-> env :ns :name)]
                    (str env-ns)))
        hansel-config (assoc (tracer/hansel-config opts)
                             :env env)
        {:keys [inst-form init-forms]} (hansel/instrument-form hansel-config wrapped-form)]
    `(let [flow-id# ~(or flow-id (-> form meta :flow-id) 0)
           curr-ns# ~(or ns `(when *ns* (str (ns-name *ns*))) (-> env :ns :name str))]
       ~@init-forms

       (~inst-form))))

(defmacro runi

  "Run instrumented.

  (runi opts form)

  Instrument form and run it for tracing.

  Same as doing #rtrace `form`.

  `opts` is a map that support the same keys as `instrument-var`. "

  [opts form]

  (runi* (assoc opts :env &env) form))

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
             (rt-events/publish-event! (rt-events/make-vanilla-var-instrumented-event vname# vns#))
             (add-watch v#
                        :flow-storm/var-redef
                        (fn [a1# a2# fn-before# fn-after#]
                          (cond

                            (and (:hansel/instrumented? (meta fn-before#))
                                 (not (:hansel/instrumented? (meta fn-after#))))
                            (rt-events/publish-event! (rt-events/make-vanilla-var-uninstrumented-event vname# vns#))

                            (and (not (:hansel/instrumented? (meta fn-before#)))
                                 (:hansel/instrumented? (meta fn-after#)))
                            (rt-events/publish-event! (rt-events/make-vanilla-var-instrumented-event vname# vns#))))))))


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
  (let [full-config (merge config (meta form))]
    `(cond

       (utils/storm-env?) (throw (ex-info "#rtrace and #trace can't be used with ClojureStorm, they aren't needed. All your configured compilations will be automatically instrumented. Please re-run the expression without it. Evaluation skipped." {}))

       (not (tracer/recording?)) (do
                                   (log "FlowStorm recording is paused, please switch recording on before running with #rtrace.")
                                   ~form)

       :else (let [_# (dbg-api/discard-flow (tracer/get-current-flow-id))
                   res# (runi ~full-config ~form)]
               (dbg-api/jump-to-last-expression-in-this-thread)
               res#))))

(defn read-rtrace-tag [form]  (read-rtrace-tag* {} form))

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
  ([fq-fn-symb] (dbg-api/add-breakpoint! fq-fn-symb {}))
  ([fq-fn-symb args-pred] (dbg-api/add-breakpoint! fq-fn-symb {} args-pred)))

(defn remove-break [fq-fn-symb]
  (dbg-api/remove-breakpoint! fq-fn-symb {}))

(def unblock-thread dbg-api/unblock-thread)
(def clear-breaks dbg-api/clear-breakpoints!)

(defn start-recording [] (dbg-api/set-recording true))
(defn stop-recording [] (dbg-api/set-recording false))

(defn set-before-reload-callback! [cb]
  (reload-utils/set-before-reload-callback! cb))

(defn set-after-reload-callback! [cb]
  (reload-utils/set-after-reload-callback! cb))

(defn data-window-push-val
  ([dw-id val] (data-window-push-val dw-id val nil))
  ([dw-id val stack-key]
   (let [vdata (assoc (rt-values/extract-data-aspects val)
                      :flow-storm.debugger.ui.data-windows.data-windows/dw-id dw-id
                      :flow-storm.debugger.ui.data-windows.data-windows/stack-key stack-key)]
     (rt-events/publish-event! (rt-events/make-data-window-push-val-data-event dw-id vdata true)))))

(defn data-window-val-update [dw-id new-val]
  (rt-events/publish-event! (rt-events/make-data-window-update-event dw-id {:new-val new-val})))
