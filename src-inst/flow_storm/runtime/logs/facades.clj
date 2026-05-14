(ns flow-storm.runtime.logs.facades
  (:require [flow-storm.runtime.logs.utils :refer [class-exists? call-static call class-origin]]
            [clojure.string :as str]
            [flow-storm.runtime.logs.utils :as log-utils])
  (:import
   [java.util.logging Logger Level]
   [java.lang System$Logger$Level]))

(def facades
  {:slf4j
   (let [levels [:trace :debug :info :warn :error]]
     {:id :slf4j
      :artifact 'org.slf4j/slf4j-api
      :present?  (fn [] (class-exists? "org.slf4j.LoggerFactory"))
      :levels levels
      :log-test-fn (fn print-test-slf4j [msg]
                       (let [logger (call-static "org.slf4j.LoggerFactory"
                                                 "getLogger"
                                                 ^{:args-types ["java.lang.String"]} ["probe.slf4j"])]
                         (doseq [lvl levels]
                           (call logger (name lvl) [(str "[Logging on SLF4J facade at " lvl "] " msg)]))))
      :current-backend-fn (fn []
                            (let [clazz (class (org.slf4j.LoggerFactory/getILoggerFactory))]
                              (case (.getName clazz)
                                "ch.qos.logback.classic.LoggerContext"         :logback
                                "org.slf4j.jul.JDK14LoggerFactory"             :jul
                                "org.apache.logging.slf4j.Log4jLoggerFactory"  :log4j2
                                "org.slf4j.reload4j.Reload4jLoggerFactory"     :reload4j
                                "org.slf4j.simple.SimpleLoggerFactory"         :slf4j
                                "org.slf4j.nop.NOPLoggerFactory"               :nop
                                "org.tinylog.slf4j.ModernTinylogLoggerFactory" :tinylog
                                (throw (ex-info "Unhandled backend detection case for :slf4j facade" {:class clazz})))))
      :possible-backends {:logback  {:desc "Add dependency"
                                     :needs-artifacts ['ch.qos.logback/logback-classic]
                                     :init (fn [])}
                          :log4j2   {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-slf4j2-impl]
                                     :init (fn [])}
                          :jul      {:desc "Add dependency"
                                     :needs-artifacts ['org.slf4j/slf4j-jdk14]
                                     :init (fn [])}
                          :reload4j {:desc "Add dependency"
                                     :needs-artifacts ['org.slf4j/slf4j-reload4j]
                                     :init (fn [])}
                          :slf4j    {:desc "Add dependency"
                                     :needs-artifacts ['org.slf4j/slf4j-simple]
                                     :init (fn [])}
                          :tinylog  {:desc "Add dependency"
                                     :needs-artifacts ['org.tinylog/slf4j-tinylog]
                                     :init (fn [])}}})

   :jul
   (let [levels [Level/FINEST Level/FINER Level/FINE Level/CONFIG Level/INFO Level/WARNING Level/SEVERE]]
     {:id :jul
      :present?  (fn [] (class-exists? "java.util.logging.Logger"))
      :levels (mapv #(-> % str str/lower-case keyword) levels)
      :log-test-fn (fn print-test-jul [msg]
                       (let [logger (Logger/getLogger "probe.jul")]
                         (doseq [lvl levels]
                           (.log logger lvl (str "[Logging on JUL facade at " (str lvl) "] " msg)))))
      :current-backend-fn (fn []
                            (let [log-manager-class-name (.getName (class (java.util.logging.LogManager/getLogManager)))
                                  first-handler-class-name (.getName (class (first (.getHandlers (java.util.logging.Logger/getLogger "")))))]
                              (cond
                                (= log-manager-class-name "org.apache.logging.log4j.jul.LogManager")
                                :log4j2

                                (= first-handler-class-name "org.slf4j.bridge.SLF4JBridgeHandler")
                                :slf4j

                                (and (= log-manager-class-name "java.util.logging.LogManager")
                                     (= first-handler-class-name "java.util.logging.ConsoleHandler"))
                                :jul

                                (= first-handler-class-name "org.tinylog.jul.TinylogHandler")
                                :tinylog

                                :else
                                (throw (ex-info "Unhandled backend detection case for :jul facade" {:first-handler-class-name first-handler-class-name
                                                                                                    :log-manager-class-name log-manager-class-name})))))
      :possible-backends {:logback  {:desc "Use jul -> slf4j -> logback"
                                     :needs-artifacts [[:facade :slf4j] 'org.slf4j/jul-to-slf4j [:backend :logback]]
                                     :init (fn [])}
                          :log4j2   {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-jul]
                                     :init (fn [])}
                          :jul      {:desc "Built in"
                                     :init (fn [])}
                          :reload4j {:desc "jul -> slf4j -> reload4j"
                                     :needs-artifacts [[:facade :slf4j] 'org.slf4j/jul-to-slf4j [:backend :reload4j]]
                                     :init (fn [])}
                          :slf4j    {:desc "Add dependency"
                                     :needs-artifacts ['org.slf4j/jul-to-slf4j]
                                     :init (fn []
                                             (call-static "org.slf4j.bridge.SLF4JBridgeHandler" "removeHandlersForRootLogger" [])
                                             (call-static "org.slf4j.bridge.SLF4JBridgeHandler" "install" []))}
                          :tinylog  {:desc "Add dependency"
                                     :needs-artifacts ['org.tinylog/jul-tinylog]
                                     :init (fn [])}}})

   :commons-logging
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :commons-logging
      :artifact 'commons-logging/commons-logging
      :present?  (fn [] (class-exists? "org.apache.commons.logging.LogFactory"))
      :levels levels
      :log-test-fn (fn print-test-commons-logging [msg]
                    (let [logger (call-static "org.apache.commons.logging.LogFactory"
                                              "getLog"
                                              ^{:args-types ["java.lang.String"]} ["probe.slf4j"])]
                      (doseq [lvl levels]
                        (let [args ^{:args-types ["java.lang.String"]} [(str "[Logging on COMMONS-LOGGING facade at " lvl "] " msg)]
                              lvl-method (log-utils/find-method (Class/forName "org.apache.commons.logging.Log")
                                                                (name lvl)
                                                                args)]
                          (.invoke lvl-method
                                 logger
                                 (to-array args))))))
      :current-backend-fn (fn []
                            (let [clazz (class (call-static "org.apache.commons.logging.LogFactory" "getLog" ["probe"]))]
                              (case (.getName clazz)
                                "org.apache.commons.logging.impl.Log4jApiLogFactory" :log4j2
                                "org.apache.commons.logging.impl.Jdk14Logger" :jul
                                "org.apache.commons.logging.impl.Log4JLogger" :reload4j
                                "org.apache.commons.logging.impl.SLF4JLog" :slf4j
                                "org.tinylog.jcl.TinylogLog" :tinylog
                                (throw (ex-info "Unhandled backend detection case for :commons-logging facade" {:class clazz})))))
      :possible-backends {:logback  {:desc "Use commons-logging -> slf4j -> logback"
                                     :needs-artifacts [[:facade :slf4j] 'org.slf4j/jcl-over-slf4j [:backend :logback]]
                                     :init (fn [])}
                          :log4j2   {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-jcl]
                                     :init (fn [])}
                          :jul      {:desc "Built in commons logging"
                                     :init (fn [])}
                          :reload4j {:desc "Built in commons logging"
                                     :init (fn [])}
                          :slf4j    {:desc "Add dependency"
                                     :needs-artifacts ['org.slf4j/jcl-over-slf4j]
                                     :init (fn [])}
                          :tinylog  {:desc "Add dependency"
                                     :needs-artifacts ['org.tinylog/jcl-tinylog]
                                     :init (fn [])}}})

   :log4j2
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :log4j2
      :artifact 'org.apache.logging.log4j/log4j-api
      :present?  (fn [] (class-exists? "org.apache.logging.log4j.LogManager"))
      :levels levels
      :log-test-fn (fn print-test-log4j2 [msg]
                       (let [logger (call-static "org.apache.logging.log4j.LogManager"
                                                 "getLogger"
                                                 ^{:args-types ["java.lang.String"]} ["probe.log4j2"])]
                         (doseq [lvl levels]
                           (call logger (name lvl) ^{:args-types ["java.lang.String"]} [(str "[Logging on LOG4J2 facade at " lvl "] " msg)]))))
      :current-backend-fn (fn []
                            (let [clazz (class (call-static "org.apache.logging.log4j.LogManager" "getFactory" []))]
                              (case (.getName clazz)
                                "org.apache.logging.log4j.core.impl.Log4jContextFactory" :log4j2
                                "org.apache.logging.slf4j.SLF4JLoggerContextFactory" :slf4j
                                ;;"" :jul
                                ;; "" :reload4j
                                (throw (ex-info "Unhandled backend detection case for :log4j2 facade" {:class clazz})))))
      :possible-backends {:logback  {:desc "Use log4j2 -> slf4j -> logback"
                                     :needs-artifacts [[:facade :slf4j] 'org.apache.logging.log4j/log4j-to-slf4j [:backend :logback]]
                                     :init (fn [])}
                          :log4j2   {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-core]
                                     :init (fn [])}
                          :jul      {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-to-jul]
                                     :init (fn [])}
                          :reload4j {:desc "Not sure it is possible..."}
                          :slf4j    {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-to-slf4j]
                                     :init (fn [])}
                          :tinylog  {:desc "Use log4j2 -> slf4j -> tinylog"
                                     :needs-artifacts [[:facade :slf4j] [:backend :tinylog]]
                                     :init (fn [])}}})

   :reload4j
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :reload4j
      :artifact 'ch.qos.reload4j/reload4j
      :present?  (fn []
                   (and (class-exists? "org.apache.log4j.Logger")
                        (str/includes? (class-origin "org.apache.log4j.Logger") "reload4j")))
      :levels levels
      :log-test-fn (fn print-test-log4j1 [msg]
                       (let [logger (call-static "org.apache.log4j.Logger"
                                                 "getLogger"
                                                 ^{:args-types ["java.lang.String"]} ["probe.log4j1"])]
                         (doseq [lvl levels]
                           (call logger (name lvl) [(str "[Logging on LOG4J1(reload4j) facade at " lvl "] " msg)]))))
      :current-backend-fn (fn []
                            (let [origin (class-origin "org.apache.log4j.Logger")]
                              (cond
                                (= origin "org.apache.logging.log4j/log4j-1.2-api") :log4j2
                                (= origin "org.slf4j/log4j-over-slf4j")             :slf4j
                                (= origin "org.tinylog/log4j1.2-api")               :tinylog
                                (str/includes? origin "reload4j")                   :reload4j
                                :else (throw (ex-info "Unhandled backend detection case for :log4j1 facade" {:origin origin})))))
      :possible-backends {:logback  {:desc "Use reload4j(log4j1) -> slf4j -> logback"
                                     :needs-artifacts [[:facade :slf4j] 'org.slf4j/log4j-over-slf4j [:backend :logback]]
                                     :init (fn [])}
                          :log4j2   {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-1.2-api]
                                     :init (fn [])}
                          :jul      {:desc "Use reload4j(log4j1) -> slf4j -> jul"
                                     :needs-artifacts [[:facade :slf4j] 'org.slf4j/log4j-over-slf4j 'org.slf4j/slf4j-jdk14]
                                     :init (fn [])}
                          :reload4j {:desc "Add dependency"
                                     :needs-artifacts ['ch.qos.reload4j/reload4j]
                                     :init (fn [])}
                          :slf4j    {:desc "Add dependency"
                                     :needs-artifacts ['org.slf4j/log4j-over-slf4j]
                                     :init (fn [])}
                          :tinylog  {:desc "Add dependency"
                                     :needs-artifacts ['org.tinylog/log4j1.2-api]
                                     :init (fn [])}}})

   :jboss-logging
   (let [levels [:trace :debug :info :warn :error :fatal]]
     {:id :jboss-logging
      :artifact 'org.jboss.logging/jboss-logging
      :present?  (fn [] (class-exists? "org.jboss.logging.Logger"))
      :levels levels
      :log-test-fn (fn print-test-jboss-logging [msg]
                       (let [logger (call-static "org.jboss.logging.Logger"
                                                 "getLogger"
                                                 ^{:args-types ["java.lang.String"]} ["probe.jboss-logging"])]
                         (doseq [lvl levels]
                           (call logger (name lvl) [(str "[Logging on JBOSS-LOGGING facade at " lvl "] " msg)]))))
      :current-backend-fn (fn []
                            (let [provider-name (class (call-static "org.jboss.logging.LoggerProviders" "PROVIDER" []))]
                              (case provider-name
                                "org.jboss.logging.Log4jLoggerProvider" :reload4j
                                "org.jboss.logging.Log4j2LoggerProvider" :log4j2
                                "org.jboss.logging.JDKLoggerProvider" :jul
                                "org.jboss.logging.Slf4jLoggerProvider" :slf4j
                                "org.jboss.logging.TinylogLoggerProvider" :tinylog

                                (throw (ex-info "Unhandled backend detection case for :jboss-logging facade" {:provider-name provider-name})))))
      :possible-backends {:logback  {:desc "Use jboss -> slf4j -> logback"
                                     :needs-artifacts [[:facade :slf4j] [:backend :logback]]
                                     :init (fn [])}
                          :log4j2   {:desc "Built into jboss logging"
                                     :init (fn [])}
                          :jul      {:desc "Built into jboss logging"
                                     :init (fn [])}
                          :reload4j {:desc "Built into jboss logging"
                                     :init (fn [])}
                          :slf4j    {:desc "Built into jboss logging"
                                     :init (fn [])}
                          :tinylog  {:desc "Add dependency"
                                     :needs-artifacts ['org.tinylog/jboss-tinylog]
                                     :init (fn [])}}})

   :system-logger
   (let [levels [System$Logger$Level/TRACE System$Logger$Level/DEBUG System$Logger$Level/INFO System$Logger$Level/WARNING System$Logger$Level/ERROR]]
     {:id :system-logger
      :present?  (fn [] (class-exists? "java.lang.System$Logger"))
      :levels (mapv #(-> % str/lower-case keyword) levels)
      :log-test-fn (fn print-test-system-logger [msg]
                       (let [logger (System/getLogger "probe.system-logger")]
                         (doseq [lvl levels]
                           (.log logger lvl (str "[Logging on SYSTEM-LOGGER facade at " (str lvl) "] " msg)))))
      :current-backend-fn (fn []
                            (let [class-name (class (System/getLogger "probe"))]
                              (cond
                                (= class-name "sun.util.logging.internal.LoggingProviderImpl$JULWrapper") :jul
                                (contains? class-name "org.apache.logging.log4j.jpl") :log4j2
                                (contains? class-name "org.slf4j.jdk.platform.logging") :slf4j
                                (contains? class-name "org.tinylog.jsl") :tinylog
                                :else (throw (ex-info "Unhandled backend detection case for :jboss-logging facade" {:class-name class-name})))))
      :possible-backends {:logback  {:desc "Use system logger -> slf4j -> logback"
                                     :needs-artifacts [[:facade :slf4j] [:backend :logback]]
                                     :init (fn [])}
                          :log4j2   {:desc "Add dependency"
                                     :needs-artifacts ['org.apache.logging.log4j/log4j-jpl]
                                     :init (fn [])}
                          :jul      {:desc "Built in"
                                     :init (fn [])}
                          :reload4j {:desc "Use system logger -> slf4j -> reload4j"
                                     :needs-artifacts [[:facade :slf4j] [:backend :reload4j]]
                                     :init (fn [])}
                          :slf4j    {:desc "Add dependency"
                                     :needs-artifacts ['org.slf4j/slf4j-jdk-platform-logging]
                                     :init (fn [])}
                          :tinylog  {:desc "Add dependency"
                                     :needs-artifacts ['org.tinylog/jsl-tinylog]
                                     :init (fn [])}}})})
