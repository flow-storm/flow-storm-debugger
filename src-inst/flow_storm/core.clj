(ns flow-storm.core
  (:require [flow-storm.instrument.forms :as inst-forms]
            [flow-storm.instrument.namespaces :as inst-ns]
            [flow-storm.utils :refer [log]]
            [flow-storm.tracer :as tracer]
            [clojure.repl :as clj.repl]))

(defn disable-from-profile [profile]
  (case profile
    :full {}
    :light {:disable #{:expr :binding :anonymous-fn}}
    {}))

(defmacro run-with-execution-ctx
  [{:keys [orig-form ns flow-id]} form]
  `(let [flow-id# ~(or flow-id 0)
         curr-ns# ~(or ns `(str (ns-name *ns*)))]
     (binding [tracer/*runtime-ctx* (tracer/build-runtime-ctx {:flow-id flow-id#
                                                               :tracing-disabled? false})]
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
         (inst-ns/instrument-and-eval-form form-ns form config)
         (log (format "Instrumented %s" var-symb)))

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
              (log (format "Uninstrumented %s" v)))

            (log (format "Don't know howto untrace %s" (pr-str expanded-form))))

          (log (format "Couldn't find source for %s" var-symb)))))))
