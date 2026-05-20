(ns flow-storm.runtime.logs.bridges
  (:require [flow-storm.runtime.logs.utils :refer [class-exists? call-static]]))

(def bridges


  {

   ;;;;;;;;;;;;;;;;;;;
   ;; SLF4J bridges ;;
   ;;;;;;;;;;;;;;;;;;;

   [:slf4j :logback]
   {:artifact :included-in-backend
    :present? (fn [] (class-exists? "ch.qos.logback.classic.spi.LogbackServiceProvider"))}

   [:slf4j :log4j2]
   {:artifact 'org.apache.logging.log4j/log4j-slf4j2-impl
    :present? (fn [])}


   [:slf4j :jul]
   {:artifact 'org.slf4j/slf4j-jdk14
    :present? (fn [] (class-exists? "org.slf4j.jul.JDK14LoggerFactory"))}

   [:slf4j :reload4j]
   {:artifact 'org.slf4j/slf4j-reload4j
    :present? (fn [] (class-exists? "org.slf4j.reload4j.Reload4jLoggerFactory"))}

   [:slf4j :slf4j]
   {:artifact :included-in-backend
    :present? (fn [])}

   [:slf4j :tinylog]
   {:artifact 'org.tinylog/slf4j-tinylog
    :present? (fn [])}


   ;;;;;;;;;;;;;;;;;
   ;; JUL bridges ;;
   ;;;;;;;;;;;;;;;;;

   [:jul :logback]
   {:via-slf4j? true
    :present?  (fn [] )}

   [:jul :log4j2]
   {:artifact 'org.apache.logging.log4j/log4j-jul
    :present?  (fn [] )}

   [:jul :jul]
   {:present?  (constantly true)}

   [:jul :reload4j]
   {:via-slf4j? true
    :present?  (fn [] )}

   [:jul :slf4j]
   {:artifact 'org.slf4j/jul-to-slf4j
    :present?  (fn [] )
    :init (fn []
            (call-static "org.slf4j.bridge.SLF4JBridgeHandler" "removeHandlersForRootLogger" [])
            (call-static "org.slf4j.bridge.SLF4JBridgeHandler" "install" []))}

   [:jul :tinylog]
   {:artifact 'org.tinylog/jul-tinylog
    :present?  (fn [] )}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Commons logging bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:commons-logging :logback]
   {:via-slf4j? true
    :present?  (fn [] )}

   [:commons-logging :log4j2]
   {:artifact 'org.apache.logging.log4j/log4j-jcl
    :present?  (fn [] )}

   [:commons-logging :jul]
   {:present?  (fn [] )
    :built-in-backend? true}

   [:commons-logging :reload4j]
   {:present?  (fn [] )
    :built-in-backend? true}

   [:commons-logging :slf4j]
   {:present?  (fn [] )
    :artifact 'org.slf4j/jcl-over-slf4j}

   [:commons-logging :tinylog]
   {:present?  (fn [] )
    :artifact 'org.tinylog/jcl-tinylog}

   ;;;;;;;;;;;;;;;;;;;;
   ;; Log4j2 bridges ;;
   ;;;;;;;;;;;;;;;;;;;;

   [:log4j2 :logback]
   {:via-slf4j? true
    :present? (fn [])}

   [:log4j2 :log4j2]
   {:present? (fn [])
    :built-in-backend? true}

   [:log4j2 :jul]
   {:present? (fn [])
    :artifact 'org.apache.logging.log4j/log4j-to-jul}

   [:log4j2 :slf4j]
   {:present? (fn [])
    :artifact 'org.apache.logging.log4j/log4j-to-slf4j}

   [:log4j2 :tinylog]
   {:via-slf4j? true
    :present? (fn [])}

   ;;;;;;;;;;;;;;;;;;;;;;
   ;; Reload4j bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;

   [:reload4j :logback]
   {:via-slf4j? true
    :present? (fn [])}

   [:reload4j :log4j2]
   {:present? (fn [])
    :artifact 'org.apache.logging.log4j/log4j-1.2-api}

   [:reload4j :jul]
   {:via-slf4j? true
    :present? (fn [])}

   [:reload4j :reload4j]
   {:present? (fn [])
    :artifact 'ch.qos.reload4j/reload4j}

   [:reload4j :slf4j]
   {:present? (fn [])
    :artifact 'org.slf4j/log4j-over-slf4j}

   [:reload4j :tinylog]
   {:present? (fn [])
    :artifact 'org.tinylog/log4j1.2-api}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; JBoss logging bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:jboss-logging :logback]
   {:present? (fn [])
    :via-slf4j? true}

   [:jboss-logging :log4j2]
   {:present? (fn [])
    :built-in-backend? true}

   [:jboss-logging :jul]
   {:present? (fn [])
    :built-in-backend? true}

   [:jboss-logging :reload4j]
   {:present? (fn [])
    :built-in-backend? true}

   [:jboss-logging :slf4j]
   {:present? (fn [])
    :built-in-backend? true}

   [:jboss-logging :tinylog]
   {:present? (fn [])
    :artifact 'org.tinylog/jboss-tinylog}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; System logger bridges ;;
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:system-logger :logback]
   {:present? (fn [])
    :via-slf4j? true}

   [:system-logger :log4j2]
   {:present? (fn [])
    :artifact 'org.apache.logging.log4j/log4j-jpl}

   [:system-logger :jul]
   {:present? (fn [])}

   [:system-logger :reload4j]
   {:present? (fn [])
    :via-slf4j? true}

   [:system-logger :slf4j]
   {:present? (fn [])
    :artifact 'org.slf4j/slf4j-jdk-platform-logging}

   [:system-logger :tinylog]
   {:present? (fn [])
    :artifact 'org.tinylog/jsl-tinylog}

   })
