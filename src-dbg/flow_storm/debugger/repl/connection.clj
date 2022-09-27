(ns flow-storm.debugger.repl.connection
  (:require [mount.core :as mount :refer [defstate]]
            [flow-storm.debugger.repl.nrepl :as nrepl]
            [flow-storm.debugger.websocket]
            [flow-storm.debugger.config :refer [config]]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io]))

(declare start-repl-connection)
(declare close-repl-connection)

(def log-file-path "./repl-client-debug")

(declare connection)
(defstate connection
  :start (start-repl-connection)
  :stop (close-repl-connection))

(defn default-repl-ns [{:keys [env-kind]}]
  (case env-kind :clj "user" :cljs "cljs.user"))

(defn eval-code-str
  ([code-str] (eval-code-str code-str (default-repl-ns config)))
  ([code-str ns]
   (if-let [repl-eval (:repl-eval connection)]
     (repl-eval code-str ns)
     (utils/log-error "No repl available"))))

(defn remote-connect-code [config]
  (format "(fsa/remote-connect %s)" (-> config
                                        (select-keys [:port :debugger-host])
                                        (pr-str))))

(defn make-specific-repl-init-sequence [{:keys [repl-type build-id]}]
  (case repl-type
    :shadow [{:code (format "(do (require '[shadow.cljs.devtools.api :as shadow]) (require '[flow-storm.runtime.debuggers-api :include-macros true]) (shadow/nrepl-select %s))" build-id)
              :ns nil}]

    ;; else it is a clj remote repl
    [{:code "(require '[flow-storm.runtime.debuggers-api])"
      :ns nil}]))

(defn make-general-repl-init-sequence [{:keys [env-kind] :as config}]
  (let [default-ns (default-repl-ns config)

        ns-ensure-command (case env-kind
                            :clj {:code "(do (in-ns 'user) nil)" :ns nil}
                            :cljs {:code "(in-ns 'cljs.user)" :ns nil})
        fs-require-api-command {:code "(require '[flow-storm.api :as fsa :include-macros true])"
                                :ns default-ns}
        fs-connect-command {:code (remote-connect-code config)
                            :ns default-ns}
        fs-require-dbg-command {:code "(require '[flow-storm.runtime.debuggers-api :as dbg-api :include-macros true])"
                                :ns default-ns}]

    [ns-ensure-command
     fs-require-api-command
     fs-connect-command
     fs-require-dbg-command]))

(defn start-repl-connection []
  (utils/log "[Starting Repl subsystem]")
  (when (:connect-to-repl? config)
    (let [{:keys [repl-kind]} config
          log-file (io/file log-file-path)
          log-output-stream (io/make-output-stream log-file {:append true
                                                             :encoding "UTF-8"})

          ;; repl here will be a map with :repl-eval (fn [code-str ns] ) and :close-connection (fn [])
          ;; :repl-eval fn will eval on the specific repl and return the value always as a string
          repl (case repl-kind
                 :nrepl (nrepl/connect config))
          repl-eval (fn [code-str ns]
                      (try

                        (when-not (= code-str ":watch-dog-ping")
                          (.write log-output-stream (.getBytes (format "\n\n---- [ %s ] ---->\n" ns)))
                          (.write log-output-stream (.getBytes (pr-str code-str)))
                          (.flush log-output-stream))

                        (let [response ((:repl-eval repl) code-str ns)]

                          (when-not (= code-str ":watch-dog-ping")
                            (.write log-output-stream (.getBytes "\n<---------\n"))
                            (.write log-output-stream (.getBytes (pr-str response)))
                            (.flush log-output-stream))

                          (read-string {} response))
                        (catch Exception e
                          (utils/log-error (.getMessage e)))))]

      (utils/log "Initializing repl...")

      ;; initialize the repl
      (doseq [{:keys [code ns]} (make-specific-repl-init-sequence config)]
        (repl-eval code ns))

      (doseq [{:keys [code ns]} (make-general-repl-init-sequence config)]
        (repl-eval code ns))

      {:repl-eval repl-eval
       :close-connection (fn []
                           (:close-connection repl)
                           (.close log-output-stream))})))

(defn close-repl-connection []
  (utils/log "[Stopping Repl subsystem]")
  (when-let [close-conn (:close-connection connection)]
    (close-conn)))
