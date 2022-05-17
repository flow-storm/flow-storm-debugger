(ns flow-storm.core
  (:require [flow-storm.instrument.forms :as inst-forms]
            [flow-storm.instrument.namespaces :as inst-ns]
            [flow-storm.utils :refer [log]]
            [flow-storm.tracer :as tracer]
            [flow-storm.instrument.trace-types :as trace-types]
            [clojure.repl :as clj.repl]
            [clojure.pprint :as pp]))

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

(defmacro run-with-execution-ctx
  [{:keys [orig-form ns flow-id]} form]
  `(let [flow-id# ~(or flow-id 0)
         curr-ns# ~(or ns `(str (ns-name *ns*)))]
     (binding [tracer/*runtime-ctx* (tracer/empty-runtime-ctx flow-id#)]
       (tracer/trace-flow-init-trace flow-id# curr-ns# ~(or orig-form (list 'quote form)))
       ~form)))

(defn instrument-var
  ([var-symb] (instrument-var var-symb {}))
  ([var-symb config]
   (let [form (some->> (clj.repl/source-fn var-symb)
                       (read-string {:read-cond :allow}))
         form-ns (find-ns (symbol (namespace var-symb)))]
     (if form

       (binding [*ns* form-ns]
         (inst-ns/instrument-and-eval-form form-ns form config))

       (log (format "Couldn't find source for %s" var-symb))))))

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

(defn- instrument-fn-command [{:keys [fn-symb]}]
  (instrument-var fn-symb))

(defn uninstrument-fns-command [{:keys [vars-symbs]}]
  (doseq [var-symb vars-symbs]
    (uninstrument-var var-symb)))

(defn eval-forms-command [{:keys [forms]}]
  (doseq [{:keys [form-ns form]} forms]
    (binding [*ns* (find-ns (symbol form-ns))]
      (eval form))))

(defn instrument-forms-command [{:keys [forms config]}]
  (doseq [{:keys [form-ns form]} forms]
    (binding [*ns* (find-ns (symbol form-ns))]
      (inst-ns/instrument-and-eval-form (find-ns (symbol form-ns)) form config))))

(defn re-run-flow-command [{:keys [flow-id execution-expr]}]
  (let [{:keys [ns form]} execution-expr]
    (binding [*ns* (find-ns (symbol ns))]
      (run-with-execution-ctx
       {:flow-id flow-id
        :orig-form form}
       (eval form)))))

(defn- get-remote-value-command [{:keys [vid print-length print-level pprint? nth-elem]}]
  (let [value (trace-types/get-reference-value vid)
        print-fn (if pprint? pp/pprint print)]
    (with-out-str
      (binding [*print-level* print-level
                *print-length* print-length]
        (print-fn (cond-> value
                    nth-elem (nth nth-elem)))))))

(defn run-command [method args-map]
  (let [f (case method
            :instrument-fn        instrument-fn-command
            :uninstrument-fns     uninstrument-fns-command
            :eval-forms           eval-forms-command
            :instrument-forms     instrument-forms-command
            :re-run-flow          re-run-flow-command
            :get-remote-value     get-remote-value-command)]
    (f args-map)))
