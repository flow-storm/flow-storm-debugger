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
            [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.runtime.values :as rt-values :refer [ScopeFrameP ScopeFrameSampleP]]))

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
  (fs-api/local-connect {})
  (spec-instrument-state))

(defn start-shadow-remote [port build-id]
  (main/start-debugger {:port port
                        :repl-type :shadow
                        :build-id build-id})
  (spec-instrument-state))

(comment

  (start-shadow-remote 7123 :dev-test)

  )
(defn stop []
  (fs-api/stop))

(defn refresh []
  (let [running? (boolean dbg-state/state)]
    (log "Reloading system ...")
    (when running?
      (log "System is running, stopping it first ...")
      (fs-api/stop))
    (reload/reload)
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

(defn make-sample-frame [from-x step]
  (let [[final-x samples] (loop [i 0
                                 x from-x
                                 samples (transient [])]
                            (if (< i 10000)
                              (let [samp (reify ScopeFrameSampleP
                                           (sample-chan-1 [_] (+ (Math/sin x) 0))
                                           (sample-chan-2 [_] (+ (Math/cos x) 0)))]
                                (recur (inc i)
                                       (+ x step)
                                       (conj! samples samp)))
                              [x (persistent! samples)]))]
    [final-x (reify ScopeFrameP
               (frame-samp-rate [_] 200e3)
               (frame-samples [_] samples))]))

(defn scope-test [& _]
  (fs-api/local-connect {})
  (let [dw-id :scope0
        _ (fs-api/data-window-push-val dw-id (reify ScopeFrameP
                                               (frame-samp-rate [_] 1)
                                               (frame-samples [_] [])))
        x-step 0.001]
    (def th (doto (Thread.
                   (fn []
                     (loop [from-x 0.0]
                       (when-not (Thread/interrupted)
                         (let [[new-x frame] (make-sample-frame from-x x-step)]
                           (fs-api/data-window-val-update dw-id frame)
                           (Thread/sleep 10)
                           (recur (double new-x)))))))
              (.start)))
    )
  )
(comment

  (require '[clj-async-profiler.core :as prof])
  (prof/serve-ui 8081)

  (def test-frame (second (make-sample-frame 0 0.001)))
  (def s (first (rt-values/frame-samples test-frame)))
  (rt-values/sample-chan-1 s)
  (rt-values/sample-chan-2 s)
  (tap> test-frame)
  (tap> {:a (range)})

  (tap> {:a {:name {:other :hello
                   :bla "world"}}
         :b {:age 10}})



  (-> (rt-values/frame-samples fr)
      second
      rt-values/sample-chan-1)



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



;; - ClojureStorm
;;   - [X] Add on new form emitted handler with form-id
;; - Backend
;;   - [X] Register on tracer (clojure.storm.Tracer/setOnFormBytecodeEmitted (fn [_ _]))
;;   - [X] What happens with remote and serialization, maybe just select-keys on event
;; - API
;;   - [X] New EVENT on-new-form-emission-collected
;;   - [X] New api like the instrument one but for toggling clojure.storm.Emitter/setCollectFormsEmissionsEnable
;; - UI
;;   - [X] New UI tab
;;   - [X] Add event handler
;;   - [X] Toggle emission collection, which calls API
;;   - [X] Interactive form display
;;   - [X] Interactive bytecode display
;;   - [X] Clear forms (Ctrl-l) (clears dbg state, no state on runtime)

;;   - [X] Better control emission collection
;;   - [ ] Improve/fix bytecode coordinates

;; - Other
;;   - [ ] Test a bunch of forms decompilation.
;;         - Makes sense?
;;         - Same as decompiler?
;;   - [ ] Improve naming all over

;; - On Hansel
;; - What do we do here?

;; After all this
;; - [ ] Can we do a SCIStorm?
;;       Since it is interpreted should be much easier to add tracing
;; - [ ] Can nbb and babashka runtimes use the index stuff and remote_websocket_client?

(comment

  ^:clojure.storm/collect-emitted
  (defn sum [a b] (let [m {:x 100}] (+ a b (:x m))))

  ^:clojure.storm/collect-emitted
  (defn calcs [a b]
    (let [x (+ a b)
          y (* a b)]
      (* x y)))


  (defn sum [a b] (let [m {:x 100}] (+ a b (:x m))))
  )


;; class dev$sum
;;     Minor version: 0
;;     Major version: 52
;;     Flags: PUBLIC, FINAL, SUPER

;;     public static final clojure.lang.AFn const__2;
;;         Flags: PUBLIC, STATIC, FINAL

;;     static final clojure.lang.KeywordLookupSite __site__0__;
;;         Flags: STATIC, FINAL

;;     static clojure.lang.ILookupThunk __thunk__0__;
;;         Flags: STATIC

;;     public void <init>();
;;         Flags: PUBLIC
;;         Code:
;;                   linenumber      1
;;                0: aload_0
;;                1: invokespecial   clojure/lang/AFunction.<init>:()V
;;                4: return

;;     public static java.lang.Object invokeStatic(java.lang.Object a, java.lang.Object b);
;;         Flags: PUBLIC, STATIC
;;         Code:
;;                   linenumber      1
;;                0: getstatic       dev$sum.const__2:Lclojure/lang/AFn;
;;                3: astore_2        /* m */
;;                4: aload_0         /* a */
;;                5: aload_1         /* b */
;;                   linenumber      1
;;                6: invokestatic    clojure/lang/Numbers.add:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;
;;                   linenumber      1
;;                9: getstatic       dev$sum.__thunk__0__:Lclojure/lang/ILookupThunk;
;;               12: dup
;;               13: aload_2         /* m */
;;                   linenumber      1
;;               14: dup_x2
;;               15: invokeinterface clojure/lang/ILookupThunk.get:(Ljava/lang/Object;)Ljava/lang/Object;
;;               20: dup_x2
;;               21: if_acmpeq       28
;;               24: pop
;;               25: goto            50
;;               28: swap
;;               29: pop
;;               30: dup
;;               31: getstatic       dev$sum.__site__0__:Lclojure/lang/KeywordLookupSite;
;;               34: swap
;;               35: invokeinterface clojure/lang/ILookupSite.fault:(Ljava/lang/Object;)Lclojure/lang/ILookupThunk;
;;               40: dup
;;               41: putstatic       dev$sum.__thunk__0__:Lclojure/lang/ILookupThunk;
;;               44: swap
;;               45: invokeinterface clojure/lang/ILookupThunk.get:(Ljava/lang/Object;)Ljava/lang/Object;
;;                   linenumber      1
;;               50: invokestatic    clojure/lang/Numbers.add:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Number;
;;               53: areturn
;;         StackMapTable: 00 02 FF 00 1C 00 03 07 00 2F 07 00 2F 07 00 31 00 03 07 00 33 07 00 2F 07 00 31 FF 00 15 00 03 07 00 2F 07 00 2F 07 00 31 00 02 07 00 33 07 00 2F

;;     public java.lang.Object invoke(java.lang.Object p0, java.lang.Object p1);
;;         Flags: PUBLIC
;;         Code:
;;                0: aload_1
;;                1: aconst_null
;;                2: astore_1
;;                3: aload_2
;;                4: aconst_null
;;                5: astore_2
;;                   linenumber      1
;;                6: invokestatic    dev$sum.invokeStatic:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
;;                9: areturn

;;     static {};
;;         Flags: PUBLIC, STATIC
;;         Code:
;;                   linenumber      1
;;                0: iconst_2
;;                1: anewarray       Ljava/lang/Object;
;;                4: dup
;;                5: iconst_0
;;                6: aconst_null
;;                7: ldc             "x"
;;                9: invokestatic    clojure/lang/RT.keyword:(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Keyword;
;;               12: aastore
;;               13: dup
;;               14: iconst_1
;;               15: ldc2_w          100
;;               18: invokestatic    java/lang/Long.valueOf:(J)Ljava/lang/Long;
;;               21: aastore
;;               22: invokestatic    clojure/lang/RT.map:([Ljava/lang/Object;)Lclojure/lang/IPersistentMap;
;;               25: checkcast       Lclojure/lang/AFn;
;;               28: putstatic       dev$sum.const__2:Lclojure/lang/AFn;
;;               31: new             Lclojure/lang/KeywordLookupSite;
;;               34: dup
;;               35: aconst_null
;;               36: ldc             "x"
;;               38: invokestatic    clojure/lang/RT.keyword:(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Keyword;
;;               41: invokespecial   clojure/lang/KeywordLookupSite.<init>:(Lclojure/lang/Keyword;)V
;;               44: dup
;;               45: putstatic       dev$sum.__site__0__:Lclojure/lang/KeywordLookupSite;
;;               48: putstatic       dev$sum.__thunk__0__:Lclojure/lang/ILookupThunk;
;;               51: return
