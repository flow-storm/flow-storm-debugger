(ns flow-storm.instrument.namespaces
  (:require [flow-storm.instrument.forms :as inst-forms]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.reader :as reader]
            [flow-storm.utils :as utils :refer [log]])
  (:import [java.io PushbackReader]))

(defn all-ns-with-prefixes

  "Return all loaded namespaces that start with `prefixes` but
  excluding `excluding-ns`."

  [prefixes {:keys [excluding-ns]}]
  (->> (all-ns)
       (keep (fn [ns]
               (let [nsname (str (ns-name ns))]
                 (when (and (not (excluding-ns nsname))
                            (some (fn [prefix]
                                    (str/starts-with? nsname prefix))
                                  prefixes))
                  ns))))))

(defn ns-vars

  "Return all vars for a `ns`."

  [ns]
  (vals (ns-interns ns)))

(defn interesting-files-for-namespaces

  "Given a set of namespaces `ns-set` return a set of all files
  used to load them."

  [ns-set]
  (reduce (fn [r ns]
            (let [ns-vars (vals (ns-interns ns))]
              (into r (keep (fn [v]
                              (when-let [file (:file (meta v))]
                                (io/resource file)))
                            ns-vars))))
          #{}
          ns-set))

(defn uninteresting-form?

  "Predicate to check if a `form` is interesting to instrument."

  [ns form]

  (or (nil? form)
      (when (and (seq? form)
                 (symbol? (first form)))
        (contains? '#{"ns" "defmulti" "defprotocol" "defmacro" "comment" "import-macros"}
                   (name (first form))))
      (let [macro-expanded-form (try
                                  (inst-forms/macroexpand-all macroexpand-1 form ::original-form)
                                  (catch Exception e
                                    (let [e-msg (.getMessage e)
                                          ex-type (cond
                                                    ;; core.cljs has a macro called resolve, and also some local fns
                                                    ;; shadowing resolve with a local fn
                                                    ;; When applying clojure.walk/macroexpand-all or our inst-forms/macroexpand-all it doesn't work
                                                    ;; Hard to fix, and there shouldn't be a lot of cases like that
                                                    (and (= (ns-name ns) 'cljs.core)
                                                         (str/includes? e-msg "macroexpanding resolve"))
                                                    :known-error

                                                    :else
                                                    :unknown-error)]
                                      (throw (ex-info "Error macroexpanding form" {:type ex-type})))))
            kind (inst-forms/expanded-form-type macro-expanded-form {:compiler :clj})]
        (not (contains? #{:defn :defmethod :extend-type :extend-protocol} kind)))))

(defn expanded-defn-parse

  "Given a `ns-name` and a `expanded-defn-form` (macroexpanded) returns
  [fn-name-symbol fn-body]."

  [ns-name expanded-defn-form]

  (let [[_ var-name var-val] expanded-defn-form
        var-symb (symbol ns-name (str var-name))]
    [(find-var var-symb) var-val]))

(defn instrument-and-eval-form

  "Instrument `form` and evaluates it under `ns`."

  [ns form config]

  (let [ctx (inst-forms/build-form-instrumentation-ctx config (str (ns-name ns)) form nil)

        inst-form (try
                    (-> form
                        (inst-forms/instrument-all ctx)
                        (inst-forms/maybe-unwrap-outer-form-instrumentation ctx))
                    (catch Exception _
                      (throw (ex-info "Error instrumenting form" {:type :unknown-error}))))]

    (try
      (if (inst-forms/expanded-def-form? inst-form)
        (let [[v vval] (expanded-defn-parse (str (ns-name ns)) inst-form)]
          (alter-var-root v (fn [_] (eval vval))))
        (do
          ;; enable for debugging
          #_(log (with-out-str (clojure.pprint/pprint inst-form)))
          (eval inst-form)))
      (catch Exception e
        #_(utils/log-error (format "Evaluating form %s" (pr-str inst-form)) e)
        #_(System/exit 1)
        (let [e-msg (.getMessage e)
              ex-type (cond

                        ;; known issue, using recur inside fn* (without loop*)
                        (str/includes? e-msg "recur")
                        :known-error

                        :else
                        :unknown-error)]
          (throw (ex-info "Error evaluating form" {:type ex-type})))))))

(defn read-file-ns-decl

  "Attempts to read a (ns ...) declaration from `file` and returns the unevaluated form.

  Returns nil if ns declaration cannot be found.

  `read-opts` is passed through to tools.reader/read."

  [file]
  (let [ns-decl? (fn [form] (and (list? form) (= 'ns (first form))))]
    (with-open [rdr (PushbackReader. (io/reader file))]
     (let [opts {:read-cond :allow
                 :features #{:clj}
                 :eof ::eof}]
       (loop []
         (let [form (reader/read opts rdr)]
           (cond
             (ns-decl? form) form
             (= ::eof form) nil
             :else (recur))))))))

(defn- instrument-and-eval-file-forms

  "Instrument and evaluates all forms in `file-url`"

  [file-url config]

  (let [[_ ns-from-decl] (read-file-ns-decl file-url)]
    (if-not ns-from-decl

      (log (format "Warning, skipping %s since it doesn't contain a (ns ) decl. We don't support (in-ns ...) yet." (.getFile file-url)))

      ;; this is IMPORTANT, once we have `ns-from-decl` all the instrumentation work
      ;; should be done as if we where in `ns-from-decl`
      (binding [*ns* (find-ns ns-from-decl)]
        (when-not (= ns-from-decl 'clojure.core) ;; we don't want to instrument clojure core since it brings too much noise
         (let [ns (find-ns ns-from-decl)
               file-forms (read-string {:read-cond :allow}
                                       (format "[%s]" (slurp file-url)))]
           (log (format "Instrumenting namespace: %s Forms (%d) (%s)" ns-from-decl (count file-forms) (.getFile file-url)))

           (doseq [form file-forms]
             (try

               (if (uninteresting-form? ns form)
                 (print ".")

                 (do
                   (instrument-and-eval-form ns form config)
                   (print "I")))

               (catch clojure.lang.ExceptionInfo ei
                 (let [ex-type (:type (ex-data ei))]
                   ;; Enable for debugging unknown errors
                   #_(when (= ex-type :unknown-error)
                       (log (ex-message ei))
                       (System/exit 1))
                   (case ex-type
                       :known-error   (print (utils/colored-string "X" :yellow))
                       :unknown-error (print (utils/colored-string "X" :red)))))))
           (println)))))))

(defn instrument-files-for-namespaces

  "Instrument and evaluates all forms of all loaded namespaces matching
  the `prefixes` set."

  [prefixes config]

  (let [config (-> config
                   (update :excluding-ns #(or % #{}))
                   (update :disable #(or % #{})))
        ns-set (all-ns-with-prefixes prefixes config)
        files-set (interesting-files-for-namespaces ns-set)]
    (doseq [file files-set]
      (instrument-and-eval-file-forms file config))))
