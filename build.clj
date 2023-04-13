(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [rewrite-clj.zip :as z]
            [clojure.spec.alpha :as s]))

(def flow-storm-ns-tag "FLOWNS")
(def class-dir "target/classes")

(defn clean [_]
  (b/delete {:path "target"}))

(defn- version-ns-symb [symb version kind]
  (case kind
    :require (symbol (format "%s-v%s-%s" (name symb) version flow-storm-ns-tag))
    :import  (symbol (format "%s_v%s_%s" (name symb) (str/replace version "-" "_") flow-storm-ns-tag))))

(defn- z-find-require [zloc]
  (-> zloc (z/find-next-value z/next :require) z/up))

(defn- z-find-import [zloc]
  (-> zloc (z/find-next-value z/next :import) z/up))

(defn- z-find-ns-name [zloc]
  (-> zloc z/next z/right))

(defn- z-version-ns-name [zloc version]
  (-> zloc
      z-find-ns-name
      (z/edit (fn [symb] (version-ns-symb symb version :require)))
      z/up))

(defn- z-version-namespaces [zloc ns-prefix version kind]
  ;; Assumes `zloc` is pointing to a (:require ...) or (:import ...) form
  (let [maybe-edit-ns-vec (fn [v]
                            (if (vector? v)

                              (if (str/starts-with? (-> v first str) ns-prefix)
                                (update v 0 (fn [symb]
                                              (version-ns-symb symb version kind)))
                                v)

                              (do
                                (println (format "[Versioning WARNING] %s is not a vector, skipping ..." v))
                                v)))]
    (loop [ns-vec-zloc (-> zloc z/down z/right)]
      (let [edited-ns-vec-zloc (z/edit ns-vec-zloc maybe-edit-ns-vec)
            next-ns-vec-zloc (z/right edited-ns-vec-zloc)]
        (if-not next-ns-vec-zloc
          (z/up edited-ns-vec-zloc)
          (recur next-ns-vec-zloc))))))

(defn- z-write-to-file
  "Assumes `zloc` is pointing the the ns form"
  [zloc file]
  (let [new-file-content (with-out-str
                           (z/print-root zloc))]
    (spit file new-file-content)))

(defn- inst-entry-point-file? [file]
  (str/ends-with? (.getAbsolutePath file) "flow_storm/api.clj"))

(defn- dbg-entry-point-file? [file]
  (str/ends-with? (.getAbsolutePath file) "flow_storm/debugger/main.clj"))

(defn- data-readers-file? [file]
  (str/ends-with? (.getAbsolutePath file) "data_readers.clj"))

(defn- z-version-require-namespaces [zloc version]
  (let [req-zloc (z-find-require zloc)]
    (if req-zloc
      (z/up (z-version-namespaces req-zloc "flow-storm." version :require))
      zloc)))

(defn- z-version-import-namespaces [zloc version]
  (let [imp-zloc (z-find-import zloc)]
    (if imp-zloc
      (z/up (z-version-namespaces imp-zloc "flow_storm." version :import))
      zloc)))

