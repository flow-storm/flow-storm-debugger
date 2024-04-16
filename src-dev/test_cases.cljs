(ns test-cases
  (:require  [clojure.test :as t]
             [flow-storm.api :as fs-api]
             [clojure.set :as set]
             [dev-tester]))

;; # rlwrap npx shadow-cljs browser-repl

;; Start the debugger

;; clj -Sforce -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "3.3-alpha-290"}}}' -X flow-storm.debugger.main/start-debugger :port 9000 :repl-type :shadow :build-id :browser-repl

;;;;;;;;;;;;;;;;;;;
;; Basic #rtrace ;;
;;;;;;;;;;;;;;;;;;;

#rtrace (reduce + (map inc (range 10)))

;;;;;;;;;;;;;;;;;;
;; Basic #trace ;;
;;;;;;;;;;;;;;;;;;

#trace
(defn factorial [n]
  (if (zero? n)
    1
    (* n (factorial (dec n)))))

(factorial 5)

;;;;;;;;;;;;;
;; Def val ;;
;;;;;;;;;;;;;

js/v

;;;;;;;;;;;;;;;;;
;; Inspect val ;;
;;;;;;;;;;;;;;;;;

(tap> {:a 10 :b [1 2 3 {:c 10}]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SOME OF THIS IN SHADOW CLJ REPL ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require '[flow-storm.api :as fs-api])
(require '[dev-tester])
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; REPL [un]instrument var ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(fs-api/vanilla-instrument-var-cljs 'clojure.set/difference {:build-id :browser-repl})

(set/difference #{1 2 3} #{2})

(fs-api/vanilla-uninstrument-var-cljs 'clojure.set/difference {:build-id :browser-repl})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; REPL [un]instrument namespaces ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(fs-api/instrument-namespaces-cljs #{"dev-tester"} {:build-id :browser-repl})

(dev-tester/boo [1 "hello" 4])

(fs-api/uninstrument-namespaces-cljs #{"dev-tester"} {:build-id :browser-repl})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; - Re-run flow                     ;;
;; - Fully instrument form           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Instrument clojure.set NS :light

#rtrace (set/difference #{1 2 3} #{2})

;; now fully instrument form and re run flow

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; - Call tree                       ;;
;; - Call tree goto trace            ;;
;; - Call search                     ;;
;; - Functions list                  ;;
;; - Functions list goto trace       ;;
;; - UnInstrument forms from fn list ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;
;; Taps ;;
;;;;;;;;;;

(tap> {:a 10 :b [1 2 3 {:c 10}]})

;;;;;;;;;;;;
;; Themes ;;
;;;;;;;;;;;;

;; Run with dark theme

;; clj -Sforce -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "3.3-alpha-290"}}}' -X flow-storm.debugger.main/start-debugger :port 9000 :repl-type :shadow :build-id :browser-repl :theme :dark
