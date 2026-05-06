(ns flow-storm.runtime.logs.bridges
  (:require [flow-storm.runtime.logs.utils :refer [class-exists?]]))

(def bridges


  {:jul->slf4j ;; :maven "org.slf4j/jul-to-slf4j"
   {:id :jul->slf4j
    :desc "Routes java.util.logging events to SLF4J when SLF4JBridgeHandler is installed."
    :present?  (fn [] (class-exists? "org.slf4j.bridge.SLF4JBridgeHandler"))
    :from :jul
    :to :slf4j}

   :commons-logging->slf4j ;; :maven "org.slf4j/jcl-over-slf4j"
   {:id :commons-logging->slf4j
    :desc "Replaces Commons Logging so JCL calls route to SLF4J."
    :present?  (fn [] (class-exists? "org.apache.commons.logging.impl.SLF4JLogFactory"))
    :from :commons-logging
    :to :slf4j}

   :log4j1->slf4j ;; :maven "org.slf4j/log4j-over-slf4j"
   {:id :log4j1->slf4j
    :desc "Implements the Log4j 1.x API and routes calls to SLF4J."
    :present?  (fn [] (class-exists? "org.apache.log4j.Category"))
    :from :log4j1
    :to :slf4j}

   :log4j2->slf4j ;; :maven "org.apache.logging.log4j/log4j-to-slf4j"
   {:id :log4j2->slf4j
    :desc "Routes Log4j 2 API calls to SLF4J."
    :present?  (fn [] (class-exists? "org.apache.logging.slf4j.Log4jLoggerFactory"))
    :from :log4j2
    :to :slf4j}

   :slf4j->jul ;; :maven "org.slf4j/slf4j-jdk14"
   {:id :slf4j->jul
    :desc "SLF4J provider that routes SLF4J calls to java.util.logging."
    :present?  (fn [] (class-exists? "org.slf4j.jul.JDK14LoggerFactory"))
    :from :slf4j
    :to :jul}

   :slf4j->reload4j ;; :maven "org.slf4j/slf4j-reload4j"
   {:id :slf4j->reload4j
    :desc "SLF4J provider that routes SLF4J calls to reload4j / Log4j 1-style backend."
    :present?  (fn [] (class-exists? "org.slf4j.reload4j.Reload4jLoggerFactory"))
    :from :slf4j
    :to :reload4j}

   :log4j2->jul ;; :maven "org.apache.logging.log4j/log4j-jul"
   {:id :log4j2->jul
    :desc "Routes java.util.logging to Log4j 2 when configured as the JUL LogManager."
    :present?  (fn [] (class-exists? "org.apache.logging.log4j.jul.LogManager"))
    :from :jul
    :to :log4j2}
   })
