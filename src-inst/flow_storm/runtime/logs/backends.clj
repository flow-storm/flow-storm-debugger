(ns flow-storm.runtime.logs.backends
  (:require [flow-storm.runtime.logs.utils :refer [class-exists?]]))

(def backends
  {:logback ;; :maven "ch.qos.logback/logback-classic"
   {:id :logback
    :present?  (fn [] (class-exists? "ch.qos.logback.classic.LoggerContext"))
    :root-logger "ch.qos.logback.classic.Logger/ROOT_LOGGER_NAME"
    :levels [:trace]}

   :log4j2 ;; :maven "org.apache.logging.log4j/log4j-core"
   {:id :log4j2
    :present?  (fn [] (class-exists? "org.apache.logging.log4j.core.LoggerContext"))
    :levels [:all]}

   :jul
   {:id :jul
    :present?  (fn [] (class-exists? "java.util.logging.LogManager"))
    :levels [:all]}

   :reload4j ;;   :maven "ch.qos.reload4j/reload4j"
   {:id :reload4j
    :present?  (fn [] (class-exists? "org.apache.log4j.Logger"))
    :levels [:all]}

   :slf4j ;; :maven "org.slf4j/slf4j-simple"
   {:id :slf4j
    :present?  (fn [] (class-exists? "org.slf4j.simple.SimpleLoggerFactory"))
    :levels [:trace]
    :config-resources ["simplelogger.properties"]}

   :tinylog ;; :maven "org.tinylog/tinylog-impl"
   {:id :tinylog
    :present?  (fn [] (class-exists? "org.tinylog.provider.ProviderRegistry"))
    :levels [:trace]
    :config-resources ["tinylog.properties"]}})
