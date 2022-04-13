(ns flow-storm.instrument.forms

  "This namespace started as a fork of cider.instrument but
  departed a lot from it to make it work for clojurescript and
  to make it able to trace more stuff.

  Provides utilities to recursively instrument forms for all our traces."

  (:require
   [clojure.walk :as walk]
   #_[cljs.analyzer :as ana]
   [clojure.string :as str]))


(declare instrument-outer-form)
(declare instrument-coll)
(declare instrument-special-form)
(declare instrument-function-call)
(declare instrument-case-map)

(def orphan-flow-id -1)
;;;;;;;;;;;;;;;;;;;;
;; Some utilities ;;
;;;;;;;;;;;;;;;;;;;;

(defn merge-meta

  "Non-throwing version of (vary-meta obj merge metamap-1 metamap-2 ...).
  Like `vary-meta`, this only applies to immutable objects. For
  instance, this function does nothing on atoms, because the metadata
  of an `atom` is part of the atom itself and can only be changed
  destructively."

  {:style/indent 1}
  [obj & metamaps]
  (try
    (apply vary-meta obj merge metamaps)
    (catch Exception _ obj)))

(defn strip-meta

  "Strip meta from form.
  If keys are provided, strip only those keys."

  ([form] (strip-meta form nil))
  ([form keys]
   (if (and (instance? clojure.lang.IObj form)
            (meta form))
     (if keys
       (with-meta form (apply dissoc (meta form) keys))
       (with-meta form nil))
     form)))

(defn macroexpand+

  "A macroexpand version that support custom `macroexpand-1-fn`"

  [macroexpand-1-fn form]

  (let [ex (if (seq? form)
             (macroexpand-1-fn form)
             form)]
    (if (identical? ex form)
      form
      (macroexpand+ macroexpand-1-fn ex))))

