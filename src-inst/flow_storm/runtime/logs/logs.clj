(ns flow-storm.runtime.logs.logs
  (:require [clojure.string :as str]
            [flow-storm.runtime.logs.utils :as log-utils :refer [class-exists?]]
            [flow-storm.runtime.logs.facades :as facades]
            [flow-storm.runtime.logs.backends :as backends]
            [flow-storm.runtime.logs.bridges :as bridges]
            [clojure.pprint :as pp]))

;; Objective
;;
;; - Display the precense and configuration of current JVM logging libraries
;; - Allow the user to change different backends root loggers levels for library tracing
;;
;; TODOs
;;
;; - [X] Detect facades, backends and bridges presence
;; - [X] Complete backends and facades levels
;; - [X] Complete backends config files
;; - [X] Add backends config files detection
;; - [X] Make printer fns for all supported facades
;; - [X] Add setters/getters for backends root loggers
;; - [ ] Create UI tab
;; - [ ] Display graph
;; - [ ] Change backends level with UI
;; - [ ] Display found config files content
;; - [ ] Figure out how to treat backends like :slf4j-nop
;; - [ ] Figure out a way of testing all this, including multi-hops
;; - [ ] There are backends like reload4j that doesn't have appenders by default, do something about it?


(defn- config-resources [systems]
  (reduce (fn [acc back-key]
            (let [back-res (->> (get-in backends/backends [back-key :config-resources])
                                (keep log-utils/resource))]
              (if (seq back-res)
                (assoc acc back-key back-res)
                acc)))
          {}
          (:backends systems)))

(defn inspect-systems-presence []
  (let [detect (fn [m]
                 (reduce-kv
                  (fn [acc k {:keys [present?]}]
                    (if (present?)
                      (conj acc k)
                      acc))
                  []
                  m))
        found-systems {:facades  (detect facades/facades)
                       :bridges  (detect bridges/bridges)
                       :backends (detect backends/backends)}
        backends-configs (config-resources found-systems)]
    (assoc found-systems :backends-configs-found backends-configs)))

(defn log-test [facade-key]
  (when-let [log-test-fn (-> facades/facades facade-key :log-test-fn)]
    (log-test-fn "Test mesage")))

(defn bridge-to-backend [facade-key backend-key]
  ()
  )

(defn set-backend-level [backend-key new-lvl]
  (when-let [set-backend-lvl-fn (-> backends/backends backend-key :set-root-level-fn)]
    (set-backend-lvl-fn new-lvl)))

(defn look [fac-k]
  (println "Systems : ")
  (pp/pprint (inspect-systems-presence))
  (println)
  (if-let [back-key ((-> facades/facades fac-k :current-backend-fn))]
    (let [path (if (= back-key :slf4j)
                 [fac-k :slf4j ((-> facades/facades :slf4j :current-backend-fn))]
                 [fac-k back-key])
          _ (println "back-key" back-key "path" path)
          final-back-key (last path)
          back-root-lvl (when-let [grl (-> backends/backends final-back-key :get-root-level-fn)]
                          (grl))]
      (println "Logging path" path)
      (println "Backend root level :"  back-root-lvl))
    (println "NO BACKEND DETECTED!!!"))
  ((-> facades/facades fac-k :log-test-fn) "Hello world"))



(comment

  (inspect-systems-presence)

  ;; (doseq [fk (keys facades/facades)]
  ;;   (let [fac-art (get-in facades/facades [fk :artifact])]
  ;;     (doseq [[bk {:keys [desc needs-artifacts]}] (get-in facades/facades [fk :possible-backends])]
  ;;       (println ";;" fk "->" bk)
  ;;       (when (or fac-art (seq needs-artifacts)) (print ";; "))
  ;;       (when fac-art (print (format "%s {:mvn/version \"RELEASE\"}" fac-art)))
  ;;       (when (seq needs-artifacts)
  ;;         (doseq [a needs-artifacts]
  ;;           (if (symbol? a)
  ;;             (print (format " %s {:mvn/version \"RELEASE\"}" a))
  ;;             (let [[f-or-b k] a]
  ;;               (let [art-symb (case f-or-b
  ;;                                :facade  (get-in facades/facades [k :artifact])
  ;;                                :backend (get-in backends/backends [k :artifact]))]
  ;;                 (print (format " %s {:mvn/version \"RELEASE\"}" art-symb)))))))
  ;;       (println)
  ;;       (println))))

  (import '[org.apache.commons.logging Log])


  ((-> facades/facades  :slf4j :log-test-fn) "Hello world")
  ((-> facades/facades  :slf4j :current-backend-fn))

  ((-> facades/facades  :jul :log-test-fn) "Hello world")
  ((-> facades/facades  :jul :current-backend-fn))

  ((-> facades/facades  :commons-logging :log-test-fn) "Hello world")
  ((-> facades/facades  :commons-logging :current-backend-fn))

  ((-> facades/facades  :log4j2 :log-test-fn) "Hello world")
  ((-> facades/facades  :log4j2 :current-backend-fn))

  ((-> facades/facades  :log4j1 :log-test-fn) "Hello world") ;; add appender here
  ((-> facades/facades  :log4j1 :current-backend-fn))

  ((-> facades/facades  :jboss-logging :log-test-fn) "Hello world")
  ((-> facades/facades  :jboss-logging :current-backend-fn))

  ((-> facades/facades  :system-logger :log-test-fn) "Hello world")
  ((-> facades/facades  :system-logger :current-backend-fn))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (-> backends/backends :logback :levels)
  ((-> backends/backends :logback :get-root-level-fn))
  ((-> backends/backends :logback :set-root-level-fn) :debug)

  (-> backends/backends :log4j2 :levels)
  ((-> backends/backends :log4j2 :get-root-level-fn))
  ((-> backends/backends :log4j2 :set-root-level-fn) :debug)

  (-> backends/backends :jul :levels)
  ((-> backends/backends :jul :get-root-level-fn))
  ((-> backends/backends :jul :set-root-level-fn) :finest)

  (-> backends/backends :reload4j :levels)
  ((-> backends/backends :reload4j :get-root-level-fn))
  ((-> backends/backends :reload4j :set-root-level-fn) :trace)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



  )
