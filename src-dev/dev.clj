(ns dev
  (:require [flow-storm.debugger.ui.main :as ui-main]
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
  (fs-api/run
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

(comment
  (self-instrument)
  (run-test-instrumented)
  )
