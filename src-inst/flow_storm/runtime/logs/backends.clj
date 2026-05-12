(ns flow-storm.runtime.logs.backends
  (:require [flow-storm.runtime.logs.utils :refer [class-exists?]]))

(def backends
  {:logback ;; :maven "ch.qos.logback/logback-classic"
   {:id :logback
    :present?  (fn [] (class-exists? "ch.qos.logback.classic.LoggerContext"))
    :root-logger "ch.qos.logback.classic.Logger/ROOT_LOGGER_NAME"
    :levels [:trace :debug :info :warn :error :off]
    :config-resources ["logback-test.xml" "logback.xml" "logback.configurationFile"]}

   :log4j2 ;; :maven "org.apache.logging.log4j/log4j-core"
   {:id :log4j2
    :present?  (fn [] (class-exists? "org.apache.logging.log4j.core.LoggerContext"))
    :levels [:all :trace :debug :info :warn :error :fatal :off]
    :config-resources ["log4j2-test.properties" "log4j2-test.yaml"
                       "log4j2-test.yml" "log4j2-test.json" "log4j2-test.jsn"
                       "log4j2-test.xml" "log4j2.properties" "log4j2.yaml"
                       "log4j2.yml" "log4j2.json" "log4j2.jsn" "log4j2.xml"
                       "log4j.configurationFile"]}

   :jul
   {:id :jul
    :present?  (fn [] (class-exists? "java.util.logging.LogManager"))
    :levels [:all :finest :finer :fine :config :info :warning :severe :off]
    :config-resources ["logging.properties"]}

   :reload4j ;;   :maven "ch.qos.reload4j/reload4j"
   {:id :reload4j
    :present?  (fn [] (class-exists? "org.apache.log4j.Logger"))
    :levels [:all :trace :debug :info :warn :error :fatal :off]
    :config-resources ["log4j.properties" "log4j.xml" "log4j.configuration"]}

   :slf4j ;; :maven "org.slf4j/slf4j-simple"
   {:id :slf4j
    :present?  (fn [] (class-exists? "org.slf4j.simple.SimpleLoggerFactory"))
    :levels [:trace :debug :info :warn :error :off]
    :config-resources ["simplelogger.properties"]}

   :tinylog ;; :maven "org.tinylog/tinylog-impl"
   {:id :tinylog
    :present?  (fn [] (class-exists? "org.tinylog.provider.ProviderRegistry"))
    :levels [:trace :debug :info :warn :error :off]
    :config-resources ["tinylog.properties" "tinylog.configuration"]}})
