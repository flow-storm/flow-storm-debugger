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
;; - [ ] Make printer fns for all supported facades
;; - [ ] Add setters/getters for backends root loggers
;; - [ ] Create UI tab
;; - [ ] Display graph
;; - [ ] Change backends level with UI
;; - [ ] Display found config files content
;; - [ ] Figure out how to treat backends like :slf4j-nop
;; - [ ] Figure out a way of testing all this, including multi-hops


(defn detect-systems []
  (let [detect (fn [m]
                 (keep
                  (fn [{:keys [id present?]}]
                    (when (present?)
                      id))
                  (vals m)))]
    {:facades  (detect facades/facades)
     :bridges  (detect bridges/bridges)
     :backends (detect backends/backends)}))

(defn add-config-resources [systems]
  (let [configs-found (reduce (fn [acc back-key]
                                (let [back-res (->> (get-in backends/backends [back-key :config-resources])
                                                    (keep log-utils/resource))]
                                  (if (seq back-res)
                                    (assoc acc back-key back-res)
                                    acc)))
                              {}
                              (:backends systems))]
    (assoc systems :backends-configs-found configs-found)))


(comment

  (->> (detect-systems)
       add-config-resources)

  (import '[org.apache.commons.logging Log])


  ((-> facades/facades  :slf4j :print-test) "Hello world")
  ((-> facades/facades  :jul :print-test) "Hello world")
  ((-> facades/facades  :commons-logging :print-test) "Hello world")
  ((-> facades/facades  :log4j2 :print-test) "Hello world")
  ((-> facades/facades  :log4j1 :print-test) "Hello world")
  ((-> facades/facades  :jboss-logging :print-test) "Hello world")
  ((-> facades/facades  :system-logger :print-test) "Hello world")

  )

;; (require '[clojure.java.io :as io])

;; (println org.slf4j.LoggerFactory/REQUESTED_API_VERSION)
;; (println (.getProtectionDomain org.slf4j.LoggerFactory))
;; (println (io/resource "org/slf4j/LoggerFactory.class"))

;; (System/getProperty "java.class.path")
