(ns dev

  "A bunch of utilities to help with development.

  After loading this ns you can :

  - `start-local` to start the UI and runtime
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
            [flow-storm.utils :refer [log-error log] :as utils]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [clj-reload.core :as reload]
            [flow-storm.form-pprinter :as form-pprinter]
            [dev-tester]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [flow-storm.runtime.indexes.protocols :as index-protos]))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for interactive development ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- spec-instrument-state []
  (add-watch
   dbg-state/state
   :spec-validator
   (fn [_ _ _ s]
     (when-not (s/valid? ::dbg-state/state s)
       (s/explain ::dbg-state/state s))))
  nil)

(defn- spec-uninstrument-state []
  (remove-watch dbg-state/state :spec-validator))

(defn start-local []
  (fs-api/local-connect {:skip-index-start? (not (nil? index-api/flow-thread-registry))})
  (spec-instrument-state))

(defn start-shadow-remote [port build-id]
  (main/start-debugger {:port port
                        :repl-type :shadow
                        :build-id build-id})
  (spec-instrument-state))

(defn stop []
  (fs-api/stop {:skip-index-stop? (utils/storm-env?)}))

(defn refresh []
  (let [running? dbg-state/state]
    (log "Reloading system ...")
    (when running?
      (log "System is running, stopping it first ...")
      (fs-api/stop))
    (reload/reload)
    (alter-var-root #'utils/out-print-writer (constantly *out*))
    (log "Reload done")))

(defn run-tester-1 []
  (dev-tester/run))

(defn run-tester-2 []
  (dev-tester/run-parallel))

;;;;;;;;;;;;;;;;;;;;;;;
;; Vanilla FlowStorm ;;
;;;;;;;;;;;;;;;;;;;;;;;

(comment


  (fs-api/instrument-namespaces-clj
   #{"dev-tester"}
   {:disable #{} #_#{:expr-exec :anonymous-fn :bind}})

  (fs-api/uninstrument-namespaces-clj #{"dev-tester"})

  #rtrace (dev-tester/boo [2 "hello" 6])

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Querying indexes programatically ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (run-tester-1)
  (run-tester-2)
  ((requiring-resolve 'dev-tester-12/run))

  (def tl (index-api/get-timeline 24)) ;; set the thread-id

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

  (def total-timeline (index-api/total-order-timeline 0))
  (->> total-timeline
       (take 10)
       (map index-api/as-immutable))

  (index-api/find-fn-call-entry {:backward? true
                                 :fn-name "factorial"})

  ;; Synthesizing all the spec information for parameters that flow into a function
  (defn fn-signatures [flow-id thread-id fn-ns fn-name]
    (let [frames (index-api/find-fn-frames flow-id thread-id fn-ns fn-name nil)
          signature-types (->> frames
                               (reduce (fn [coll-samples frame]
                                         (conj coll-samples (mapv type (:args-vec frame))))
                                       #{}))]
      signature-types))

  (fn-signatures 0 24 "dev-tester" "factorial")
  (fn-signatures 0 24 "dev-tester" "other-function")

  ;; Find all the sub expressions at the same code coordinate and fn frame
  ;; than the one which evaluated at idx
  (defn frame-same-coord-values [flow-id thread-id idx]
    (let [{:keys [fn-call-idx coord]} (index-api/timeline-entry flow-id thread-id idx :at)
          {:keys [expr-executions]} (index-api/frame-data flow-id thread-id fn-call-idx {:include-exprs? true})]

      (->> expr-executions
           (reduce (fn [coll-vals expr-exec]
                     (if (= coord (:coord expr-exec))
                       (conj coll-vals (:result expr-exec))
                       coll-vals))
                   []))))

  (frame-same-coord-values 0 24 49) ;; sum on dev/run-tester-1

  ;; Create a small debugger for the repl
  ;; -------------------------------------------------------------------------------------------

  (def idx (atom 0))
  (def flow-id 0)
  (def thread-id 24)

  (defn show-current []
    (let [{:keys [type fn-ns fn-name coord fn-call-idx result] :as idx-entry} (index-api/timeline-entry flow-id thread-id @idx :at)
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

  (step-next)
  (step-prev))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; DataWindows testing ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (tap> {:a (range)})

  (tap> {:a {:name {:other :hello
                   :bla "world"}}
         :b {:age 10}})

  (def dw-id :scope11)

  (fs-api/data-window-push-val dw-id 0)

  (defn calc [x]
    (* 100 (Math/sin x)))

  (def th (Thread.
           (fn []
             (loop [x 0]
               (when-not (Thread/interrupted)
                 (Thread/sleep 10)
                 (fs-api/data-window-val-update dw-id (calc x))
                 (recur (+ x 0.1)))))))

  (.start th)
  (.interrupt th)
  )

;;;;;;;;;;;;;;;;;;;;;
;; Other utilities ;;
;;;;;;;;;;;;;;;;;;;;;

(comment

  (add-tap (bound-fn* println))

  (Thread/setDefaultUncaughtExceptionHandler
   (reify
     Thread$UncaughtExceptionHandler
     (uncaughtException [_ _ throwable]
       (tap> throwable)
       (log-error "Unhandled exception" throwable))))
  )
