(ns flow-storm.core
  (:require [flow-storm.instrument.forms :as inst-forms]
            [flow-storm.instrument.namespaces :as inst-ns]
            [flow-storm.utils :refer [log]]
            [flow-storm.tracer :as tracer]
            [clojure.repl :as clj.repl]))

(defmacro instrument

  "Recursively instrument a form for tracing."

  ([form] `(instrument {:disable #{}} ~form)) ;; need to do this so multiarity macros work
  ([config form]
   (let [form-ns (str (ns-name *ns*))
         ctx (inst-forms/build-form-instrumentation-ctx config form-ns form &env)
         inst-code (-> form
                       (inst-forms/instrument-all ctx)
                       (inst-forms/maybe-unwrap-outer-form-instrumentation ctx))]

     ;; Uncomment to debug
     ;; Printing on the *err* stream is important since
     ;; printing on standard output messes  with clojurescript macroexpansion
     #_(let [pprint-on-err (fn [x]
                           (binding [*out* *err*] (pp/pprint x)))]
       (pprint-on-err (inst-forms/macroexpand-all form))
       (pprint-on-err inst-code))

     inst-code)))

(defn instrument-var [var-symb config]
  (let [form (some->> (clj.repl/source-fn var-symb)
                      (read-string {:read-cond :allow}))
        form-ns (find-ns (symbol (namespace var-symb)))]
    (if form

      (binding [*ns* form-ns]
        (inst-ns/instrument-and-eval-form form-ns form config))

      (log (format "Couldn't find source for %s" var-symb)))))

(defn uninstrument-var [var-symb]
  (let [ns-name (namespace var-symb)]
    (binding [*ns* (find-ns (symbol ns-name))]
      (let [form (some->> (clj.repl/source-fn var-symb)
                          (read-string {:read-cond :allow}))
            expanded-form (inst-forms/macroexpand-all macroexpand-1 form ::original-form)]
        (if form

          (if (inst-forms/expanded-def-form? expanded-form)
            (let [[v vval] (inst-ns/expanded-defn-parse ns-name expanded-form)]
              (alter-var-root v (fn [_] (eval vval)))
              (log (format "Untraced %s" v)))

            (log (format "Don't know howto untrace %s" (pr-str expanded-form))))

          (log (format "Couldn't find source for %s" var-symb)))))))

(defn uninstrument-vars [vars-symbs]
  (doseq [var-symb vars-symbs]
    (uninstrument-var var-symb)))

(defmacro run-with-execution-ctx
  [{:keys [orig-form ns flow-id]} form]
  `(let [flow-id# ~(or flow-id 0)
         curr-ns# ~(or ns `(str (ns-name *ns*)))]
     (binding [tracer/*runtime-ctx* (tracer/empty-runtime-ctx flow-id#)]
       (tracer/trace-flow-init-trace flow-id# curr-ns# ~(or orig-form (list 'quote form)))
       ~form)))

(defn eval-form-bulk

  "Forms should be a collection of maps with :form-ns :form.
  Evaluates each `form` under `form-ns`"

  [forms]
  (doseq [{:keys [form-ns form]} forms]
    (binding [*ns* (find-ns (symbol form-ns))]
      (eval form))))

(defn instrument-form-bulk

  "Forms should be a collection of maps with :form-ns :form.
  Evaluates each `form` under `form-ns` instrumented."

  [forms config]
  (doseq [{:keys [form-ns form]} forms]
    (binding [*ns* (find-ns (symbol form-ns))]
      (inst-ns/instrument-and-eval-form (find-ns (symbol form-ns)) form config))))

(defn re-run-flow

  "Evaluates `form` under `ns` inside a execution-ctx"

  [flow-id {:keys [ns form]}]

  (binding [*ns* (find-ns (symbol ns))]
    (run-with-execution-ctx
     {:flow-id flow-id
      :orig-form form}
     (eval form))))

#_(defn ws-connect [opts]
  (let [{:keys [send-fn]} (tracer/build-ws-sender opts)]
    (tracer/start-trace-sender {:send-fn send-fn})))

#_(defn file-connect [opts]
  (let [{:keys [send-fn]} (tracer/build-file-sender opts)]
    (tracer/start-trace-sender {:send-fn send-fn})))

#_(def trace-ref
  "Adds a watch to ref with ref-name that traces its value changes.
  The first argument is the ref to watch for.
  The second argument is a options map. Available options are :
  - :ref-name A string name for the ref.
  - :ignore-keys A collection of keys that will be skipped in traces.

  :ignore-keys only works for maps and does NOT ignore nested maps keys."
  tracer/trace-ref)

#_(def untrace-ref
  "Removes the watch added by trace-ref."
  tracer/untrace-ref)
