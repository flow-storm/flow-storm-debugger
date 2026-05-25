(ns flow-storm.runtime.logs.bridges
  (:require [flow-storm.runtime.logs.utils :refer [class-exists? call-static class-origin-matches? origin-version any-class-exists?]]))

(def bridges
  {;;;;;;;;;;;;;;;;;;;
   ;; SLF4J bridges ;;
   ;;;;;;;;;;;;;;;;;;;

   [:slf4j :logback]
   (let [presence-class "ch.qos.logback.classic.spi.LogbackServiceProvider"]
     {:artifact :included-in-backend
      :present? (fn [] (class-exists? presence-class))
      :version-fn (fn [] (origin-version presence-class "ch/qos/logback" "logback-classic"))
      :backend-for-facade-major {}})

   [:slf4j :log4j2]
   (let [presence-class "org.apache.logging.slf4j.SLF4JServiceProvider"]
     {:artifact 'org.apache.logging.log4j/log4j-slf4j2-impl
      :present? (fn [] (class-exists? presence-class))
      :version-fn (fn [] (origin-version presence-class "org/apache/logging/log4j" "log4j-slf4j2-impl"))})

   [:slf4j :jul]
   (let [presence-class "org.slf4j.jul.JDK14LoggerFactory"]
     {:artifact 'org.slf4j/slf4j-jdk14
      :present? (fn [] (class-exists? presence-class))
      :version-fn (fn [] (origin-version presence-class "org/slf4j" "slf4j-jdk14"))})

   [:slf4j :reload4j]
   (let [presence-class "org.slf4j.reload4j.Reload4jLoggerFactory"]
     {:artifact 'org.slf4j/slf4j-reload4j
      :present? (fn [] (class-exists? presence-class))
      :version-fn (fn [] (origin-version presence-class "org/slf4j" "slf4j-reload4j"))})

   [:slf4j :slf4j]
   {:artifact :included-in-backend
    :present? (fn []
                (any-class-exists?
                 "org.slf4j.simple.SimpleLoggerFactory"
                 "org.slf4j.nop.NOPLoggerFactory"))}

   [:slf4j :tinylog]
   (let [classes ["org.tinylog.slf4j.SLF4JServiceProvider"
                  "org.tinylog.slf4j.TinylogLoggerFactory"]]
     {:artifact 'org.tinylog/slf4j-tinylog
      :present? (fn [] (apply any-class-exists? classes))
      :version-fn (fn []
                    (some #(origin-version % "org/tinylog" "slf4j-tinylog")
                          classes))})


   ;;;;;;;;;;;;;;;;;
   ;; JUL bridges ;;
   ;;;;;;;;;;;;;;;;;

   [:jul :logback]
   {:via-slf4j? true
    :present?  (fn []
                 (and ((:present? (get bridges [:jul :slf4j])))
                      ((:present? (get bridges [:slf4j :logback])))))}

   [:jul :log4j2]
   (let [presence-class "org.apache.logging.log4j.jul.LogManager"]
     {:artifact 'org.apache.logging.log4j/log4j-jul
      :present?  (fn [] (class-exists? presence-class))
      :version-fn (fn [] (origin-version presence-class "org/apache/logging/log4j" "log4j-jul"))
      :init-note "Requires -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager before JUL initializes."})

   [:jul :jul]
   {:present?  (constantly true)}

   [:jul :reload4j]
   {:via-slf4j? true
    :present?  (fn []
                 (and ((:present? (get bridges [:jul :slf4j])))
                      ((:present? (get bridges [:slf4j :reload4j])))))}

   [:jul :slf4j]
   (let [presence-class "org.slf4j.bridge.SLF4JBridgeHandler"]
     {:artifact 'org.slf4j/jul-to-slf4j
      :present?  (fn [] (class-exists? presence-class))
      :version-fn (fn []
                    (origin-version presence-class
                                    "org/slf4j"
                                    "jul-to-slf4j"))
      :init (fn []
              (call-static "org.slf4j.bridge.SLF4JBridgeHandler"
                           "removeHandlersForRootLogger")
              (call-static "org.slf4j.bridge.SLF4JBridgeHandler"
                           "install"))})

   [:jul :tinylog]
   (let [presence-class "org.tinylog.jul.TinylogHandler"]
     {:artifact 'org.tinylog/jul-tinylog
      :present?  (fn [] (class-exists? presence-class))
      :version-fn (fn []
                    (origin-version presence-class
                                    "org/tinylog"
                                    "jul-tinylog"))})


   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Commons logging bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:commons-logging :logback]
   {:via-slf4j? true
    :present?  (fn []
                 (and ((:present? (get bridges [:commons-logging :slf4j])))
                      ((:present? (get bridges [:slf4j :logback])))))}

   [:commons-logging :log4j2]
   (let [presence-class "org.apache.logging.log4j.jcl.LogFactoryImpl"]
     {:artifact 'org.apache.logging.log4j/log4j-jcl
      :present?  (fn [] (class-exists? presence-class))
      :version-fn (fn []
                    (origin-version presence-class
                                    "org/apache/logging/log4j"
                                    "log4j-jcl"))})

   [:commons-logging :jul]
   {:present?  (fn []
                 (class-exists? "org.apache.commons.logging.impl.Jdk14Logger"))
    :built-in-backend? true}

   [:commons-logging :reload4j]
   {:present?  (fn []
                 (and (class-exists? "org.apache.commons.logging.impl.Log4JLogger")
                      (class-origin-matches? "org.apache.log4j.Logger" #"reload4j")))
    :built-in-backend? true}

   [:commons-logging :slf4j]
   (let [presence-class "org.apache.commons.logging.impl.SLF4JLogFactory"]
     {:artifact 'org.slf4j/jcl-over-slf4j
      :present?  (fn [] (class-exists? presence-class))
      :version-fn (fn []
                    (origin-version presence-class
                                    "org/slf4j"
                                    "jcl-over-slf4j"))})

   [:commons-logging :tinylog]
   (let [presence-class "org.tinylog.jcl.TinylogLog"]
     {:artifact 'org.tinylog/jcl-tinylog
      :present?  (fn [] (class-exists? presence-class))
      :version-fn (fn []
                    (origin-version presence-class
                                    "org/tinylog"
                                    "jcl-tinylog"))})


   ;;;;;;;;;;;;;;;;;;;;
   ;; Log4j2 bridges ;;
   ;;;;;;;;;;;;;;;;;;;;

   [:log4j2 :logback]
   {:via-slf4j? true
    :present? (fn []
                (and ((:present? (get bridges [:log4j2 :slf4j])))
                     ((:present? (get bridges [:slf4j :logback])))))}

   [:log4j2 :log4j2]
   (let [presence-class "org.apache.logging.log4j.core.impl.Log4jContextFactory"]
     {:present? (fn [] (class-exists? presence-class))
      :built-in-backend? true
      :version-fn (fn []
                    (origin-version presence-class
                                    "org/apache/logging/log4j"
                                    "log4j-core"))})

   ;; I would keep this false unless you have a custom bridge.
   ;; Log4j has log4j-jul for JUL -> Log4j2, not commonly Log4j2 -> JUL.
   [:log4j2 :jul]
   {:present? (constantly false)
    :artifact nil
    :note "No common official direct Log4j2 API -> JUL bridge. Use log4j2->slf4j->jul if desired."}

   [:log4j2 :slf4j]
   (let [presence-class "org.apache.logging.slf4j.SLF4JLoggerContextFactory"]
     {:artifact 'org.apache.logging.log4j/log4j-to-slf4j
      :present? (fn [] (class-exists? presence-class))
      :version-fn (fn []
                    (origin-version presence-class
                                    "org/apache/logging/log4j"
                                    "log4j-to-slf4j"))})

   [:log4j2 :tinylog]
   {:via-slf4j? true
    :present? (fn []
                (and ((:present? (get bridges [:log4j2 :slf4j])))
                     ((:present? (get bridges [:slf4j :tinylog])))))}


   ;;;;;;;;;;;;;;;;;;;;;;
   ;; Reload4j bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;

   [:reload4j :logback]
   {:via-slf4j? true
    :present? (fn []
                (and ((:present? (get bridges [:reload4j :slf4j])))
                     ((:present? (get bridges [:slf4j :logback])))))}

   [:reload4j :log4j2]
   {:artifact 'org.apache.logging.log4j/log4j-1.2-api
    :present? (fn []
                (class-origin-matches? "org.apache.log4j.Logger"
                                       #"log4j-1\.2-api"))
    :version-fn (fn []
                  (origin-version "org.apache.log4j.Logger"
                                  "org/apache/logging/log4j"
                                  "log4j-1.2-api"))}

   [:reload4j :jul]
   {:via-slf4j? true
    :present? (fn []
                (and ((:present? (get bridges [:reload4j :slf4j])))
                     ((:present? (get bridges [:slf4j :jul])))))}

   [:reload4j :reload4j]
   {:artifact 'ch.qos.reload4j/reload4j
    :present? (fn []
                (class-origin-matches? "org.apache.log4j.Logger"
                                       #"reload4j"))
    :version-fn (fn []
                  (origin-version "org.apache.log4j.Logger"
                                  "ch/qos/reload4j"
                                  "reload4j"))}

   [:reload4j :slf4j]
   {:artifact 'org.slf4j/log4j-over-slf4j
    :present? (fn []
                (class-origin-matches? "org.apache.log4j.Logger"
                                       #"log4j-over-slf4j"))
    :version-fn (fn []
                  (origin-version "org.apache.log4j.Logger"
                                  "org/slf4j"
                                  "log4j-over-slf4j"))}

   [:reload4j :tinylog]
   {:artifact 'org.tinylog/log4j1.2-api
    :present? (fn []
                (class-origin-matches? "org.apache.log4j.Logger"
                                       #"log4j1\.2-api"))
    :version-fn (fn []
                  (origin-version "org.apache.log4j.Logger"
                                  "org/tinylog"
                                  "log4j1.2-api"))}


   ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; JBoss logging bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:jboss-logging :logback]
   {:via-slf4j? true
    :present? (fn []
                (and ((:present? (get bridges [:jboss-logging :slf4j])))
                     ((:present? (get bridges [:slf4j :logback])))))}

   [:jboss-logging :log4j2]
   {:built-in-backend? true
    :present? (fn []
                (class-exists? "org.jboss.logging.Log4j2LoggerProvider"))}

   [:jboss-logging :jul]
   {:built-in-backend? true
    :present? (fn []
                (class-exists? "org.jboss.logging.JDKLoggerProvider"))}

   [:jboss-logging :reload4j]
   {:built-in-backend? true
    :present? (fn []
                (and (class-exists? "org.jboss.logging.Log4jLoggerProvider")
                     (class-origin-matches? "org.apache.log4j.Logger"
                                            #"reload4j")))}

   [:jboss-logging :slf4j]
   {:built-in-backend? true
    :present? (fn []
                (class-exists? "org.jboss.logging.Slf4jLoggerProvider"))}

   [:jboss-logging :tinylog]
   {:artifact 'org.tinylog/jboss-tinylog
    :present? (fn []
                (any-class-exists?
                 "org.tinylog.jboss.TinylogLoggerProvider"
                 "org.tinylog.jboss.TinylogLogger"))
    :version-fn (fn []
                  (or (origin-version "org.tinylog.jboss.TinylogLoggerProvider"
                                      "org/tinylog"
                                      "jboss-tinylog")
                      (origin-version "org.tinylog.jboss.TinylogLogger"
                                      "org/tinylog"
                                      "jboss-tinylog")))}


   ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; System logger bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:system-logger :logback]
   {:via-slf4j? true
    :present? (fn []
                (and ((:present? (get bridges [:system-logger :slf4j])))
                     ((:present? (get bridges [:slf4j :logback])))))}

   [:system-logger :log4j2]
   {:artifact 'org.apache.logging.log4j/log4j-jpl
    :present? (fn []
                (or (class-exists? "org.apache.logging.log4j.jpl.Log4jSystemLoggerFinder")
                    (some-> (class System/out) str boolean false)))
    :service-resource "META-INF/services/java.lang.System$LoggerFinder"
    :version-fn (fn []
                  (origin-version "org.apache.logging.log4j.jpl.Log4jSystemLoggerFinder"
                                  "org/apache/logging/log4j"
                                  "log4j-jpl"))}

   [:system-logger :jul]
   {:present? (fn []
                ;; Built-in fallback. This doesn't prove it is active,
                ;; only that JUL is always available as the default JDK path.
                (class-exists? "java.lang.System$LoggerFinder"))
    :built-in-backend? true}

   [:system-logger :reload4j]
   {:via-slf4j? true
    :present? (fn []
                (and ((:present? (get bridges [:system-logger :slf4j])))
                     ((:present? (get bridges [:slf4j :reload4j])))))}

   [:system-logger :slf4j]
   {:artifact 'org.slf4j/slf4j-jdk-platform-logging
    :present? (fn []
                (any-class-exists?
                 "org.slf4j.jdk.platform.logging.SLF4JSystemLoggerFinder"
                 "org.slf4j.jdk.platform.logging.SLF4JPlatformLoggerFinder"))
    :service-resource "META-INF/services/java.lang.System$LoggerFinder"
    :version-fn (fn []
                  (or (origin-version "org.slf4j.jdk.platform.logging.SLF4JSystemLoggerFinder"
                                      "org/slf4j"
                                      "slf4j-jdk-platform-logging")
                      (origin-version "org.slf4j.jdk.platform.logging.SLF4JPlatformLoggerFinder"
                                      "org/slf4j"
                                      "slf4j-jdk-platform-logging")))}

   [:system-logger :tinylog]
   {:artifact 'org.tinylog/jsl-tinylog
    :present? (fn []
                (any-class-exists?
                 "org.tinylog.jsl.TinylogLoggerFinder"
                 "org.tinylog.jsl.SystemLoggerFinder"))
    :service-resource "META-INF/services/java.lang.System$LoggerFinder"
    :version-fn (fn []
                  (or (origin-version "org.tinylog.jsl.TinylogLoggerFinder"
                                      "org/tinylog"
                                      "jsl-tinylog")
                      (origin-version "org.tinylog.jsl.SystemLoggerFinder"
                                      "org/tinylog"
                                      "jsl-tinylog")))}})
