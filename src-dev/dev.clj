(ns dev
  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.main :as main]
            [cljs.main :as cljs-main]
            [hansel.api :as hansel]
            [flow-storm.api :as fs-api]
            [flow-storm.runtime.indexes.api :as index-api]
            [flow-storm.runtime.indexes.timeline-index :as timeline-index]
            [flow-storm.tracer :as tracer]
            [flow-storm.utils :refer [log-error log]]
            [clojure.tools.namespace.repl :as tools-namespace-repl :refer [set-refresh-dirs disable-unload! disable-reload!]]
            [flow-storm.form-pprinter :as form-pprinter]
            [dev-tester]
            [flow-storm.fn-sampler.core :as sampler]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io]))

(javafx.embed.swing.JFXPanel.)

(comment (add-tap (bound-fn* println)) )

#_(Thread/setDefaultUncaughtExceptionHandler
   (reify
     Thread$UncaughtExceptionHandler
     (uncaughtException [_ _ throwable]
       (tap> throwable)
       (log-error "Unhandled exception" throwable))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for reloading everything ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-local [] (fs-api/local-connect {:theme :ligth}))

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
  (index-api/select-thread nil 18)
  (index-api/print-forms)


  ;; Synthesizing all the spec information for parameters that flow into a function
  (defn fn-signatures [fn-ns fn-name]
    (let [[flow-id thread-id] @index-api/selected-thread
          frames (index-api/all-frames flow-id thread-id (fn [fns fname _ _]
                                                           (and (= fn-name fname)
                                                                (= fn-ns fns))))
          signature-types (->> frames
                               (reduce (fn [coll-samples frame]
                                         (conj coll-samples (mapv type (:args-vec frame))))
                                       #{}))]
      signature-types))

  (fn-signatures "dev-tester" "factorial")
  (fn-signatures "dev-tester" "other-function")

  ;; Visualization lenses over traces: say I have a loop-recur process in which I am computing
  ;; new versions of an accumulated data structure, but I want to see only some derived data
  ;; instead of the entire data-structure (like, a visualization based on every frame of the loop).
  (defn frame-similar-values [idx]
    (let [[flow-id thread-id] @index-api/selected-thread
          {:keys [fn-call-idx coord]} (index-api/timeline-entry flow-id thread-id idx :at)
          {:keys [expr-executions]} (index-api/frame-data flow-id thread-id fn-call-idx {:include-exprs? true})]

      (->> expr-executions
           (reduce (fn [coll-vals expr-exec]
                     (if (= coord (:coord expr-exec))
                       (conj coll-vals (:result expr-exec))
                       coll-vals))
                   []))))

  (frame-similar-values (dec 161)) ;; sum

  ;; Create a small debugger for the repl
  ;; -------------------------------------------------------------------------------------------

  (require '[flow-storm.form-pprinter :as form-pprinter])
  (def idx (atom 0))

  (defn show-current []
    (let [[flow-id thread-id] @index-api/selected-thread
          {:keys [type fn-ns fn-name coord fn-call-idx result] :as idx-entry} (index-api/timeline-entry flow-id thread-id @idx :at)
          {:keys [form-id]} (index-api/frame-data flow-id thread-id fn-call-idx {})
          {:keys [form/form]} (index-api/get-form flow-id thread-id form-id)]
      (case type
        :fn-call (let [{:keys [fn-name fn-ns]} idx-entry]
                   (println "Called" fn-ns fn-name))
        (:expr :fn-return) (let [{:keys [coord result]} idx-entry]
                             (form-pprinter/pprint-form-hl-coord form coord)
                             (println "\n")
                             (println "==[VAL]==>" (utils/colored-string result :yellow))))))

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

  (defn bar [a b] (+ a b))
  (defn foo [a b] (let [c (+ a b)] (bar c c)))

  (doall (pmap (fn [i] (foo i (inc i))) (range 4)))

  (dev-tester/boo [1 "hello" 4])

  (flow-storm.api/continue)

  )
