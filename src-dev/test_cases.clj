(ns test-cases
  (:require  [clojure.test :as t]
             [flow-storm.api :as fs-api]
             [clojure.set :as set]
             [dev-tester]))

(fs-api/local-connect)

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

user/v

;;;;;;;;;;;;;;;;;
;; Inspect val ;;
;;;;;;;;;;;;;;;;;

(tap> (all-ns))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; REPL [un]instrument var ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(fs-api/instrument-var-clj 'clojure.set/difference)

(set/difference #{1 2 3} #{2})

(fs-api/uninstrument-var-clj 'clojure.set/difference)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; REPL [un]instrument namespaces ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(fs-api/instrument-namespaces-clj #{"dev-tester"})

(dev-tester/boo [1 "hello" 4])

(fs-api/uninstrument-namespaces-clj #{"dev-tester"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; - Cli-run                         ;;
;; - Re-run flow                     ;;
;; - Call tree                       ;;
;; - Call tree goto trace            ;;
;; - Call search                     ;;
;; - Functions list                  ;;
;; - Functions list goto trace       ;;
;; - Fully instrument form           ;;
;; - UnInstrument forms from fn list ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

make test-instrument-own-cljs-light

;;;;;;;;;;
;; Taps ;;
;;;;;;;;;;

(tap> (all-ns))

;;;;;;;;;;;;
;; Themes ;;
;;;;;;;;;;;;

(fs-api/stop)

(fs-api/local-connect {:theme :dark})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; For remote debugging testing ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Login on windows vm
