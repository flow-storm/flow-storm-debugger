(ns dev
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.main :as main]
            [flow-storm.api :as fs-api]
            [flow-storm.runtime.indexes.api :as index-api]
            [flow-storm.runtime.indexes.frame-index :as frame-index]
            [flow-storm.tracer :as tracer]
            [flow-storm.utils :refer [log-error]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [dev-tester]
            [flow-storm.api-v2-0-38-FLOWNS :as dbg-api]))


(javafx.embed.swing.JFXPanel.)

(comment (add-tap (bound-fn* println)) )

(Thread/setDefaultUncaughtExceptionHandler
   (reify
     Thread$UncaughtExceptionHandler
     (uncaughtException [_ _ throwable]
       (tap> throwable)
       (log-error "Unhandled exception" throwable))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for reloading everything ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-dev-debugger []
  (fs-api/local-connect))

(defn local-restart-everything []
  (fs-api/stop)

  ;; reload all namespaces
  (refresh :after 'dev/start-dev-debugger))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Playing at the repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

;; instrument and run dev-tester namespaces
(comment

  (fs-api/instrument-forms-for-namespaces
   #{"dev-tester"}
   {:disable #{} #_#{:expr :anonymous-fn :binding}})

  #rtrace (dev-tester/boo [2 "hello" 6])

  )

;; Start a remote debugger and connect to a shadow repl
(comment

  (main/start-debugger {:local? false
                        :host "localhost"
                        :port 9000
                        :repl-type :shadow
                        :build-id :browser-repl})
  (main/stop-debugger)

  )

;; local start with different themes
(comment

  (fs-api/local-connect {:theme :light})
  (fs-api/stop)
  (fs-api/local-connect {:theme :dark})

  )

;; ideas for accessing the indexes programmatically
(comment

  (index-api/print-threads)
  (index-api/select-thread 0 16)
  (index-api/print-forms)

  ;; Synthesizing all the spec information for parameters that flow into a function
  (defn fn-signatures [fn-ns fn-name]
    (let [[flow-id thread-id] @index-api/selected-thread
          {:keys [frame-index]} (index-api/get-thread-indexes flow-id thread-id)
          frames (frame-index/timeline-frame-seq frame-index)
          sampled-args (->> frames
                            (reduce (fn [coll-samples frame]
                                      (if (and (= fn-ns (:fn-ns frame))
                                               (= fn-name (:fn-name frame)))

                                        (conj coll-samples (:args-vec frame))

                                        coll-samples))
                                    []))
          signature-types (->> sampled-args
                               (map (fn [args-v] (mapv type args-v)))
                               (into #{}))]
      signature-types))

  (fn-signatures "dev-tester" "factorial")
  (fn-signatures "dev-tester" "other-function")

  ;; Visualization lenses over traces: say I have a loop-recur process in which I am computing
  ;; new versions of an accumulated data structure, but I want to see only some derived data
  ;; instead of the entire data-structure (like, a visualization based on every frame of the loop).

  (defn frame-similar-values [idx]
    (let [[flow-id thread-id] @index-api/selected-thread
          {:keys [frame-index]} (index-api/get-thread-indexes flow-id thread-id)
          frame (frame-index/frame-data frame-index 116)
          target-exec (frame-index/timeline-entry frame-index 116)]

      (->> (:expr-executions frame)
           (reduce (fn [coll-vals {:keys [coor result]}]
                     (if (= coor (:coor target-exec))
                       (conj coll-vals result)
                       coll-vals))
                   []))))

  (frame-similar-values 32) ;; sum

  )
