(ns flow-storm.runtime.logs.facades
  (:require [flow-storm.runtime.logs.utils :refer [class-exists? call-static call]]
            [clojure.string :as str]
            [flow-storm.runtime.logs.utils :as log-utils])
  (:import
   [java.util.logging Logger Level]
   [java.lang System$Logger$Level]))

(def facades
  {:slf4j ;; :maven "org.slf4j/slf4j-api"
   (let [levels [:trace :debug :info :warn :error]]
     {:id :slf4j
      :present?  (fn [] (class-exists? "org.slf4j.LoggerFactory"))
      :levels levels
      :print-test (fn print-test-slf4j [msg]
                    (let [logger (call-static "org.slf4j.LoggerFactory"
                                              "getLogger"
                                              ^{:args-types ["java.lang.String"]} ["probe.slf4j"])]
                      (doseq [lvl levels]
                        (call logger (name lvl) [(str "[SLF4J " lvl "] " msg)]))))})

   :jul
   (let [levels [Level/FINEST Level/FINER Level/FINE Level/CONFIG Level/INFO Level/WARNING Level/SEVERE]]
     {:id :jul
      :present?  (fn [] (class-exists? "java.util.logging.Logger"))
      :levels (mapv #(-> % str str/lower-case keyword) levels)
      :print-test (fn print-test-jul [msg]
                    (let [logger (Logger/getLogger "probe.jul")]
                      (doseq [lvl levels]
                        (.log logger lvl (str "[JUL " (str lvl) "] " msg)))))})

   :commons-logging ;; :maven "commons-logging/commons-logging"
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :commons-logging
      :present?  (fn [] (class-exists? "org.apache.commons.logging.LogFactory"))
      :levels levels
      :print-test (fn print-test-commons-logging [msg]
                    (let [logger (call-static "org.apache.commons.logging.LogFactory"
                                              "getLog"
                                              ^{:args-types ["java.lang.String"]} ["probe.slf4j"])]
                      (doseq [lvl levels]
                        (let [args ^{:args-types ["java.lang.String"]} [(str "[COMMONS-LOGGING " lvl "] " msg)]
                              lvl-method (log-utils/find-method (Class/forName "org.apache.commons.logging.Log")
                                                                (name lvl)
                                                                args)]
                          (.invoke lvl-method
                                 logger
                                 (to-array args))))))})

   :log4j2 ;; :maven "org.apache.logging.log4j/log4j-api"
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :log4j2
      :present?  (fn [] (class-exists? "org.apache.logging.log4j.LogManager"))
      :levels levels
      :print-test (fn print-test-log4j2 [msg]
                    (let [logger (call-static "org.apache.logging.log4j.LogManager"
                                              "getLogger"
                                              ^{:args-types ["java.lang.String"]} ["probe.log4j2"])]
                      (doseq [lvl levels]
                        (call logger (name lvl) ^{:args-types ["java.lang.String"]} [(str "[LOG4J2 " lvl "] " msg)]))))})

   :log4j1 ;; :maven "log4j/log4j"
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :log4j1
      :present?  (fn [] (class-exists? "org.apache.log4j.Logger"))
      :levels levels
      :print-test (fn print-test-log4j1 [msg]
                    (let [logger (call-static "org.apache.log4j.Logger"
                                              "getLogger"
                                              ^{:args-types ["java.lang.String"]} ["probe.log4j1"])]
                      (doseq [lvl levels]
                        (call logger (name lvl) [(str "[LOG4J1 " lvl "] " msg)]))))})

   :jboss-logging ;; :maven "org.jboss.logging/jboss-logging"
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :jboss-logging
      :present?  (fn [] (class-exists? "org.jboss.logging.Logger"))
      :levels levels
      :print-test (fn print-test-jboss-logging [msg]
                    (let [logger (call-static "org.jboss.logging.Logger"
                                              "getLogger"
                                              ^{:args-types ["java.lang.String"]} ["probe.jboss-logging"])]
                      (doseq [lvl levels]
                        (call logger (name lvl) [(str "[JBOSS-LOGGING " lvl "] " msg)]))))})

   :system-logger
   (let [levels [System$Logger$Level/TRACE System$Logger$Level/DEBUG System$Logger$Level/INFO System$Logger$Level/WARNING System$Logger$Level/ERROR]]
     {:id :system-logger
      :present?  (fn [] (class-exists? "java.lang.System$Logger"))
      :levels (mapv #(-> % str/lower-case keyword) levels)
      :print-test (fn print-test-system-logger [msg]
                    (let [logger (System/getLogger "probe.system-logger")]
                      (doseq [lvl levels]
                        (.log logger lvl (str "[SYSTEM-LOGGER " (str lvl) "] " msg)))))})})
