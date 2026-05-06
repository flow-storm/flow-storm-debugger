(ns flow-storm.runtime.logs.logs
  (:require [clojure.string :as str]
            [flow-storm.runtime.logs.utils :as log-utils :refer [class-exists?]]
            [flow-storm.runtime.logs.facades :as facades]
            [flow-storm.runtime.logs.backends :as backends]
            [flow-storm.runtime.logs.bridges :as bridges]))

;; Objective
;;
;; - Display the precense and configuration of current JVM logging libraries
;; - Allow the user to change different backends root loggers levels for library tracing
;;
;; TODOs
;;
;; - [X] Detect facades, backends and bridges presence
;; - [ ] Complete backends levels
;; - [ ] Complete backends config files
;; - [ ] Add backends config files detection
;; - [ ] Make printer fns for all supported facades
;; - [ ] Create UI tab
;; - [ ] Display graph
;; - [ ] Change backends level with UI
;; - [ ] Display found config files content
;; - [ ] Figure out how to treat backends like :slf4j-nop
;; - [ ] Figure out a way of testing all this, including multi-hops


(defn detect-systems []
  (let [detect (fn [m]
                 (filter
                  (fn [{:keys [present?]}] (present?))
                  (vals m)))]
    {:facades  (detect facades/facades)
     :bridges  (detect bridges/bridges)
     :backends (detect backends/backends)}))

(comment

  (detect-systems)
  )
(defn- first-resource [& names]
  (some resource names))

(defn- upper-name [x]
  (when x
    (str/upper-case (name x))))

;; --------------------
;; Logback
;; --------------------

(defn- logback-system []
  (when (and (class-exists? "ch.qos.logback.classic.Logger")
             (class-exists? "org.slf4j.LoggerFactory"))
    (letfn [(level [x]
              (call-static "ch.qos.logback.classic.Level" "toLevel" (upper-name x)))
            (ctx []
              (call-static "org.slf4j.LoggerFactory" "getILoggerFactory"))
            (logger [name]
              (call (ctx) "getLogger" name))]
      {:key :logback
       :description "Logback Classic, commonly used as an SLF4J backend."
       :config-file (or (System/getProperty "logback.configurationFile")
                        (first-resource "logback-test.xml" "logback.xml"))
       :get-root-level
       (fn []
         (some-> (logger "ROOT") (call "getLevel") str keyword))

       :set-root-level!
       (fn [lvl]
         (call (logger "ROOT") "setLevel" (level lvl))
         lvl)

       :get-package-level
       (fn [pkg]
         (some-> (logger pkg) (call "getLevel") str keyword))

       :set-package-level!
       (fn [pkg lvl]
         ;; pass nil to inherit parent level
         (call (logger pkg) "setLevel" (when lvl (level lvl)))
         lvl)})))

;; --------------------
;; Log4j 2
;; --------------------

(defn- log4j2-system []
  (when (and (class-exists? "org.apache.logging.log4j.LogManager")
             (class-exists? "org.apache.logging.log4j.core.config.Configurator"))
    (letfn [(level [x]
              (call-static "org.apache.logging.log4j.Level" "toLevel" (upper-name x)))
            (ctx []
              (call-static "org.apache.logging.log4j.LogManager" "getContext" false))
            (config []
              (call (ctx) "getConfiguration"))
            (root-config []
              (call (config) "getRootLogger"))
            (logger-config [name]
              (call (config) "getLoggerConfig" name))
            (refresh! []
              (call (ctx) "updateLoggers"))]
      {:key :log4j2
       :description "Apache Log4j 2 backend."
       :config-file (or (System/getProperty "log4j2.configurationFile")
                        (first-resource
                         "log4j2-test.xml" "log4j2-test.json"
                         "log4j2-test.yaml" "log4j2-test.yml"
                         "log4j2-test.properties"
                         "log4j2.xml" "log4j2.json"
                         "log4j2.yaml" "log4j2.yml"
                         "log4j2.properties"))
       :get-root-level
       (fn []
         (some-> (root-config) (call "getLevel") str keyword))

       :set-root-level!
       (fn [lvl]
         (call-static "org.apache.logging.log4j.core.config.Configurator"
                      "setRootLevel"
                      (level lvl))
         lvl)

       :get-package-level
       (fn [pkg]
         (some-> (logger-config pkg) (call "getLevel") str keyword))

       :set-package-level!
       (fn [pkg lvl]
         (call-static "org.apache.logging.log4j.core.config.Configurator"
                      "setLevel"
                      pkg
                      (level lvl))
         (refresh!)
         lvl)})))

;; --------------------
;; Log4j 1.x / reload4j
;; --------------------

(defn- log4j1-system []
  (when (class-exists? "org.apache.log4j.Logger")
    (letfn [(level [x]
              (call-static "org.apache.log4j.Level" "toLevel" (upper-name x)))
            (root []
              (call-static "org.apache.log4j.Logger" "getRootLogger"))
            (logger [name]
              (call-static "org.apache.log4j.Logger" "getLogger" name))]
      {:key :log4j1
       :description "Log4j 1.x or reload4j. Log4j 1.x is obsolete; reload4j is the maintained compatibility path."
       :config-file (or (System/getProperty "log4j.configuration")
                        (first-resource "log4j.properties" "log4j.xml"))
       :get-root-level
       (fn []
         (some-> (root) (call "getLevel") str keyword))

       :set-root-level!
       (fn [lvl]
         (call (root) "setLevel" (level lvl))
         lvl)

       :get-package-level
       (fn [pkg]
         (some-> (logger pkg) (call "getLevel") str keyword))

       :set-package-level!
       (fn [pkg lvl]
         (call (logger pkg) "setLevel" (when lvl (level lvl)))
         lvl)})))

;; --------------------
;; java.util.logging
;; --------------------

(defn- jul-level [lvl]
  (let [m {:trace "FINEST"
           :debug "FINE"
           :info  "INFO"
           :warn  "WARNING"
           :error "SEVERE"
           :fatal "SEVERE"
           :off   "OFF"
           :all   "ALL"}]
    (java.util.logging.Level/parse
     (get m (keyword (str/lower-case (name lvl))) (upper-name lvl)))))

(defn- jul-system []
  {:key :jul
   :description "java.util.logging, the JDK built-in logging system."
   :config-file (or (System/getProperty "java.util.logging.config.file")
                    "JDK default logging.properties")
   :get-root-level
   (fn []
     (some-> (java.util.logging.Logger/getLogger "")
             .getLevel
             str
             keyword))

   :set-root-level!
   (fn [lvl]
     (.setLevel (java.util.logging.Logger/getLogger "") (jul-level lvl))
     lvl)

   :get-package-level
   (fn [pkg]
     (some-> (java.util.logging.Logger/getLogger pkg)
             .getLevel
             str
             keyword))

   :set-package-level!
   (fn [pkg lvl]
     (.setLevel (java.util.logging.Logger/getLogger pkg)
                (when lvl (jul-level lvl)))
     lvl)})

(defn logging-systems
  "Returns a vector of maps for logging systems found on the runtime classpath.

  Level examples:
    :trace :debug :info :warn :error :off

  Package logger examples:
    \"org.eclipse.jetty\"
    \"com.zaxxer.hikari\"
    \"my.app\""
  []
  (->> [(logback-system)
        (log4j2-system)
        (log4j1-system)
        (jul-system)]
       (remove nil?)
       vec))


(comment

  (logging-systems)

  )
