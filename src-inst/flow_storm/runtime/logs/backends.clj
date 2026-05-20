(ns flow-storm.runtime.logs.backends
  (:require [flow-storm.runtime.logs.utils :refer [class-exists? class-origin call call-static]]
            [clojure.string :as str]
            [clojure.set :as set])
  (:import
   [java.util.logging Logger Level]
   [java.lang System$Logger$Level]))

(def backends
  {:logback
   (let [levels [:trace :debug :info :warn :error]
         key->lvl-fn (fn [lvl-k] (call-static "ch.qos.logback.classic.Level" "toLevel" ^{:args-types ["java.lang.String"]} [(name lvl-k)]))
         lvl->key-fn (fn [lvl] (-> lvl str/lower-case keyword))]
     {:id :logback
      :present? (fn [] (class-exists? "ch.qos.logback.classic.LoggerContext"))
      :artifact 'ch.qos.logback/logback-classic
      :levels levels
      :can-set-at-runtime? true
      :key->lvl-fn key->lvl-fn
      :lvl->key-fn lvl->key-fn
      :get-root-level-fn (fn []
                           (-> (call-static "org.slf4j.LoggerFactory" "getILoggerFactory" [])
                               (.getLogger "ROOT")
                               .getLevel
                               lvl->key-fn))
      :set-root-level-fn (fn [lvl]
                           (-> (call-static "org.slf4j.LoggerFactory" "getILoggerFactory" [])
                               (.getLogger "ROOT")
                               (.setLevel (key->lvl-fn lvl))))

      :config-resources ["logback-test.xml" "logback.xml" "logback.configurationFile"]})

   :log4j2
   (let [levels [:trace :debug :info :warn :error :fatal]
         key->lvl-fn (fn [lvl-k] (call-static "org.apache.logging.log4j.Level" "toLevel" ^{:args-types ["java.lang.String"]} [(name lvl-k)]))
         lvl->key-fn (fn [lvl] (-> lvl str/lower-case keyword))]
     {:id :log4j2
      :artifact 'org.apache.logging.log4j/log4j-core
      :present?  (fn [] (class-exists? "org.apache.logging.log4j.core.LoggerContext"))
      :levels levels
      :can-set-at-runtime? true
      :key->lvl-fn key->lvl-fn
      :lvl->key-fn lvl->key-fn
      :get-root-level-fn (fn []
                           (-> (call-static "org.apache.logging.log4j.LogManager" "getContext" [false])
                               .getConfiguration
                               .getRootLogger
                               .getLevel
                               lvl->key-fn))
      :set-root-level-fn (fn [lvl]
                           (-> (call-static "org.apache.logging.log4j.LogManager" "getContext" [false])
                               .getConfiguration
                               .getRootLogger
                               (.setLevel (key->lvl-fn lvl))))
      :config-resources ["log4j2-test.properties" "log4j2-test.yaml"
                         "log4j2-test.yml" "log4j2-test.json" "log4j2-test.jsn"
                         "log4j2-test.xml" "log4j2.properties" "log4j2.yaml"
                         "log4j2.yml" "log4j2.json" "log4j2.jsn" "log4j2.xml"
                         "log4j.configurationFile"]})


   :jul
   (let [levels [:finest :finer :fine :config :info :warning :severe]
         key->lvl-fn (fn [lvl-k] (Level/parse (str/upper-case (name lvl-k))))
         lvl->key-fn (fn [lvl] (-> lvl str/lower-case keyword))]
     {:id :jul
      :present?  (fn [] (class-exists? "java.util.logging.LogManager"))
      :levels levels
      :can-set-at-runtime? true
      :key->lvl-fn key->lvl-fn
      :lvl->key-fn lvl->key-fn
      :get-root-level-fn (fn []
                           (-> (Logger/getLogger "")
                               .getLevel
                               lvl->key-fn))
      :set-root-level-fn (fn [lvl]
                           (-> (Logger/getLogger "")
                               (.setLevel (key->lvl-fn lvl))))
      :config-resources ["logging.properties"]})

   :reload4j
   (let [levels [:trace :debug :info :warn :error :fatal]
         key->lvl-fn (fn [lvl-k] (call-static  "org.apache.log4j.Level" "toLevel" ^{:args-types ["java.lang.String"]} [(name lvl-k)]))
         lvl->key-fn (fn [lvl] (-> lvl str/lower-case keyword))]
     {:id :reload4j
      :artifact 'ch.qos.reload4j/reload4j
      :present?  (fn []
                   (and
                    (class-exists? "org.apache.log4j.Logger")
                    (str/includes? (class-origin "org.apache.log4j.Logger") "reload4j")))
      :levels levels
      :can-set-at-runtime? true
      :key->lvl-fn key->lvl-fn
      :lvl->key-fn lvl->key-fn
      :get-root-level-fn (fn []
                           (-> (call-static "org.apache.log4j.Logger" "getRootLogger" [])
                               .getLevel
                               lvl->key-fn))
      :set-root-level-fn (fn [lvl]
                           (-> (call-static "org.apache.log4j.Logger" "getRootLogger" [])
                               (.setLevel (key->lvl-fn lvl))))
      :config-resources ["log4j.properties" "log4j.xml" "log4j.configuration"]})

   :slf4j
   (let [levels [:trace :debug :info :warn :error :off]
         key->lvl-fn (fn [lvl-k] (name lvl-k))
         lvl->key-fn (fn [lvl] (keyword lvl))
         config-resources ["simplelogger.properties"]]
     {:id :slf4j
      :artifact 'org.slf4j/slf4j-simple
      :present?  (fn [] (class-exists? "org.slf4j.simple.SimpleLoggerFactory"))
      :levels levels
      :can-set-at-runtime? false
      :key->lvl-fn key->lvl-fn
      :lvl->key-fn lvl->key-fn
      :get-root-level-fn (fn []
                           (when-let [lvl (System/getProperty "org.slf4j.simpleLogger.defaultLogLevel")]
                             (lvl->key-fn lvl)))
      :set-root-level-fn (fn [lvl]
                           (throw (ex-info "Not possible to set SLF4J levels at runtime. Restart the JVM with org.slf4j.simpleLogger.defaultLogLevel set to one of the levels, or put one of the config resources on the classpath"
                                           :levels levels
                                           :resources config-resources)))
      :config-resources config-resources })

   :tinylog
   (let [levels [:trace :debug :info :warn :error]
         key->lvl-fn (fn [lvl-k] (name lvl-k))
         lvl->key-fn (fn [lvl] (keyword lvl))
         config-resources ["tinylog.properties" "tinylog.configuration"]]
     {:id :tinylog
      :artifact 'org.tinylog/tinylog-impl
      :present?  (fn [] (class-exists? "org.tinylog.provider.ProviderRegistry"))
      :levels levels
      :can-set-at-runtime? false
      :key->lvl-fn key->lvl-fn
      :lvl->key-fn lvl->key-fn
      :get-root-level-fn (fn []
                           (when-let [lvl (System/getProperty "tinylog.level")]
                             (lvl->key-fn (str/lower-case lvl))))
      :set-root-level-fn (fn [lvl]
                           (throw (ex-info "Not possible to set tinylog levels at runtime. Restart the JVM with tinylog.level set to one of the levels, or put one of the config resources on the classpath"
                                           :levels levels
                                           :resources config-resources)))
      :config-resources config-resources})

   :slf4j-nop
   {:id :slf4j-nop
    :present? (fn [] )
    :levels []}})
