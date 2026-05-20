(ns user)

(do
  (import '[org.slf4j.bridge SLF4JBridgeHandler])
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (require 'flow-storm.runtime.logs.logs)
  (flow-storm.runtime.logs.logs/look #_:slf4j
                                     :jul
                                     #_:commons-logging
                                     #_:log4j2
                                     #_:reload4j
                                     #_:jboss-logging
                                     #_:system-logger))
