(ns dev
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.main :as main]
            [cljs.main :as cljs-main]
            [hansel.api :as hansel]
            [flow-storm.api :as fs-api]
            [flow-storm.runtime.indexes.api :as index-api]
            [flow-storm.runtime.indexes.frame-index :as frame-index]
            [flow-storm.tracer :as tracer]
            [flow-storm.utils :refer [log-error log]]
            [clojure.tools.namespace.repl :as tools-namespace-repl :refer [set-refresh-dirs disable-unload! disable-reload!]]
            [flow-storm.debugger.form-pprinter :as form-pprinter]
            [dev-tester]
            [flow-storm.fn-sampler.core :as sampler]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io]))

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

(defn start-local [] (fs-api/local-connect {:theme :dark}))

(defn start-remote []

  (comment

    (main/start-debugger {:port 9000
                          :repl-type :shadow
                          :build-id :browser-repl})

    (main/start-debugger {:port 9000
                          :repl-type :shadow
                          :build-id :app})

    (main/start-debugger {})
    (main/start-debugger {:port 9000})

    (main/start-debugger {:port 46000
                          :repl-type :shadow
                          :build-id :analysis-viewer})
    (main/stop-debugger)

    )

  (main/start-debugger {:port 9000
                        :repl-type :shadow
                        :build-id :browser-repl}))

(defn after-refresh []
  (alter-var-root #'utils/out-print-writer (constantly *out*))
  (log "Refresh done"))

(defn stop []
  (fs-api/stop))

(defn refresh []
  (tools-namespace-repl/refresh :after 'dev/after-refresh))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Playing at the repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (local-restart-everything)

  )


;; instrument and run dev-tester namespaces
(comment

  (fs-api/instrument-namespaces-clj
   #{"dev-tester"}
   {:disable #{} #_#{:expr-exec :anonymous-fn :bind}})

  (fs-api/uninstrument-namespaces-clj #{"dev-tester"})

  #rtrace (dev-tester/boo [2 "hello" 6])

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
  (index-api/select-thread 0 18)
  (index-api/print-forms)

  ;; Synthesizing all the spec information for parameters that flow into a function
  (defn fn-signatures [fn-ns fn-name]
    (let [[flow-id thread-id] @index-api/selected-thread
          {:keys [frame-index]} (index-api/get-thread-indexes flow-id thread-id)
          frames (frame-index/timeline-frame-seq frame-index)
          signature-types (->> frames
                               (reduce (fn [coll-samples frame]
                                         (if (and (= fn-ns (:fn-ns frame))
                                                  (= fn-name (:fn-name frame)))

                                           (conj coll-samples (mapv type (:args-vec frame)))

                                           coll-samples))
                                       #{}))]
      signature-types))

  (fn-signatures "dev-tester" "factorial")
  (fn-signatures "dev-tester" "other-function")

  ;; Visualization lenses over traces: say I have a loop-recur process in which I am computing
  ;; new versions of an accumulated data structure, but I want to see only some derived data
  ;; instead of the entire data-structure (like, a visualization based on every frame of the loop).
  (defn frame-similar-values [idx]
    (let [[flow-id thread-id] @index-api/selected-thread
          {:keys [frame-index]} (index-api/get-thread-indexes flow-id thread-id)
          {:keys [expr-executions]} (frame-index/frame-data frame-index idx)
          {:keys [coor]} (frame-index/timeline-entry frame-index idx)]

      (->> expr-executions
           (reduce (fn [coll-vals expr-exec]
                     (if (= coor (:coor expr-exec))
                       (conj coll-vals (:result expr-exec))
                       coll-vals))
                   []))))

  (frame-similar-values (dec 109)) ;; sum

  ;; Create a small debugger for the repl
  ;; -------------------------------------------------------------------------------------------

  (def idx (atom 0))

  (defn show-current []
    (let [[flow-id thread-id] @index-api/selected-thread
          {:keys [coor form-id result]} (index-api/timeline-entry flow-id thread-id @idx)
          {:keys [form/form]} (index-api/get-form flow-id thread-id form-id)]
      (when coor
        (form-pprinter/pprint-form-hl-coord form coor)
        (println "\n")
        (println "==[VAL]==>" (utils/colored-string result :yellow)))))

  (defn step-next []
    (swap! idx inc)
    (show-current))

  (defn step-prev []
    (swap! idx dec)
    (show-current))

  ;; use the debugger
  (index-api/print-threads)
  (index-api/select-thread 0 16)

  (step-next)
  (step-prev)

  ;; ---------------------------------------------------------------------------------------------


  )

;; forms instrumentation
(comment

  (inst-forms/instrument {:disable #{}} '(defn foo [a b] ^{:meta true} (conj [] b)))

  )


;; Function sampler

(comment

  (def r (sampler/sample
          {:result-name "dev-tester-flow-docs-0.1.0"
           :inst-ns-prefixes #{"dev-tester"}
           :verbose? true
           :print-unsampled? true}
          (dev-tester/boo [1 "hello" 6])))

  (io/copy (io/file "/tmp/1670878691457-36075814-1/samples.edn")
           (io/file "samples.edn"))



  )

(defn foo [a b]
  (let [c (+ a b)]
    c))

(comment

  (clojure.storm.Tracer/enable)
  (clojure.sotrm.Tracer/disable)

  (foo 5 (foo 7 8))


  )
