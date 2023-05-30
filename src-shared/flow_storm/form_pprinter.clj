(ns flow-storm.form-pprinter
  (:require [clojure.pprint :as pp]
            [flow-storm.utils :as utils]))

(defn- seq-delims [form]
  (let [delims (pr-str (empty form))]
    (if (= (count delims) 2)
      [(str (first delims)) (str (second delims))]
      ["#{" "}"])))

(defn- form-tokens [form]
  (let [curr-coord (::coord (meta form))]
    (cond
      (or (seq? form) (vector? form) (set? form))
      (let [[db de] (seq-delims form)]
        (-> [[db curr-coord]]
            (into (mapcat (fn [f] (form-tokens f)) form))
            (into [[de curr-coord]])))

      (map? form)
      (let [keys-vals (mapcat identity form)
            keys-vals-tokens (mapcat (fn [f] (form-tokens f))
                                     keys-vals)]
        (-> [["{" curr-coord]]
            (into keys-vals-tokens)
            (into [["}" curr-coord]])))

      :else
      [[(pr-str form) curr-coord]])))

(defn- consecutive-inv-chars [inv-chars-map idx]
  (loop [i (inc idx)
         inv-chars [(inv-chars-map idx)]]
    (if-let [inv-char (inv-chars-map i)]
      (recur (inc i) (conj inv-chars inv-char))
      inv-chars)))

(def hacked-code-table
  (#'pp/two-forms
     (#'pp/add-core-ns
        {'def #'pp/pprint-hold-first, 'defonce #'pp/pprint-hold-first,
         'defn #'pp/pprint-defn, 'defn- #'pp/pprint-defn, 'defmacro #'pp/pprint-defn, 'fn #'pp/pprint-defn,
         'let #'pp/pprint-let, 'loop #'pp/pprint-let, 'binding #'pp/pprint-let,
         'with-local-vars #'pp/pprint-let, 'with-open #'pp/pprint-let, 'when-let #'pp/pprint-let,
         'if-let #'pp/pprint-let, 'doseq #'pp/pprint-let, 'dotimes #'pp/pprint-let,
         'when-first #'pp/pprint-let,
         'if #'pp/pprint-if, 'if-not #'pp/pprint-if, 'when #'pp/pprint-if, 'when-not #'pp/pprint-if,
         'cond #'pp/pprint-cond, 'condp #'pp/pprint-condp,

         'fn* #'pp/pprint-simple-code-list, ;; <--- all for changing this from `pp/pprint-anon-func` to `pp/pprint-simple-code-list`
         ;;      so it doesn't substitute anonymous functions
         '. #'pp/pprint-hold-first, '.. #'pp/pprint-hold-first, '-> #'pp/pprint-hold-first,
         'locking #'pp/pprint-hold-first, 'struct #'pp/pprint-hold-first,
         'struct-map #'pp/pprint-hold-first, 'ns #'pp/pprint-ns
         })))

(defn code-pprint [form]
  ;; Had to hack pprint like this because code pprinting replace (fn [arg#] ... arg# ...) with #(... % ...)
  ;; and #' with var, deref with @ etc, wich breaks our pprintln system
  ;; This is super hacky! because I wasn't able to use with-redefs (it didn't work) I replace
  ;; the pprint method for ISeqs for the duration of our printing

  (#'pp/use-method pp/code-dispatch clojure.lang.ISeq (fn [alis] ;; <---- this hack disables reader macro sustitution
                                                        (if-let [special-form (hacked-code-table (first alis))]
                                                          (special-form alis)
                                                          (#'pp/pprint-simple-code-list alis))))

  (binding [pp/*print-pprint-dispatch* pp/code-dispatch
            pp/*code-table* hacked-code-table]
    (let [pprinted-form-str (utils/normalize-newlines
                             (with-out-str
                               (pp/pprint form)))]

      ;; restore the original pprint so we don't break it
      (#'pp/use-method pp/code-dispatch clojure.lang.ISeq #'pp/pprint-code-list)

      pprinted-form-str)))

(defn pprint-tokens [form]
  (let [form (utils/tag-form-recursively form ::coord)
        pprinted-str (code-pprint form)
        pos->layout-char (->> pprinted-str
                              (keep-indexed (fn [i c]
                                              (cond
                                                (= c \newline) [i :nl]
                                                (= c \space)   [i :sp]
                                                (= c \,)       [i :sp]
                                                :else nil)))
                              (into {}))
        pre-tokens (form-tokens form)
        final-tokens (loop [[[tname :as tok] & next-tokens] pre-tokens
                            i 0
                            final-toks []]
                       (if-not tok
                         final-toks
                         (if (pos->layout-char i)
                           (let [consec-inv-chars (consecutive-inv-chars pos->layout-char i)]
                             (recur next-tokens
                                    (+ i (count tname) (count consec-inv-chars))
                                    (-> final-toks
                                        (into consec-inv-chars)
                                        (into  [tok]))))
                           (recur next-tokens (+ i (count tname)) (into final-toks [tok])))))]
    final-tokens))

(defn- debug-print-tokens [ptokens]
  (doseq [t ptokens]
    (cond
      (= :sp t) (print " ")
      (= :nl t) (println)
      :else     (print (first t))))
  (println))

(defn pprint-form-hl-coord [form c]
  (let [tokens (pprint-tokens form)]
    (doseq [tok tokens]
      (let [txt (case tok
                  :sp " "
                  :nl "\n"
                  (let [[txt coord] tok]
                    (if (= coord c)
                      (utils/colored-string txt :red)
                      txt)))]
        (print txt)))))

(comment

  (let [test-form '(defn factorial [n] (if (zero? n) 1 (* n (factorial (dec n)))))]
    (binding [pp/*print-right-margin* 40
              pp/*print-pprint-dispatch* pp/code-dispatch]
      (= (-> test-form
             (pprint-tokens)
             debug-print-tokens
             with-out-str)
         (-> test-form
             pp/pprint
             with-out-str))))

  (def test-form '(defn clojurescript-version
                    "Returns clojurescript version as a printable string."
                    []
                    (fn* [p1__12449#] (+ 1 p1__12449#))
                    (if (bound? #'*clojurescript-version*)
                      (str
                       (:major *clojurescript-version*)
                       "."
                       (:minor *clojurescript-version*)
                       (when-let [i (:incremental *clojurescript-version*)]
                         (str "." i))
                       (when-let [q (:qualifier *clojurescript-version*)]
                         (str "." q))
                       (when (:interim *clojurescript-version*)
                         "-SNAPSHOT"))
                      @synthetic-clojurescript-version)))

  (binding [pp/*print-right-margin* 80
              pp/*print-pprint-dispatch* pp/code-dispatch]
      (-> test-form
          (pprint-tokens)
          debug-print-tokens))

  )