(defn macroexpand-all

  "Like `clojure.walk/macroexpand-all`, but preserves and macroexpands
  metadata. Also store the original form (unexpanded and stripped of
  metadata) in the metadata of the expanded form under original-key."

  [macroexpand-1-fn form & [original-key]]

  (let [md (meta form)
        expanded (walk/walk #(macroexpand-all macroexpand-1-fn % original-key)
                            identity
                            (if (seq? form)
                              ;; Without this, `macroexpand-all`
                              ;; throws if called on `defrecords`.
                              (try (let [r (macroexpand+ macroexpand-1-fn form)]
                                     r)
                                   (catch ClassNotFoundException _ form))
                              form))]
    (if md
      ;; Macroexpand the metadata too, because sometimes metadata
      ;; contains, for example, functions. This is the case for
      ;; deftest forms.
      (merge-meta expanded
        (macroexpand-all macroexpand-1-fn md)
        (when original-key
          ;; We have to quote this, or it will get evaluated by
          ;; Clojure (even though it's inside meta).
          {original-key (list 'quote (strip-meta form))}))

      expanded)))

;;;;;;;;;;;;;;;;;;;;;
;; Instrumentation ;;
;;;;;;;;;;;;;;;;;;;;;

;;; The following code is responsible for automatic instrumentation.
;;; This involves:
;;;    - knowing what's interesting and what's not,
;;;    - walking though the code,
;;;    - distinguishing function calls from special-forms,
;;;    - distinguishing between the different collections.

;;;; ## Auxiliary defs
(def dont-break-forms
  "Set of special-forms that we don't wrap breakpoints around.
  These are either forms that don't do anything interesting (like
  `quote`) or forms that just can't be wrapped (like `catch` and
  `finally`)."
  ;; `recur` needs to be handled separately.
  '#{quote catch finally})

;;;; ## Instrumentation
;;;
;;; The top-level instrumenting function is `instrument-tagged-code`. See
;;; its doc for more information.
;;;
;;; Each of the other `instrument-*` functions is responsible for
;;; calling subordinates and incrementing the coordinates vector if
;;; necessary.

;;;; ### Instrumenting Special forms
;;;
;;; Here, we implement instrumentation of special-forms on a
;;; case-by-case basis. Unlike function calls, we can't just look at
;;; each argument separately.
(declare instrument)

(defmacro definstrumenter

  "Defines a private function for instrumenting forms.
  This is like `defn-`, except the metadata of the return value is
  merged with that of the first input argument."

  [& args]

  (let [[_ name f] (macroexpand `(defn- ~@args))]
    `(def ~name
       (fn [& args#] (merge-meta (apply ~f args#) (meta (first args#)))))))

(definstrumenter instrument-coll
  "Instrument a collection."
  [coll ctx]
  (walk/walk #(instrument %1 ctx) identity coll))

(definstrumenter instrument-case-map
  "Instrument the map that is 5th arg in a `case*`."
  [args ctx]
  (into {} (map (fn [[k [v1 v2]]] [k [v1 (instrument v2 ctx)]])
                args)))

(defn- uninteresting-symb?

  "Return true if it is a uninteresting simbol,
  like core.async generated symbols, the _ symbol, etc."

  [symb]

  (let [symb-name (name symb)]
    (or (= symb-name "_")
        (= symb-name "&")

        ;; symbols generated by clojure destructure
        (str/includes? symb-name "__")

        ;; core async generated symbols
        (str/includes? symb-name "state_")
        (str/includes? symb-name "statearr-")
        (str/includes? symb-name "inst_"))))

(defn- bind-tracer

  "Generates a form to trace a `symb` binding at `coor`."

  [symb coor {:keys [on-bind-fn disable] :as ctx}]

  (when-not (or (disable :binding)
                (uninteresting-symb? symb))
    `(~on-bind-fn
      (quote ~symb)
      ~symb
      ~{:coor coor
        :form-id (:form-id ctx)})))

(defn- args-bind-tracers

  "Generates a collection of forms to trace `args-vec` symbols at `coor`.
  Used for tracing all arguments of a fn."

  [args-vec coor ctx]

  ;;TODO: maybe we can have a trace that send all bindings together, instead
  ;; of one binding trace per argument.

  (->> args-vec
       (keep (fn [symb] (bind-tracer symb coor ctx)))))

(defn lazy-seq-form? [form]
  (and (seq? form)
       (let [[a b] form]
         (and (= a 'new)
              (= b 'clojure.lang.LazySeq)))))

(defn- clear-fn-args-vec [args]
  (let [remove-&-symb (fn [args]
                        (into [] (keep #(when-not (= % '&) %) args)))
        remove-type-hint-tags (fn [args]
                                (mapv (fn [a]
                                        (if (contains? (meta a) :tag)
                                          (vary-meta a dissoc :tag)
                                          a))
                                      args))]
    (-> args
        remove-&-symb
        remove-type-hint-tags)))

(defn instrument-special-dot [args ctx]
  (list* (first args)
         ;; To handle the case when second argument to dot call
         ;; is a list e.g (. class-name (method-name args*))
         ;; The values present as args* should be instrumented.
         (let [s (second args)]
           (if (coll? s)
             (->> (instrument-coll (rest s) ctx)
                  (concat (cons (first s) '())))
             s))
         (instrument-coll (rest (rest args)) ctx)))

(defn instrument-special-def [orig-form args ctx]
  (let [[sym & rargs] args
        is-fn-def? (and (seq? (first rargs))
                        (= 'fn* (-> rargs first first)))
        ctx (cond-> ctx
              is-fn-def? (assoc :fn-ctx {:trace-name sym
                                         :kind :defn
                                         :orig-form orig-form}))]
    (list* (merge-meta sym
             ;; Instrument the metadata, because
             ;; that's where tests are stored.
             (instrument (or (meta sym) {}) ctx)
             ;; to be used later for meta stripping
             {::def-symbol true})

           (map (fn [arg] (instrument arg ctx)) rargs))))

(defn instrument-special-loop*-like

  "Trace lets and loops bindings right side recursively."

  [[name & args :as form] {:keys [disable] :as ctx}]

  (cons (->> (first args)
             (partition 2)
             (mapcat (fn [[symb x]]
                       (if (or (uninteresting-symb? symb)
                               (#{'loop*} name))
                         [symb (instrument x ctx)]

                         ;; if it is not a loop add more _ bindings
                         ;; that just trace the bound values
                         ;; like [a (+ 1 2)] will became
                         ;; [a (+ 1 2)
                         ;;  _ (bound-trace a ...)]
                         (cond-> [symb (instrument x ctx)]
                           ;; doesn't make sense to trace the bind if its a letfn* since
                           ;; they are just fns
                           (and (not (disable :binding))
                                (not= 'letfn* name))
                           (into ['_ (bind-tracer symb (-> form meta ::coor) ctx)])))))
             vec)
        (instrument-coll (rest args) ctx)))

(defn- instrument-fn-arity-body [form-coor [arity-args-vec & arity-body-forms :as arity] {:keys [fn-ctx outer-form-kind extend-ctx form-id form-ns on-outer-form-init-fn on-fn-call-fn disable excluding-fns] :as ctx}]
  (let [fn-trace-name (or (:trace-name fn-ctx) (gensym "fn-"))
        fn-form (cond
                  (= outer-form-kind :extend-protocol) (:orig-form extend-ctx)
                  (= outer-form-kind :extend-type) (:orig-form extend-ctx)
                  (= outer-form-kind :defrecord) (:orig-form extend-ctx)
                  (= outer-form-kind :deftype) (:orig-form extend-ctx)
                  (= (:kind fn-ctx) :reify) (:orig-form fn-ctx)
                  (= (:kind fn-ctx) :defmethod) (:orig-form fn-ctx)
                  (= (:kind fn-ctx) :defn) (:orig-form fn-ctx)
                  (= (:kind fn-ctx) :anonymous) (:orig-form fn-ctx)
                  :else (throw (Exception. (format "Don't know how to handle functions of this type. %s %s %s" fn-ctx outer-form-kind extend-ctx))))
        outer-preamble (-> []
                           (into [`(~on-outer-form-init-fn {:form-id ~form-id
                                                            :ns ~form-ns
                                                            :def-kind ~(cond
                                                                         (= outer-form-kind :extend-protocol) :extend-protocol
                                                                         (= outer-form-kind :extend-type) :extend-type
                                                                         (= outer-form-kind :defrecord) :extend-type
                                                                         (= outer-form-kind :deftype) :extend-type
                                                                         (= (:kind fn-ctx) :defmethod) :defmethod
                                                                         (= (:kind fn-ctx) :defn) :defn)
                                                            :dispatch-val ~(:dispatch-val fn-ctx)}
                                    ~fn-form)])
                           (into [`(~on-fn-call-fn ~form-id ~form-ns ~(str fn-trace-name) ~(clear-fn-args-vec arity-args-vec))])
                           (into (args-bind-tracers arity-args-vec form-coor ctx)))

        ctx' (-> ctx
                 (dissoc :fn-ctx)) ;; remove :fn-ctx so fn* down the road instrument as anonymous fns

        lazy-seq-fn? (lazy-seq-form? (first arity-body-forms))

        inst-arity-body-form (if (or (and (disable :anonymous-fn) (= :anonymous (:kind fn-ctx))) ;; don't instrument anonymous-fn if they are disabled
                                     (and (#{:deftype :defrecord} outer-form-kind)
                                          (or (str/starts-with? (str fn-trace-name) "->")
                                              (str/starts-with? (str fn-trace-name) "map->"))) ;; don't instrument record constructors
                                     (excluding-fns (symbol form-ns (str fn-trace-name))) ;; don't instrument if in excluding-fn
                                     lazy-seq-fn?) ;; don't instrument if lazy-seq-fn
                               ;; NOTE: on skipping instrumentation for lazy-seq fns
                               ;;
                               ;; if the fn* returns a lazy-seq we can't wrap it or we will generate a
                               ;; recursion and we risk getting a StackOverflow.
                               ;; If we can't wrap the output we can't wrap the call neither since
                               ;; they are sinchronized, so we skip this fn* body tracing
                               `(do ~@arity-body-forms)

                               (instrument-outer-form ctx'
                                                      (instrument-coll arity-body-forms ctx')
                                                      outer-preamble))]
    (-> `(~arity-args-vec ~inst-arity-body-form)
        (merge-meta (meta arity)))))

(defn instrument-special-fn* [[_ & args :as form] ctx]
  (let [[a1 & a1r] args
        orig-form (::original-form (meta form))
        form-coor (-> form meta ::coor)
        [fn-name arities-bodies-seq] (cond

                                       ;; named fn like (fn* fn-name ([] ...) ([p1] ...))
                                       (symbol? a1)
                                       [a1 a1r]

                                       ;; anonymous fn like (fn* [] ...), comes from expanding #( % )
                                       (vector? a1)
                                       [nil [`(~a1 ~@a1r)]]

                                       ;; anonymous fn like (fn* ([] ...) ([p1] ...))
                                       :else
                                       [nil args])
        ctx (cond-> ctx
              (nil? (:fn-ctx ctx)) (assoc :fn-ctx (or (:fn-ctx ctx)
                                                      {:trace-name fn-name
                                                       :kind :anonymous
                                                       :orig-form orig-form})))
        instrumented-arities-bodies (map #(instrument-fn-arity-body form-coor % ctx) arities-bodies-seq)]

    (if (nil? fn-name)
      `(~@instrumented-arities-bodies)
      `(~fn-name ~@instrumented-arities-bodies))))

(defn instrument-special-case* [args ctx]
  (case (:compiler ctx)
    :clj (let [[a1 a2 a3 a4 a5 & ar] args]
           ;; Anyone know what a2 and a3 represent? They were always 0 on my tests.
           `(~a1 ~a2 ~a3 ~(instrument a4 ctx) ~(instrument-case-map a5 ctx) ~@ar))
    :cljs (let [[a1 left-vec right-vec else] args]
            `(~a1 ~left-vec ~(instrument-coll right-vec ctx) ~(instrument else ctx)))))

(defn instrument-special-reify* [[proto-or-interface-vec & methods] orig-form ctx]
  (let [inst-methods (->> methods
                          (map (fn [[method-name args-vec & body :as form]]
                                 (let [form-coor (-> form meta ::coor)
                                       ctx (assoc ctx :fn-ctx {:trace-name method-name
                                                               :kind :reify
                                                               :orig-form orig-form})
                                       [_ inst-body] (instrument-fn-arity-body form-coor `(~args-vec ~@body) ctx)]
                                   `(~method-name ~args-vec ~inst-body)))))]
    `(~proto-or-interface-vec ~@inst-methods)))

(defn instrument-special-deftype* [[a1 a2 a3 a4 a5 & methods] {:keys [outer-form-kind deftype-ctx] :as ctx}]
  (let [inst-methods (->> methods
                          (map (fn [[method-name args-vec & body :as form]]
                                 (if (and (= outer-form-kind :defrecord)
                                          (= "clojure.core" (namespace method-name)))

                                   ;; don't instrument defrecord types
                                   `(~method-name ~args-vec ~@body)

                                   (let [form-coor (-> form meta ::coor)
                                         ctx (assoc ctx :fn-ctx {:trace-name method-name
                                                                 :kind :extend-type
                                                                 :orig-form (:orig-form deftype-ctx)})
                                         [_ inst-body] (instrument-fn-arity-body form-coor `(~args-vec ~@body) ctx)]
                                     `(~method-name ~args-vec ~inst-body))))))]
    `(~a1 ~a2 ~a3 ~a4 ~a5 ~@inst-methods)))

#_(map #(if (seq? %)
        (let [[a1 a2 & ar] %]
          (merge-meta (list* a1 a2 (instrument-coll ar ctx))
            (meta %)))
        %)
     args)
(definstrumenter instrument-special-form
  "Instrument form representing a macro call or special-form."
  [[name & args :as form] {:keys [orig-outer-form form-id form-ns on-outer-form-init-fn on-fn-call-fn disable excluding-fns] :as ctx}]
  (cons name
        ;; We're dealing with some low level stuff here, and some of
        ;; these internal forms are completely undocumented, so let's
        ;; play it safe and use a `try`.
        (try
          (condp #(%1 %2) name
            '#{do if recur throw finally try monitor-exit monitor-enter} (instrument-coll args ctx)
            '#{new} (cons (first args) (instrument-coll (rest args) ctx))
            '#{quote & var clojure.core/import*} args
            '#{.} (instrument-special-dot args ctx)
            '#{def} (instrument-special-def (::original-form (meta form)) args ctx)
            '#{set!} (list (first args)
                           (instrument (second args) ctx))
            '#{loop* let* letfn*} (instrument-special-loop*-like form ctx)
            '#{deftype*} (instrument-special-deftype* args ctx)
            '#{reify*} (instrument-special-reify* args (::original-form (meta form)) ctx)
            '#{fn*} (instrument-special-fn* form ctx)
            '#{catch} `(~@(take 2 args)
                        ~@(instrument-coll (drop 2 args) ctx))
            '#{case*} (instrument-special-case* args ctx))
          (catch Exception e
            (binding [*out* *err*]
              (println "Failed to instrument" name args (pr-str form)
                       ", please file a bug report: " e))
            args))))

;;;; ### Instrumenting Functions and Collections
;;;
;;; This part is quite simple, most of the code is devoted to checking
;;; form-types and special cases. The idea here is that we walk
;;; through collections and function arguments looking for interesting
;;; things around which we'll wrap a breakpoint. Interesting things
;;; are most function-forms and vars.
(definstrumenter instrument-function-call
  "Instrument a regular function call sexp.
  This must be a sexp that starts with a symbol which is not a macro
  nor a special form.
  This includes regular function forms, like `(range 10)`, and also
  includes calls to Java methods, like `(System/currentTimeMillis)`."
  [[name & args :as fncall] ctx]
  (cons name (instrument-coll args ctx)))

(defn- instrument-form [form coor {:keys [on-expr-exec-fn form-id outer-form? disable]}]
  ;; only disable :fn-call traces if it is not the outer form, we still want to
  ;; trace it since its the function return trace
  (if (and (disable :expr) (not outer-form?))

    form

    (let [trace-data (cond-> {:coor coor, :form-id form-id}
                       outer-form? (assoc :outer-form? outer-form?))]
      `(~on-expr-exec-fn ~form nil ~trace-data))))

(defn- maybe-instrument
  "If the form has been tagged with ::coor on its meta, then instrument it
  with trace-and-return"
  ([form ctx]
   (let [{coor ::coor} (meta form)]
     (cond
       coor
       (instrument-form form coor ctx)

       ;; If the form is a list and has no metadata, maybe it was
       ;; destroyed by a macro. Try guessing the extras by looking at
       ;; the first element. This fixes `->`, for instance.
       (seq? form)
       (let [{coor ::coor} (meta (first form))
             ;; coor (if (= (last extras) 0)
             ;;          (pop extras)
             ;;          extras)
             ]
         (if coor
           (instrument-form form coor ctx)
           form))
       :else form))))

(defn- contains-recur?
  "Return true if form is not a `loop` or a `fn` and a `recur` is found in it."
  [form]
  (cond
    (seq? form) (case (first form)
                  recur true
                  loop* false
                  fn*   false
                  (some contains-recur? (rest form)))
    ;; `case` expands the non-default branches into a map.
    ;; We therefore expect a `recur` to appear in a map only
    ;; as the result of a `case` expansion.
    ;; This depends on Clojure implementation details.
    (map? form) (some contains-recur? (vals form))
    (vector? form) (some contains-recur? (seq form))
    :else false))

(defn- dont-break?
  "Return true if it's NOT ok to wrap form in a breakpoint.
  Expressions we don't want to wrap are those listed in
  `dont-break-forms` and anything containing a `recur`
  form (unless it's inside a `loop`)."
  [[name :as form]]
  (or (dont-break-forms name)
      (contains-recur? form)))

#_(defn cljs-multi-arity-defn? [[x1 & x2]]
  (when (= x1 'do)
    (let [[xdef & xset] (keep first x2)]
      (and (= xdef 'def)
           (pos? (count xset))
           (every? #(= 'set! %) xset)))))

(defn expanded-def-form? [form]
  (and (seq? form)
       (= (first form) 'def)))

(defn expanded-defmethod-form? [form {:keys [compiler]}]
  (and (seq? form)
       (or (and (= compiler :clj)
            (= (count form) 5)
            (= (nth form 2) 'clojure.core/addMethod))
       (and (= compiler :cljs)
            (= (count form) 4)
            (= (first form) 'cljs.core/-add-method)))))

(defn expanded-defn-form? [form]
  (and (= (count form) 3)
       (= 'def (first form))
       (let [[_ _ x] form]
         (and (seq? x)
              (= (first x) 'fn*)))))

(defn- expanded-clojure-core-extend-form? [[symb] _]
  (= symb 'clojure.core/extend))

(defn- expanded-deftype-form [form _]
  (cond

    (and (> (count form) 5)
         (let [x (nth form 4)]
           (and (seq? x) (= (first x) 'deftype*))))
    :defrecord

    (and (seq? form)
         (>= (count form) 3)
         (let [x (nth form 2)]
           (and (seq? x) (= (first x) 'deftype*))))
    :deftype

    :else nil))

(defn- expanded-extend-protocol-form? [form _]
  (and (seq? form)
       (= 'do (first form))
       (seq? (second form))
       (= 'clojure.core/extend (-> form second first))))

(defn expanded-form-type [form ctx]
  (when (seq? form)
    (cond

      (expanded-defn-form? form) :defn ;; this covers (defn foo [] ...), (def foo (fn [] ...)), and multy arities
      (expanded-defmethod-form? form ctx) :defmethod
      (or (expanded-clojure-core-extend-form? form ctx)
          (expanded-deftype-form form ctx)) :extend-type
      (expanded-extend-protocol-form? form ctx) :extend-protocol

      ;; (and (= compiler :cljs)
      ;;      (cljs-multi-arity-defn? form))
      ;; :defn-cljs-multi-arity

)))


(defn maybe-unwrap-outer-form-instrumentation [inst-form _]
  (if (and (seq? inst-form)
           (= 'flow-storm.tracer/trace-expr-exec-trace (first inst-form)))

    ;; discard the on-expr-exec-fn
    (second inst-form)

    ;; else do nothing
    inst-form))

(defn- instrument-core-extend-form [[_ ext-type & exts] ctx]
  ;; We need special instrumentation for core/extend (extend-protocol and extend-type)
  ;; so we can trace fn-name, and trace that each fn is a protocol/type fn*
  (let [inst-ext (fn [[etype emap]]
                   (let [inst-emap (reduce-kv
                                    (fn [r k f]
                                      ;; @@@HACKY This ' is needed in `fn-name` because it will end up
                                      ;; in (fn* fn-name ([])) functions entry of extend-type after instrumenting
                                      ;; because of how fn-name thing is currently designed
                                      ;; This if we use the same name as the type key there it will compile
                                      ;; but will cause problems in situations when recursion is used
                                      ;; `fn-name` will be used only for reporting purposes, so there is no harm
                                      (let [fn-name (symbol (format "%s" (name k)))]
                                        (assoc r k (instrument f
                                                               (assoc ctx :fn-ctx {:trace-name fn-name
                                                                                   :kind :extend-type
                                                                                   :orig-form (-> ctx :extend-ctx :orig-form)})))))
                                    {}
                                    emap)]
                     (list etype inst-emap)))
        extensions (->> (partition 2 exts)
                        (mapcat inst-ext))]
   `(clojure.core/extend ~ext-type ~@extensions)))

(defn- instrument-clojure-defmethod-form [[_ mname _ mdisp-val mfn] ctx]
  (let [inst-mfn (instrument mfn ctx)]
    `(. ~mname clojure.core/addMethod ~mdisp-val ~inst-mfn)))

(defn- update-context-for-top-level-form

  "Set the context for instrumenting fn*s down the road."

  [ctx form]

  (cond

    (expanded-defmethod-form? form ctx)
    (assoc ctx
           :fn-ctx {:trace-name (nth form 1)
                    :kind :defmethod
                    :dispatch-val (pr-str (nth form 3))
                    :orig-form (::original-form (meta form))})

    (expanded-extend-protocol-form? form ctx)
    (assoc ctx
           :outer-form-kind :extend-protocol
           :extend-ctx {:orig-form (::original-form (meta form))})

    (expanded-clojure-core-extend-form? form ctx)
    (assoc ctx
           :outer-form-kind :extend-type
           :extend-ctx {:orig-form (::original-form (meta form))})

    (expanded-deftype-form form ctx)
    (assoc ctx
           :outer-form-kind (expanded-deftype-form form ctx)
           :extend-ctx {:orig-form (::original-form (meta form))})

    :else
    ctx))

(defn- instrument-function-like-form
  "Instrument form representing a function call or special-form."
  [[name :as form] ctx]
  (if-not (symbol? name)
    ;; If the car is not a symbol, nothing fancy is going on and we
    ;; can instrument everything.
    (maybe-instrument (instrument-coll form ctx) ctx)

    (cond
      ;; If special form, thread with care.
      (special-symbol? name)
      (if (dont-break? form)
        (instrument-special-form form ctx)
        (maybe-instrument (instrument-special-form form ctx) ctx))

      ;; Otherwise, probably just a function. Just leave the
      ;; function name and instrument the args.
      :else
      (maybe-instrument (instrument-function-call form ctx) ctx))))

(defn- instrument
  "Walk through form and return it instrumented with traces. "
  [form ctx]
  (condp #(%1 %2) form
    ;; Function call, macro call, or special form.
    seq? (doall (instrument-function-like-form form ctx))
    symbol? (maybe-instrument form ctx)
    ;; Other coll types are safe, so we go inside them and only
    ;; instrument what's interesting.
    ;; Do we also need to check for seq?
    coll? (doall (instrument-coll form ctx))
    ;; Other things are uninteresting, literals or unreadable objects.
    form))

(defn- walk-indexed
  "Walk through form calling (f coor element).
  The value of coor is a vector of indices representing element's
  address in the form. Unlike `clojure.walk/walk`, all metadata of
  objects in the form is preserved."
  ([f form] (walk-indexed [] f form))
  ([coor f form]
   (let [map-inner (fn [forms]
                     (map-indexed #(walk-indexed (conj coor %1) f %2)
                                  forms))
         ;; Clojure uses array-maps up to some map size (8 currently).
         ;; So for small maps we take advantage of that, otherwise fall
         ;; back to the heuristic below.
         ;; Maps are unordered, but we can try to use the keys as order
         ;; hoping they can be compared one by one and that the user
         ;; has specified them in that order. If that fails we don't
         ;; instrument the map. We also don't instrument sets.
         ;; This depends on Clojure implementation details.
         walk-indexed-map (fn [map]
                            (map-indexed (fn [i [k v]]
                                           [(walk-indexed (conj coor (* 2 i)) f k)
                                            (walk-indexed (conj coor (inc (* 2 i))) f v)])
                                         map))
         result (cond
                  (map? form) (if (<= (count form) 8)
                                (into {} (walk-indexed-map form))
                                (try
                                  (into (sorted-map) (walk-indexed-map (into (sorted-map) form)))
                                  (catch Exception _
                                    form)))
                  ;; Order of sets is unpredictable, unfortunately.
                  (set? form)  form
                  ;; Borrowed from clojure.walk/walk
                  (list? form) (apply list (map-inner form))
                  (instance? clojure.lang.IMapEntry form) (vec (map-inner form))
                  (seq? form)  (doall (map-inner form))
                  (coll? form) (into (empty form) (map-inner form))
                  :else form)]
     (f coor (merge-meta result (meta form))))))

(defn tag-form
  [coor form]
  (merge-meta form {::coor coor}))

(defn tag-form-recursively
  "Like `tag-form` but also tag all forms inside the given form."
  [form]
  ;; Don't use `postwalk` because it destroys previous metadata.
  (walk-indexed tag-form form))

(defn- strip-instrumentation-meta
  "Remove all tags in order to reduce java bytecode size and enjoy cleaner code
  printouts."
  [form]
  (walk-indexed
   (fn [_ f]
     (if (instance? clojure.lang.IObj f)
       (let [keys [::original-form ::coor ::def-symbol]
             f    #_(if (::def-symbol (meta f)) ;; TODO: figure this out
                    (let [br (::breakfunction (meta f))
                          f1 (strip-meta f keys)]
                      (if br
                        (vary-meta f1 assoc :cider/instrumented (name (:name (meta br))))
                        f1))
                    (strip-meta f keys))
             (strip-meta f keys)]
         ;; also strip meta of the meta
         (with-meta f (strip-instrumentation-meta (meta f))))
       f))
   form))

(defn- instrument-start

  "Like instrument but meant to be used around outer forms, not in recursions
  since it will do some checks that are only important in outer forms. "

  [form ctx]
  (cond
    (expanded-defmethod-form? form ctx)
    (instrument-clojure-defmethod-form form ctx)

    (expanded-clojure-core-extend-form? form ctx)
    (instrument-core-extend-form form ctx)

    (expanded-extend-protocol-form? form ctx)
    `(do ~@(map (fn [ext-form] (instrument-core-extend-form ext-form ctx)) (rest form)))

    :else (instrument form ctx)))

(defn instrument-tagged-code
  [form ctx]
  (let [updated-ctx (update-context-for-top-level-form ctx form)]
    (-> form
       ;; Go through everything again, and instrument any form with
       ;; debug metadata.
       (instrument-start updated-ctx)
       (strip-instrumentation-meta))))

(defn instrument-outer-form
  "Add some special instrumentation that is needed only on the outer form."
  [{:keys [on-flow-start-fn] :as ctx} forms preamble]
  `(let [curr-ctx# flow-storm.tracer/*runtime-ctx*]
     ;; @@@ Speed rebinding on every function call probably makes execution much slower
     ;; need to find a way of remove this, and maybe use ThreadLocals for *runtime-ctx* ?

     (binding [flow-storm.tracer/*runtime-ctx* (or curr-ctx# (flow-storm.tracer/empty-runtime-ctx orphan-flow-id))]
       ~@(-> preamble
             (into [(instrument-form (conj forms 'do) [] (assoc ctx :outer-form? true))])))))

;;;;;;;;;;;;;;;;
;; @@@ Hacky  ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Normal `clojure.core/macroexpand-1` works differently when being called from clojure and clojurescript. ;;
;; See: https://github.com/jpmonettas/clojurescript-macro-issue                                            ;;
;; One solution is to use clojure.core/macroexpand-1 when we are in a clojure environment                  ;;
;; and user cljs.analyzer/macroexpand-1 when we are in a clojurescript one.                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn instrument-all [form {:keys [compiler #_environment] :as ctx}]
  (let [macroexpand-1-fn (case compiler
                           ;; :cljs (partial ana/macroexpand-1 environment)
                           :clj  macroexpand-1)
        form-with-meta (with-meta form {::original-form form})
        tagged-form (tag-form-recursively form-with-meta) ;; tag all forms adding ::i/coor
        macro-expanded-form (macroexpand-all macroexpand-1-fn tagged-form ::original-form) ;; Expand so we don't have to deal with macros.
        inst-code (instrument-tagged-code macro-expanded-form ctx)]
    inst-code))

(defn build-form-instrumentation-ctx [{:keys [disable excluding-fns]} form-ns form env]
  (let [form-id (hash form)]
    (assert (or (nil? disable) (set? disable)) ":disable configuration should be a set")
    {:on-expr-exec-fn       'flow-storm.tracer/trace-expr-exec-trace
     :on-bind-fn            'flow-storm.tracer/trace-bound-trace
     :on-fn-call-fn         'flow-storm.tracer/trace-fn-call-trace
     :on-outer-form-init-fn 'flow-storm.tracer/trace-form-init-trace
     :on-flow-start-fn      'flow-storm.tracer/trace-flow-init-trace
     :environment      env
     :compiler         (if (contains? env :js-globals)
                         :cljs
                         :clj)
     :orig-outer-form  form
     :form-id          form-id
     :form-ns          form-ns
     :excluding-fns     (or excluding-fns #{})
     :disable          (or disable #{}) ;; :expr :binding :anonymous-fn
     }))
;; ClojureScript multi arity defn expansion is much more involved than
;; a clojure one
#_(do
 (def
  multi-arity-foo
  (cljs.core/fn
   [var_args]
   (cljs.core/case
    ...
    )))

 (set!
  (. multi-arity-foo -cljs$core$IFn$_invoke$arity$1)
  (fn* ([a] (multi-arity-foo a 10))))

 (set!
  (. multi-arity-foo -cljs$core$IFn$_invoke$arity$2)
  (fn* ([a b] (+ a b))))

 (set! (. multi-arity-foo -cljs$lang$maxFixedArity) 2)

 nil)

#_(defn instrument-cljs-multi-arity-outer-form [[_ xdef & xsets] orig-form ctx]
  (let [fn-name (second xdef)
        inst-sets-forms (keep (fn [[_ xarity fn-body]]
                           (when (and (seq? fn-body) (= (first fn-body) 'fn*))
                             (let [inst-bodies (instrument-function-bodies
                                                fn-body
                                                (assoc ctx
                                                       :orig-form orig-form
                                                       :fn-name (str fn-name))

                                                instrument-outer-form)]
                               (list 'set! xarity inst-bodies))))
                              xsets)
        inst-code `(do ~xdef ~@inst-sets-forms)]
    inst-code))

(defn parse-defn-expansion [defn-expanded-form]
  ;; (def my-fn (fn* ([])))
  (let [[_ var-name & fn-arities-bodies] defn-expanded-form]
    {:var-name var-name
     :fn-arities-bodies fn-arities-bodies}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; For working at the repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  )
