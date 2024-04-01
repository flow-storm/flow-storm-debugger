(ns dev

  "A bunch of utilities to help with development.

  After loading this ns you can :

  - `start-local` to start the UI and runtime
  - `start-remote` to run only the UI and connect it to a remote process. Looks at the body for config.
  - `stop` for gracefully stopping the system
  - `refresh` to make tools.namespace unmap and reload all the modified files"

  (:require [flow-storm.debugger.ui.main :as ui-main]
            [flow-storm.debugger.main :as main]
            [flow-storm.debugger.state :as dbg-state]
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
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [flow-storm.runtime.indexes.protocols :as index-protos]))

(javafx.embed.swing.JFXPanel.)

(set! *warn-on-reflection* true)

(comment
  (add-tap (bound-fn* println))
  )

#_(Thread/setDefaultUncaughtExceptionHandler
   (reify
     Thread$UncaughtExceptionHandler
     (uncaughtException [_ _ throwable]
       (tap> throwable)
       (log-error "Unhandled exception" throwable))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for reloading everything ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spec-instrument-state []
  (add-watch
   dbg-state/state
   :spec-validator
   (fn [_ _ _ s]
     (when-not (s/valid? ::dbg-state/state s)
       (s/explain ::dbg-state/state s))))
  nil)

(comment
  (remove-watch state :spec-validator)
  )

(defn start-local []
  (fs-api/local-connect {:theme :ligth})
  (spec-instrument-state))


(defn start-remote []

  (main/start-debugger {:port 9000
                        :repl-type :shadow
                        :build-id :browser-repl})
  (spec-instrument-state))

(defn stop []
  (fs-api/stop))

(defn after-refresh []
  (alter-var-root #'utils/out-print-writer (constantly *out*))
  (log "Refresh done"))

(defn refresh []
  (let [running? dbg-state/state]
    (when running?
      (log "System is running, stopping first ...")
      (stop))
    (tools-namespace-repl/refresh :after 'dev/after-refresh )))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Playing at the repl ;;
;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  ;;;;;;;;;;;;;;;;;;;;;;;
  ;; Vanilla FlowStorm ;;
  ;;;;;;;;;;;;;;;;;;;;;;;

  (fs-api/instrument-namespaces-clj
   #{"dev-tester"}
   {:disable #{} #_#{:expr-exec :anonymous-fn :bind}})

  (fs-api/uninstrument-namespaces-clj #{"dev-tester"})

  #rtrace (dev-tester/boo [2 "hello" 6])

  ;;;;;;;;;;
  ;; Docs ;;
  ;;;;;;;;;;

  (def r (sampler/sample
             {:result-name "dev-tester-flow-docs-0.1.0"
              :inst-ns-prefixes #{"dev-tester"}
              :verbose? true
              :print-unsampled? true}
           (dev-tester/boo [1 "hello" 6])))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; Example expressions to generate trace data ;;
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


  (dev-tester/boo [1 "hello" 4])

  (flow-storm.api/continue)

  (defn my-sum [a b] (+ a b))

  (doall (pmap (fn my-sum [i] (+ i i)) (range 4)))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Querying indexes programatically ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (index-api/print-threads)
  (index-api/select-thread nil 32)
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
          {:keys [form/form]} (index-api/get-form form-id)]
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
  (index-api/select-thread nil 18)

  (step-next)
  (step-prev))

(comment

  (dev-tester/run)
  (dev-tester/run-parallel)

  (require 'dev-tester-12)
  (dev-tester-12/run)

  (def tl (index-api/get-timeline 18))

  (->> tl
      (take 10)
      (map index-api/as-immutable))

  (count tl)
  (take 10 tl)
  (time
   (reduce (fn [r e]
             (inc r))
           0
           tl))
  (get tl 0)
  (nth tl 0)
  (empty? tl)

  (def total-timeline (index-api/total-order-timeline))
  (->> total-timeline
       (take 10)
       (map index-api/as-immutable))

  (index-api/find-fn-call-entry {:backward? true
                                 :fn-name "factorial"})

  (index-api/find-expr-entry {:backward? true
                              :equality-val 42})

  )
