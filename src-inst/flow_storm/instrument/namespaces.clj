(ns flow-storm.instrument.namespaces
  (:require [flow-storm.instrument.forms :as inst-forms]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.tools.namespace.parse :as tools-ns-parse]
            [clojure.tools.namespace.file :as tools-ns-file]
            [clojure.tools.namespace.dependency :as tools-ns-deps]
            [flow-storm.utils :as utils :refer [log]]))

(def flow-storm-ns-tag "FLOWNS")

(defn all-namespaces

  "Return all loaded namespaces that match with `ns-strs` but
  excluding `excluding-ns`. If `prefixes?` is true, `ns-strs`
  will be used as prefixes, else a exact match will be required."

  [ns-strs {:keys [excluding-ns prefixes?]}]
  (->> (all-ns)
       (keep (fn [ns]
               (let [nsname (str (ns-name ns))]
                 (when (and (not (excluding-ns nsname))
                            (not (str/includes? nsname flow-storm-ns-tag))
                            (some (fn [ns-str]
                                    (if prefixes?
                                      (str/starts-with? nsname ns-str)
                                      (= nsname ns-str)))
                                  ns-strs))
                   ns))))))

(defn ns-vars

  "Return all vars for a `ns`."

  [ns]
  (vals (ns-interns ns)))

(defn read-file-ns-decl

  "Attempts to read a (ns ...) declaration from `file` and returns the unevaluated form.

  Returns nil if ns declaration cannot be found.

  `read-opts` is passed through to tools.reader/read."

  [file]
  (tools-ns-file/read-file-ns-decl file))

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

