(ns flow-storm.runtime.logs.facades
  (:require [flow-storm.runtime.logs.utils :refer [class-exists?]]))

(def facades
  {:slf4j ;; :maven "org.slf4j/slf4j-api"
   {:id :slf4j
    :present?  (fn [] (class-exists? "org.slf4j.LoggerFactory"))}

   :jul
   {:id :jul
    :present?  (fn [] (class-exists? "java.util.logging.Logger"))}

   :commons-logging ;; :maven "commons-logging/commons-logging"
   {:id :commons-logging
    :present?  (fn [] (class-exists? "org.apache.commons.logging.LogFactory"))}

   :log4j2 ;; :maven "org.apache.logging.log4j/log4j-api"
   {:id :log4j2
    :present?  (fn [] (class-exists? "org.apache.logging.log4j.LogManager"))}

   :log4j1 ;; :maven "log4j/log4j"
   {:id :log4j1
    :present?  (fn [] (class-exists? "org.apache.log4j.Logger"))}

   :jboss-logging ;; :maven "org.jboss.logging/jboss-logging"
   {:id :jboss-logging
    :present?  (fn [] (class-exists? "org.jboss.logging.Logger"))}

   :system-logger
   {:id :system-logger
    :present?  (fn [] (class-exists? "java.lang.System$Logger"))}})
