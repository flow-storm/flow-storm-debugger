(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]))

(def version "3.6.4-SNAPSHOT")

(def target-dir "target")
(def class-dir (str target-dir "/classes"))

(defn clean [_]
  (b/delete {:path target-dir}))

(def aot-compile-nses

  "Precompile this so clojure storm can call flow-storm.storm-api functions
  at repl init and it doesn't slow down repl startup."

  ['flow-storm.storm-api
   'flow-storm.utils
   'flow-storm.api
   'flow-storm.runtime.debuggers-api
   'flow-storm.runtime.types.fn-call-trace
   'flow-storm.runtime.indexes.timeline-index
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
        basis (b/create-basis {:project "deps.edn"})
        jar-file (format "%s/%s.jar" target-dir (name lib))
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
                    :ns-compile aot-compile-nses})
    (b/copy-dir {:src-dirs (into src-dirs ["resources"])
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

(defn jar-inst [_]
  (clean nil)
  (let [lib 'com.github.jpmonettas/flow-storm-inst
        src-dirs ["src-inst" "src-shared"]
        basis (b/create-basis {:project nil
                               :extra {:deps {'org.java-websocket/Java-WebSocket {:mvn/version "1.5.3"}
                                              'com.cognitect/transit-clj {:mvn/version "1.0.333"}
                                              'com.cognitect/transit-cljs {:mvn/version "0.8.280"}
                                              'io.github.clojure/tools.build {:mvn/version "0.9.4" :exclusions ['com.google.guava/guava 'org.slf4j/slf4j-nop]}
                                              'com.github.jpmonettas/hansel {:mvn/version "0.1.63"}
                                              'org.clojure/data.int-map {:mvn/version "1.2.1"}}

                                       :paths src-dirs}})
        jar-file (format "%s/%s.jar" target-dir (name lib))]
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs src-dirs})
    (b/compile-clj {:basis basis
                    :src-dirs src-dirs
                    :class-dir class-dir
                    :compile-opts {:direct-linking false}
                    :ns-compile aot-compile-nses})
    (b/copy-dir {:src-dirs src-dirs
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))
