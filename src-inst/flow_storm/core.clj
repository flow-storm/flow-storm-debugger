(ns flow-storm.core
  (:require [flow-storm.instrument.forms :as inst-forms]
            [flow-storm.instrument.namespaces :as inst-ns]
            [flow-storm.utils :refer [log-error log]]
            [flow-storm.core-multi :refer [get-remote-value-command]]
            [flow-storm.tracer :as tracer]
            [flow-storm.instrument.trace-types :as trace-types]
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
  (try
    (let [{:keys [ns form]} execution-expr]
     (binding [*ns* (find-ns (symbol ns))]
       (run-with-execution-ctx
        {:flow-id flow-id
         :orig-form form}
        (eval form))))
    (catch Exception e
      (log-error (format "re-run-flow-command couldn't re run execution-expr %s" execution-expr) e))))

(defn- def-value-command [{:keys [val val-name]}]
  (intern 'user (symbol val-name) val))

(defn- def-remote-value-command [{:keys [vid val-name]}]
  (intern 'user (symbol val-name) (trace-types/get-reference-value vid)))

(defn- get-all-namespaces-command [_]
  (map (comp name ns-name) (all-ns)))

(defn- get-all-vars-for-ns-command [{:keys [ns-name]}]
  (->> (find-ns (symbol ns-name))
       ns-interns
       vals
       (map (fn [v]
              (let [{:keys [name ns]} (meta v)]
                {:var-name (str name)
                 :var-ns (str (clojure.core/ns-name ns))})))))

(defn- get-var-meta-command [{:keys [var-name var-ns]}]
  (-> (find-var (symbol var-ns var-name))
      meta
      (update :ns (comp str ns-name))
      (update :name str)))

(defn- instrument-namespaces-command [{:keys [ns-names profile]}]
  (inst-ns/instrument-files-for-namespaces ns-names (assoc (disable-from-profile profile)
                                                           :prefixes? false)))

(defn- uninstrument-namespaces-command [{:keys [ns-names]}]
  (inst-ns/instrument-files-for-namespaces ns-names {:prefixes? false
                                                     :uninstrument? true}))

(defn run-command [comm-id method args-map]
  (let [f (case method
            :instrument-fn         instrument-fn-command
            :uninstrument-fns      uninstrument-fns-command
            :eval-forms            eval-forms-command
            :instrument-forms      instrument-forms-command
            :re-run-flow           re-run-flow-command
            :get-remote-value      get-remote-value-command
            :def-value             def-value-command
            :def-remote-value      def-remote-value-command
            :get-all-namespaces    get-all-namespaces-command
            :get-all-vars-for-ns   get-all-vars-for-ns-command
            :get-var-meta          get-var-meta-command
            :instrument-namespaces   instrument-namespaces-command
            :uninstrument-namespaces uninstrument-namespaces-command
            )]
    [:cmd-ret [comm-id (f args-map)]]))
