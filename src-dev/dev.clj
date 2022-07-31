(ns dev
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.main :as main]
            [flow-storm.api :as fs-api]
            [flow-storm.tracer :as tracer]
            [flow-storm.utils :refer [log-error]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [dev-tester]
            [flow-storm.api-v2-0-38-FLOWNS :as dbg-api]))

;; clj -X:dbg:inst:dev flow-storm.api/cli-run :fn-symb 'dev-tester/boo' :fn-args '[[2 "hello" 8]]'

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for reloading everything ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(javafx.embed.swing.JFXPanel.)

(defn run [& _]
  #_(fs-api/runi
   {:flow-id 0}
   (dev-tester/boo [2 "hello" 8])))

(defn start-and-add-data [& _]

  ;; this will restart the debugger (state and ui), the send-thread and the trace-queue
  (fs-api/local-connect)

  (fs-api/instrument-forms-for-namespaces #{"dev-tester"}
                                          {:disable #{} #_#{:expr :anonymous-fn :binding}})

  (run))

(add-tap (bound-fn* println))

(defn local-restart-everything []
  (tracer/stop-trace-sender)
  (ui-main/close-stage)

  ;; reload all namespaces
  (refresh :after 'dev/start-and-add-data))


(Thread/setDefaultUncaughtExceptionHandler
   (reify
     Thread$UncaughtExceptionHandler
     (uncaughtException [_ _ throwable]
       (log-error "Unhandled exception" throwable))))


(defn self-instrument []

  ;; start outer meta debugger
  (dbg-api/local-connect {:styles "/home/jmonetta/.flow-storm/meta-debugger.css"})

  ;; this are lazily required by (fs-api/local-connect), but lets require it before
  ;; so instrument-forms-for-namespaces can see them
  (require 'flow-storm.debugger.trace-processor)
  (require 'flow-storm.debugger.main)

  ;; instrument target debugger (this will automatically avoid instrumenting the meta debugger)
  (dbg-api/instrument-forms-for-namespaces #{"flow-storm."} {}))

(defn run-test-instrumented []
  (dbg-api/run
    {:flow-id 0}
    (start-and-add-data nil)))


(defn run-remote-debugger [{:keys [dbg?]}]
  (when dbg?

    (println "Starting meta local debugger")
    (dbg-api/local-connect {:styles "/home/jmonetta/.flow-storm/meta-debugger.css"})

    (require 'flow-storm.debugger.trace-processor)
    (require 'flow-storm.debugger.main)

    (dbg-api/instrument-forms-for-namespaces #{"flow-storm.debugger"} {}))

  (println "Starting remote debugger")
  (main/start-debugger {:local? false}))

(defn run-remote-test [{:keys [dbg?]}]

  (when dbg?
    (dbg-api/local-connect {:styles "/home/jmonetta/.flow-storm/magenta-debugger.css"})
    (dbg-api/instrument-forms-for-namespaces #{"flow-storm.tracer" "flow-storm.api" "flow-storm.core"} {}))

  (fs-api/instrument-forms-for-namespaces #{"dev-tester"} {})
  (fs-api/remote-connect {:on-connected (fn []
                                          (println "Connected to remote debugger, running test ...")
                                          (run))}))

(comment
  (self-instrument)
  (run-test-instrumented)

  (fs-api/local-connect {:theme :light})

  #trace
  (defn some-calculation [a]
    (+ a 10))

  #ctrace
  (defn boo []
    (->> (range 10)
         (map (fn sc [i]
                ^{:trace/when (<= 2 i 4)}
                (some-calculation i)))
         (reduce +)))

  (boo)


  (.start
   (Thread. (fn []
              (fs-api/cli-run
               {:instrument-ns #{"cljs."}
                :profile :light
                :verbose? :true
                :require-before #{"cljs.repl.node"}
                :excluding-ns #{"cljs.util" "cljs.vendor.cognitect.transit"}
                :fn-symb 'cljs.main/-main
                :fn-args ["-t" "nodejs" "/home/jmonetta/demo/org/foo/myscript.cljs"]}))))



  (doseq [t (take 50 (flow-storm.debugger.trace-indexer.protos/print-traces (get-in @flow-storm.debugger.state/*state [:flows 0 :flow/threads 16 :thread/trace-indexer])
                                                                            50))]
       (if (instance? flow_storm.trace_types.ExecTrace t)
         (println "ExecTrace" (:outer-form? t))
         (println "FnCallTrace")))

  )
