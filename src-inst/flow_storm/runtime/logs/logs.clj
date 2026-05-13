(ns flow-storm.runtime.logs.logs
  (:require [clojure.string :as str]
            [flow-storm.runtime.logs.utils :as log-utils :refer [class-exists?]]
            [flow-storm.runtime.logs.facades :as facades]
            [flow-storm.runtime.logs.backends :as backends]
            [flow-storm.runtime.logs.bridges :as bridges]))

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


(defn- config-resources [systems]
  (reduce (fn [acc back-key]
            (let [back-res (->> (get-in backends/backends [back-key :config-resources])
                                (keep log-utils/resource))]
              (if (seq back-res)
                (assoc acc back-key back-res)
                acc)))
          {}
          (:backends systems)))

(defn detect-systems []
  (let [detect (fn [m]
                 (keep
                  (fn [{:keys [id present?]}]
                    (when (present?)
                      id))
                  (vals m)))
        found-systems {:facades  (detect facades/facades)
                 :bridges  (detect bridges/bridges)
                 :backends (detect backends/backends)}
        backends-configs (config-resources found-systems)]
    (assoc found-systems :backends-configs-found backends-configs)))




(comment

  (detect-systems)


  (import '[org.apache.commons.logging Log])


  ((-> facades/facades  :slf4j :print-test-fn) "Hello world")
  ((-> facades/facades  :slf4j :current-backend-fn))

  ((-> facades/facades  :jul :print-test-fn) "Hello world")
  ((-> facades/facades  :jul :current-backend-fn))

  ((-> facades/facades  :commons-logging :print-test-fn) "Hello world")
  ((-> facades/facades  :commons-logging :current-backend-fn))

  ((-> facades/facades  :log4j2 :print-test-fn) "Hello world")
  ((-> facades/facades  :log4j2 :current-backend-fn))

  ((-> facades/facades  :log4j1 :print-test-fn) "Hello world") ;; add appender here
  ((-> facades/facades  :log4j1 :current-backend-fn))

  ((-> facades/facades  :jboss-logging :print-test-fn) "Hello world")
  ((-> facades/facades  :jboss-logging :current-backend-fn))

  ((-> facades/facades  :system-logger :print-test-fn) "Hello world")
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

;; (require '[clojure.java.io :as io])

;; (println org.slf4j.LoggerFactory/REQUESTED_API_VERSION)
;; (println (.getProtectionDomain org.slf4j.LoggerFactory))
;; (println (io/resource "org/slf4j/LoggerFactory.class"))

;; (System/getProperty "java.class.path")