(defn- create-version-file [file version]
  (let [step0-zloc (z/of-file file)
        step1-zloc (z-version-ns-name step0-zloc version)
        step2-zloc (z-version-require-namespaces step1-zloc version)
        step3-zloc (z-version-import-namespaces step2-zloc version)

        step4-zloc (cond

                     (inst-entry-point-file? file) ;; kind of hacky, but the inst api needs a couple of defs to be modified
                     (-> step3-zloc
                         z/right ;; find (def debugger-trace-processor-ns 'flow-storm.debugger.trace-processor)
                         z/next z/next z/next z/down
                         (z/edit (fn [symb] (version-ns-symb symb version :require)))
                         z/up
                         z/up
                         z/right ;; find (def debugger-main-ns 'flow-storm.debugger.main)
                         z/next z/next z/next z/down
                         (z/edit (fn [symb] (version-ns-symb symb version :require)))
                         z/up
                         z/up
                         z/left
                         z/left) ;; go back to ns form

                     (dbg-entry-point-file? file) ;; kind of hacky, but the dbg main needs a def to be modified
                     (-> step3-zloc
                         z/right ;; find (def flow-storm-core-ns 'flow-storm.core)
                         z/next z/next z/next z/down
                         (z/edit (fn [symb] (version-ns-symb symb version :require)))
                         z/up
                         z/up
                         z/left)

                     :else step3-zloc)
        [_ file-name ext] (re-find #"(.+)\.([cljc]+)" (.getAbsolutePath file))
        versioned-file (format "%s_v%s_%s.%s" file-name (str/replace version "-" "_") flow-storm-ns-tag ext)]

    (z-write-to-file step4-zloc versioned-file)

    (io/delete-file file)

    versioned-file))

(defn- re-write-dir-for-version [class-dir version]
  (let [version (str/replace version "." "-")
        code-files (->> (file-seq (io/file class-dir))
                        (keep (fn [file]
                                (when (or (str/ends-with? (.getName file) ".clj")
                                          (str/ends-with? (.getName file) ".cljc"))
                                  file))))]
    (doseq [file code-files]
      (when-not (data-readers-file? file)

        (println "Processing " (.getName file) (.getAbsolutePath file))

        (let [versioned-file-path (create-version-file file version)]

          ;; if it is inst api.clj we need to create a api.clj for the users so they
          ;; don't have to require like (require [flow-storm.api-v2-0-37 ...])
          (when (inst-entry-point-file? file)
            (-> (z/of-file versioned-file-path)
                z-find-ns-name
                (z/edit (constantly 'flow-storm.api))
                (z-write-to-file file)))

          ;; same for the debugger entry ns
          (when (dbg-entry-point-file? file)
            (-> (z/of-file versioned-file-path)
                z-find-ns-name
                (z/edit (constantly 'flow-storm.debugger.main))
                (z-write-to-file file))))))))

(def aot-compile-nses

  "Precompile this so clojure storm can call flow-storm.storm-api functions
  at repl init and it doesn't slow down repl startup."

  ['flow-storm.storm-api
   'flow-storm.utils
   'flow-storm.api
   'flow-storm.runtime.debuggers-api
   'flow-storm.runtime.types.fn-call-trace
   'flow-storm.runtime.indexes.frame-index
   'flow-storm.runtime.indexes.api
   'flow-storm.runtime.indexes.protocols
   'flow-storm.runtime.types.fn-return-trace
   'flow-storm.runtime.events
   'flow-storm.runtime.indexes.fn-call-stats-index
   'flow-storm.runtime.indexes.thread-registry
   'flow-storm.runtime.indexes.form-registry
   'flow-storm.runtime.values
   'flow-storm.runtime.types.bind-trace
   'flow-storm.runtime.types.expr-trace
   'flow-storm.runtime.indexes.utils])

(defn jar-dbg [_]
  (clean nil)
  (let [lib 'com.github.jpmonettas/flow-storm-dbg
        ;;version (format "3.4.%s" (b/git-count-revs nil))
        version (format "3.4-beta-8" (b/git-count-revs nil))
        basis (b/create-basis {:project "deps.edn"
                               :aliases []})
        jar-file (format "target/%s.jar" (name lib))
        src-dirs ["src-dbg" "src-shared" "src-inst"]]
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs src-dirs})
    (b/compile-clj {:basis basis
                    :src-dirs src-dirs
                    :class-dir class-dir
                    :compile-opts {:direct-linking false}
                    :ns-compile aot-compile-nses
                    })
    (b/copy-dir {:src-dirs (into src-dirs ["resources"])
                 :target-dir class-dir})
    ;; This doesn't work anymore since we are using conditional reading
    #_(re-write-dir-for-version class-dir version)
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

(defn jar-inst [_]
  (clean nil)
  (let [lib 'com.github.jpmonettas/flow-storm-inst
        ;;version (format "3.4.%s" (b/git-count-revs nil))
        version (format "3.4-beta-8" (b/git-count-revs nil))
        basis (b/create-basis {:project "deps.edn" :aliases []})
        jar-file (format "target/%s.jar" (name lib))
        src-dirs ["src-inst" "src-shared"]]
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs src-dirs})
    (b/compile-clj {:basis basis
                    :src-dirs src-dirs
                    :class-dir class-dir
                    :compile-opts {:direct-linking false}
                    :ns-compile aot-compile-nses
                    })
    (b/copy-dir {:src-dirs (into src-dirs ["resources"])
                 :target-dir class-dir})
    ;; This doesn't work anymore since we are using conditional reading
    ;; (re-write-dir-for-version class-dir version)
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))





(comment
  (def code-files (->> (file-seq (io/file "target/classes"))
                       (keep (fn [file]
                               (when (or (str/ends-with? (.getName file) ".clj")
                                         (str/ends-with? (.getName file) ".cljc"))
                                 file)))))

  (def t-proc-file (some (fn [file]
                           (when (= (.getName file) "trace_.clj")
                             file))
                         code-files))
  (def api-file (some (fn [file]
                        (when (= (.getName file) "api.clj")
                          file))
                      code-files))

  (-> (z/of-file api-file)
      z/right ;; find (def debugger-trace-processor-ns 'flow-storm.debugger.trace-processor)
      z/next z/next z/next z/down
      z/up
      z/up
      z/right ;; find (def debugger-main-ns 'flow-storm.debugger.main)
      z/next z/next z/next z/down
      z/up
      z/up
      z/left
      z/left
      z/sexpr
      )


  (def ffz (z/of-file api-file))

  (-> (z-edit-ns-name ffz "1.0.0") z/sexpr)
  (-> ffz z/next z/right z/sexpr)
  (-> (z-find-require ffz) z/sexpr)
  (-> (z-find-import ffz) z/sexpr)

  )