(defn- macroexpansion-error-data [ns form ex]
  (let [e-msg (.getMessage ex)]
    (cond
      ;; Hard to fix, and there shouldn't be a lot of cases like that
      (and (= (ns-name ns) 'cljs.core)
           (str/includes? e-msg "macroexpanding resolve"))
      {:type :known-error
       :msg "ClojureScript macroexpanding resolve. core.cljs has a macro called resolve, and also some local fns shadowing resolve with a local fn. When applying clojure.walk/macroexpand-all or our inst-forms/macroexpand-all it doesn't work."}

      :else
      {:type :unknown-error
       :ns (ns-name ns)
       :form form
       :msg e-msg})))

(defn uninteresting-form?

  "Predicate to check if a `form` is interesting to instrument."

  [ns form]

  (or (nil? form)
      (when (and (seq? form)
                 (symbol? (first form)))
        (contains? '#{"ns" "defmulti" "defprotocol" "defmacro" "comment" "import-macros"}
                    (name (first form))))
      (let [macro-expanded-form (try
                                  (inst-forms/macroexpand-all macroexpand-1
                                                              (fn [symb] (symbol (resolve symb)))
                                                              form
                                                              ::original-form)
                                  (catch Exception e
                                    (throw (ex-info "Error macroexpanding form" (macroexpansion-error-data ns form e)))))
            kind (inst-forms/expanded-form-type macro-expanded-form {:compiler :clj})]
        (not (contains? #{:defn :defmethod :extend-type :extend-protocol :def} kind)))))

(defn expanded-defn-parse

  "Given a `ns-name` and a `expanded-defn-form` (macroexpanded) returns
  [fn-name-symbol fn-body]."

  [ns-name expanded-defn-form]

  (let [[_ var-name var-val] expanded-defn-form
        var-symb (symbol ns-name (str var-name))]
    [(find-var var-symb) var-val]))

(defn eval-form-error-data [_ ex]
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
       :msg "Instrumented expression is too large for the clojure compiler"
       :retry-disabling #{:expr}}

      :else
      (binding [*print-meta* true]
        #_(utils/log-error (format "Evaluating form %s Msg: %s Cause : %s" (pr-str inst-form) (.getMessage ex) (.getMessage (.getCause ex))) ex)
        #_(System/exit 1)
        {:type :unknown-error :msg e-msg}))))

(defn instrument-and-eval-form

  "Instrument `form` and evaluates it under `ns`."

  ([ns form config] (instrument-and-eval-form ns form config false))

  ([ns form config retrying?]

   (let [inst-form (try
                     (inst-forms/instrument (assoc config :ns (str (ns-name ns)))
                                            form)
                     (catch Exception e
                       (.printStackTrace e)
                       (throw (ex-info "Error instrumenting form" {:type :unknown-error
                                                                   :msg (.getMessage e)}))))]

     (try
       (cond

         ;; if it is a defn, we swap the var fn*, so we keep the original meta
         (inst-forms/expanded-defn-form? inst-form)
         (let [[v vval] (expanded-defn-parse (str (ns-name ns)) inst-form)]
           (alter-var-root v (fn [_] (eval vval))))

         ;; for defs that aren't fn* we still want to evaluate them since they are maybe
         ;; defining a instrumented function, and we want to update it
         (inst-forms/expanded-def-form? inst-form)
         (eval form)

         ;; here we asume interesting forms kind like :defmethod, :extend-type, etc
         :else
         (eval inst-form))
       (catch Exception e

         (let [{:keys [msg retry-disabling] :as error-data} (eval-form-error-data inst-form e)]
           (if (and (not retrying?) retry-disabling)
             (do
               (when (:verbose? config)
                 (log (utils/colored-string (format "\n\nKnown error %s, retrying disabling %s for this form\n\n" msg retry-disabling)
                                            :yellow)))
               (instrument-and-eval-form ns form (assoc config :disable retry-disabling) true))
             (throw (ex-info "Error evaluating form" error-data)))))))))

(defn- instrument-and-eval-file-forms

  "Instrument and evaluates all forms in `file-url`"

  [ns-symb file-url {:keys [verbose?] :as config}]
  ;; this is IMPORTANT, once we have `ns-symb` all the instrumentation work
  ;; should be done as if we where in `ns-symb`
  (binding [*ns* (find-ns ns-symb)]
    (when-not (= ns-symb 'clojure.core) ;; we don't want to instrument clojure core since it brings too much noise
      (let [ns (find-ns ns-symb)
            file-forms (read-string {:read-cond :allow}
                                    (format "[%s]" (slurp file-url)))]
        (log (format "\nInstrumenting namespace: %s Forms (%d) (%s)" ns-symb (count file-forms) (.getFile file-url)))

        (doseq [form file-forms]
          (try

            (if (uninteresting-form? ns form)

              (print ".")

              (do
                (instrument-and-eval-form ns form config)
                (print "I")))

            (catch clojure.lang.ExceptionInfo ei
              (let [e-data (ex-data ei)
                    ex-type (:type e-data)
                    ex-type-color (case ex-type
                                    :known-error   :yellow
                                    :unknown-error :red)]
                (if verbose?
                  (do
                    (println)
                    (print (utils/colored-string (str (ex-message ei) " " e-data) ex-type-color))
                    (println))

                  ;; else, quiet mode
                  (print (utils/colored-string "X" ex-type-color)))))))
        (println)))))

(defn- re-eval-file-forms [ns-symb file-url]
  (binding [*ns* (find-ns ns-symb)]
    (let [file-forms (read-string {:read-cond :allow}
                                  (format "[%s]" (slurp file-url)))]
      (log (format "\nUninstrumenting namespace: %s Forms (%d) (%s)" ns-symb (count file-forms) (.getFile file-url)))

      (doseq [form file-forms]
        (try
          (let [mexpanded-form (inst-forms/macroexpand-all macroexpand-1
                                                           (fn [symb] (symbol (resolve symb)))
                                                           form
                                                           ::original-form)]

            (if (inst-forms/expanded-defn-form? mexpanded-form)

              ;; if it is a defn, we swap the var fn*, so we keep the original meta
              (let [[v vval] (expanded-defn-parse (str (ns-name ns-symb)) mexpanded-form)]
                (alter-var-root v (fn [_] (eval vval))))

              ;; else just eval the form

              (eval form)))
          (catch Exception e
            (println)
            (println (utils/colored-string
                      (format "Error uninstrumenting form %s" form)
                      :red))
            (println (utils/colored-string
                      (.getMessage e)
                      :red))
            (println))))
      (println))))

(defn instrument-files-for-namespaces

  "Instrument and evaluates all forms of all loaded namespaces matching
  `ns-strs`.
  If `prefixes?` is true, `ns-strs` will be used as prefixes, else a exact match will be required."

  [ns-strs {:keys [prefixes?] :as config}]

  (let [{:keys [excluding-ns] :as config} (-> config
                                              (update :excluding-ns #(or % #{}))
                                              (update :disable #(or % #{})))
        ns-set (all-namespaces ns-strs config)

        files-set (interesting-files-for-namespaces ns-set)
        ns-pred (fn [nsname]
                  (and (not (excluding-ns nsname))
                       (some (fn [ns-str]
                               (if prefixes?
                                 (str/starts-with? nsname ns-str)
                                 (= nsname ns-str)))
                             ns-strs)))
        all-ns-info (reduce (fn [r f]
                              (let [ns-decl-form (read-file-ns-decl f)
                                    ns-name (tools-ns-parse/name-from-ns-decl ns-decl-form)
                                    deps (tools-ns-parse/deps-from-ns-decl ns-decl-form)]
                                (if ns-name
                                  (assoc r ns-name {:file f :deps (filter ns-pred deps)})
                                  r)))
                            {}
                            files-set)
        ns-graph (reduce (fn [g [ns-name {:keys [deps]}]]
                           (reduce (fn [gg dep-ns-name]
                                     (tools-ns-deps/depend gg ns-name dep-ns-name))
                                   g
                                   deps))
                         (tools-ns-deps/graph)
                         all-ns-info)
        ;; all files that have dependencies between eachother that need to be
        ;; processed in topological order
        dependent-files-vec (->> (tools-ns-deps/topo-sort ns-graph)
                              (keep (fn [ns-symb]
                                      (when-let [file (get-in all-ns-info [ns-symb :file])]
                                        [ns-symb file])))
                              (into []))
        all-files-set (into #{} (map (fn [[ns-name {:keys [file]}]] [ns-name file]) all-ns-info))
        independent-files (set/difference all-files-set
                                          (into #{} dependent-files-vec))

        ;; vector of all files to be instrumented, sorted in dependency topological order
        to-instrument-vec (into dependent-files-vec independent-files)
        affected-namespaces (->> to-instrument-vec (map first) (into #{}))]

    #_(log (format "Dependent files in order : %s" dependent-files-vec))
    #_(log (format "Independent files : %s" independent-files))
    #_(log (format "To instrument : %s" to-instrument-vec))

    (if (:uninstrument? config)

      ;; uninstrument - re evaluate all file forms in reverse order
      (doseq [[ns-symb file] (reverse to-instrument-vec)]
        (re-eval-file-forms ns-symb file))

      ;; instrument - evaluate all file instrumented forms
      (doseq [[ns-symb file] to-instrument-vec]
        (instrument-and-eval-file-forms ns-symb file config)))

    affected-namespaces))
