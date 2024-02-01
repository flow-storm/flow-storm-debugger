(ns flow-storm.storm-api
  (:require [flow-storm.api :as fs-api]
            [flow-storm.tracer :as tracer]
            [flow-storm.runtime.debuggers-api :as dbg-api]
            [flow-storm.runtime.indexes.api :as indexes-api]))

(defn start-recorder []
  (dbg-api/setup-runtime)
  (indexes-api/start))

(defn start-debugger []
  (let [config {:skip-index-start? true}]
    (fs-api/local-connect config)))

(def jump-to-last-expression dbg-api/jump-to-last-expression-in-this-thread)

(defn print-flow-storm-help []
  (println "Flow Storm settings: \n")
  (println (format "  Recording : %s" (tracer/recording?)))
  (println)
  (println "ClojureStorm Commands: \n")
  (println "  :dbg        - Show the FlowStorm debugger UI, you can dispose it by closing the window.")
  (println "  :rec        - Start recording. All instrumented code traces will be recorded.")
  (println "  :stop       - Stop recording. Instrumented code will execute but nothing will be recorded, so no extra heap will be consumed.")
  (println "  :last       - Focus the last recorded expression on this thread.")
  (println "  :help       - Print this help.")
  (println "  :tut/basics - Starts the basics tutorial.")
  (println)
  (println "JVM config properties: \n")
  (println "  -Dflowstorm.startRecording              [true|false]")
  (println "  -Dflowstorm.theme                       [dark|light|auto] (defaults to auto)")
  (println "  -Dflowstorm.styles                      [STRING] Ex: /home/user/my-styles.css")
  (println "  -Dflowstorm.threadFnCallLimits          Ex: org.my-app/fn1:2,org.my-app/fn2:4")
  (println)
  (println "Modify limits : \n")
  (println "  (flow-storm.runtime.indexes.api/add-fn-call-limit \"org.my-app\" \"fn1\" 10)")
  (println "  (flow-storm.runtime.indexes.api/rm-fn-call-limit \"org.my-app\" \"fn1\")")
  (println "  (flow-storm.runtime.indexes.api/get-fn-call-limits)")
  (println))

(defn maybe-execute-flow-storm-specials [input]
  (try
    (case input
      :dbg        (do (start-debugger)                    true)
      :last       (do (jump-to-last-expression)           true)
      :rec        (do (dbg-api/set-recording true)  true)
      :stop       (do (dbg-api/set-recording false) true)

      :tut/basics (do ((requiring-resolve 'flow-storm.tutorials.basics/start))     true)
      :tut/next   (do ((requiring-resolve 'flow-storm.tutorials.basics/step-next)) true)
      :tut/prev   (do ((requiring-resolve 'flow-storm.tutorials.basics/step-prev)) true)
      false)

    (catch Exception e
      (when (or (instance? UnsupportedClassVersionError e)
                (and (.getCause e) (instance? UnsupportedClassVersionError (.getCause e))))
        (println "\n\nFlowStorm UI requires JDK >= 17. If you can't upgrade your JDK you can still use it by downgrading JavaFx.")
        (println "\nIf that is the case add this dependencies to your alias :\n")
        (println "   org.openjfx/javafx-controls {:mvn/version \"19.0.2\"}")
        (println "   org.openjfx/javafx-base     {:mvn/version \"19.0.2\"}")
        (println "   org.openjfx/javafx-graphics {:mvn/version \"19.0.2\"}")
        (println "   org.openjfx/javafx-swing    {:mvn/version \"19.0.2\"}")
        (println)
)

      false)))
