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

(defn- macroexpansion-error-data [ns ex]
  (let [e-msg (.getMessage ex)]
    (cond
      ;; Hard to fix, and there shouldn't be a lot of cases like that
      (and (= (ns-name ns) 'cljs.core)
           (str/includes? e-msg "macroexpanding resolve"))

      {:type :known-error
       :messge "ClojureScript macroexpanding resolve. core.cljs has a macro called resolve, and also some local fns shadowing resolve with a local fn. When applying clojure.walk/macroexpand-all or our inst-forms/macroexpand-all it doesn't work."}

      :else
      {:type :unknown-error})))

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
                                    (throw (ex-info "Error macroexpanding form" (macroexpansion-error-data ns e)))))
            kind (inst-forms/expanded-form-type macro-expanded-form {:compiler :clj})]
        (not (contains? #{:defn :defmethod :extend-type :extend-protocol} kind)))))

(defn expanded-defn-parse

  "Given a `ns-name` and a `expanded-defn-form` (macroexpanded) returns
  [fn-name-symbol fn-body]."

  [ns-name expanded-defn-form]

  (let [[_ var-name var-val] expanded-defn-form
        var-symb (symbol ns-name (str var-name))]
    [(find-var var-symb) var-val]))

(defn eval-form-error-data [inst-form ex]
  (let [e-msg (.getMessage ex)]
    (cond

      ;; known issue, using recur inside fn* (without loop*)
      (str/includes? e-msg "recur")
      {:type :known-error
       :msg "We can't yet instrument using recur inside fn* (without loop*)"}

      (and (.getCause ex) (str/includes? (.getMessage (.getCause ex)) "Must assign primitive to primitive mutable"))
      {:type :known-error
       :msg "Instrumenting (set! x ...) inside a deftype* being x a mutable primitive type confuses the compiler"
       :retry-disabling #{:expr}}

      (and (.getCause ex) (str/includes? (.getMessage (.getCause ex)) "Method code too large!"))
      {:type :known-error
       :msg "Instrumented expresion is too large for the clojure compiler"
       :retry-disabling #{:expr}}

      :else
      (binding [*print-meta* true]
        (utils/log-error (format "Evaluating form %s Msg: %s Cause : %s" (pr-str inst-form) (.getMessage ex) (.getMessage (.getCause ex))) ex)
        (System/exit 1)
        {:type :unknown-error}))))

(defn instrument-and-eval-form

  "Instrument `form` and evaluates it under `ns`."

  ([ns form config] (instrument-and-eval-form ns form config false))

  ([ns form config retrying?]

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

         (eval inst-form))
       (catch Exception e
         (let [{:keys [msg retry-disabling] :as error-data} (eval-form-error-data inst-form e)]
           (if (and (not retrying?) retry-disabling)
             (do
               (log (utils/colored-string (format "\n\nKnown error %s, retrying disabling %s\n\n" msg retry-disabling)
                                          :yellow))
               (instrument-and-eval-form ns form (assoc config :disable retry-disabling) true))
             (throw (ex-info "Error evaluating form" error-data)))))))))

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

      (log (utils/colored-string (format "\n\nWarning, skipping %s since it doesn't contain a (ns ) decl. We don't support (in-ns ...) yet.\n\n" (.getFile file-url))
                                 :yellow))

      ;; this is IMPORTANT, once we have `ns-from-decl` all the instrumentation work
      ;; should be done as if we where in `ns-from-decl`
      (binding [*ns* (find-ns ns-from-decl)]
        (when-not (= ns-from-decl 'clojure.core) ;; we don't want to instrument clojure core since it brings too much noise
         (let [ns (find-ns ns-from-decl)
               file-forms (read-string {:read-cond :allow}
                                       (format "[%s]" (slurp file-url)))]
           (log (format "\nInstrumenting namespace: %s Forms (%d) (%s)" ns-from-decl (count file-forms) (.getFile file-url)))

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
