(ns dev
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.main :as main]
            [flow-storm.api :as fs-api]
            [flow-storm.tracer :as tracer]
            [flow-storm.utils :refer [log-error]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [dev-tester]
            [flow-storm.api-v2-0-38-FLOWNS :as dbg-api]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for reloading everything ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(javafx.embed.swing.JFXPanel.)
(comment (add-tap (bound-fn* println)) )

(defn start-dev-debugger []
  (fs-api/local-connect))

(defn local-restart-everything []
  (fs-api/stop)

  ;; reload all namespaces
  (refresh :after 'dev/start-dev-debugger))

(comment

  (fs-api/instrument-forms-for-namespaces
   #{"dev-tester"}
   {:disable #{} #_#{:expr :anonymous-fn :binding}})

  #rtrace (dev-tester/boo [2 "hello" 6])

  (main/start-debugger {:local? false
                        :host "localhost"
                        :port 9000
                        :repl-type :shadow
                        :build-id :browser-repl})
  (main/stop-debugger)
  )


(Thread/setDefaultUncaughtExceptionHandler
   (reify
     Thread$UncaughtExceptionHandler
     (uncaughtException [_ _ throwable]
       (tap> throwable)
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

  #_(when dbg?
    (dbg-api/local-connect {:styles "/home/jmonetta/.flow-storm/magenta-debugger.css"})
    (dbg-api/instrument-forms-for-namespaces #{"flow-storm.tracer" "flow-storm.api" "flow-storm.core"} {}))

  #_(fs-api/remote-connect {:on-connected (fn []
                                          (println "Connected to remote debugger, running test ...")
                                          (fs-api/instrument-forms-for-namespaces #{"dev-tester"} {})
                                          (throw (Exception. "Not implemented"))
                                          )}))

(comment

  (self-instrument)
  (run-test-instrumented)

  (fs-api/local-connect {:theme :light})
  (fs-api/stop)
  (fs-api/local-connect {:theme :dark})

  )
