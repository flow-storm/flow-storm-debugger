(ns flow-storm.debugger.repl.core
  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.repl.nrepl :as nrepl]
            [flow-storm.debugger.websocket]
            [flow-storm.debugger.config :refer [config]]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io])
  (:import [java.io OutputStream]))

(declare start-repl)
(declare stop-repl)
(declare init-repl)

(def log-file-path "./repl-client-debug")

(declare repl)
(defstate repl
  :start (fn [_] (start-repl))
  :stop (fn [] (stop-repl)))

(defn default-repl-ns [{:keys [env-kind]}]
  (case env-kind :clj "user" :cljs "shadow.user"))

(defn eval-code-str
  ([code-str] (eval-code-str code-str nil))
  ([code-str ns]
   (let [ns (or ns (default-repl-ns config))]
     (if-let [repl-eval (:repl-eval repl)]
      (repl-eval code-str ns)
      (utils/log-error "No repl available. You need a repl connection to use this functionality. Checkout the user guide.")))))

(defn safe-eval-code-str
  "Eval code directly into the connected repl.
  It is the same as `eval-code-str` but will will catch and log
  any exceptions."
  [& args]
  (try
    (apply eval-code-str args)
    (catch Exception e (utils/log-error (.getMessage e) e))))

(defn safe-cljs-eval-code-str
  "Eval code in the clojurescript repl through the connected
  clojure repl."
  ([code-str] (safe-cljs-eval-code-str code-str nil))
  ([code-str ns]
   (try
     (let [ns (or ns "cljs.user")]
       (if-let [repl-eval-cljs (:repl-eval-cljs repl)]
         (repl-eval-cljs code-str ns)
         (utils/log-error "No cljs repl available")))
     (catch Exception e (utils/log-error (.getMessage e) e)))))

(defn make-cljs-repl-init-sequence [config]
  (let [remote-opts (select-keys config [:port :debugger-host])]

    [{:code "(do (in-ns 'shadow.user) nil)"                                    :ns nil         :repl :clj}
     {:code (format "(flow-storm.api/remote-connect %s)" (pr-str remote-opts)) :ns "cljs.user" :repl :cljs}]))

(defn make-clj-repl-init-sequence [config]
  (let [remote-opts (select-keys config [:port :debugger-host])]

    [{:code "(do (in-ns 'user) nil)"                                                         :ns nil    :repl :clj}
     {:code "(require '[flow-storm.api :as fsa])"                                            :ns "user" :repl :clj}
     {:code (format "(flow-storm.api/remote-connect %s)" (pr-str remote-opts))               :ns "user" :repl :clj}
     {:code "(require '[flow-storm.runtime.debuggers-api :as dbg-api :include-macros true])" :ns "user" :repl :clj}]))

(defn init-repl
  ([config] (init-repl config (:repl-eval repl) (:repl-eval-cljs repl)))
  ([{:keys [env-kind] :as config} repl-eval repl-eval-cljs]
   (let [repl-init-sequence (case env-kind
                              :clj  (make-clj-repl-init-sequence config)
                              :cljs (make-cljs-repl-init-sequence config))]
     (doseq [{:keys [code ns repl]} repl-init-sequence]
       (case repl
         :clj  (repl-eval code ns)
         :cljs (repl-eval-cljs code ns))))))

(defn start-repl []
  (when (:connect-to-repl? config)
    (let [{:keys [repl-kind]} config
          log-file (io/file log-file-path)
          ^OutputStream log-output-stream (io/make-output-stream log-file {:append true
                                                                           :encoding "UTF-8"})

          ;; repl here will be a map with :repl-eval (fn [code-str ns] ) and :close-connection (fn [])
          ;; :repl-eval fn will eval on the specific repl and return the value always as a string
          srepl (case repl-kind
                 :nrepl (nrepl/connect config))
          repl-eval (fn [code-str ns]
                      (when-not (= code-str ":watch-dog-ping")
                        (.write log-output-stream (.getBytes (format "\n\n---- [ %s ] ---->\n" ns)))
                        (.write log-output-stream (.getBytes (pr-str code-str)))
                        (.flush log-output-stream))

                      (let [response ((:repl-eval srepl) code-str ns)]

                        (when-not (= code-str ":watch-dog-ping")
                          (.write log-output-stream (.getBytes "\n<---------\n"))
                          (.write log-output-stream (.getBytes (pr-str response)))
                          (.flush log-output-stream))

                        (try
                          (read-string {} response)
                          (catch Exception e
                            (utils/log-error (.getMessage e))))))
          repl-eval-cljs (fn [code-str ns]
                           (repl-eval (format "((requiring-resolve 'hansel.instrument.utils/eval-in-ns-fn-cljs) '%s '%s %s)"
                                              ns
                                              code-str
                                              (pr-str (select-keys config [:build-id])))
                                      "shadow.user"))]

      (utils/log "Initializing repl...")

      (init-repl config repl-eval repl-eval-cljs)

      {:repl-eval repl-eval
       :repl-eval-cljs repl-eval-cljs
       :close-connection (fn []
                           (:close-connection srepl)
                           (.close log-output-stream))})))

(defn repl-ok? []
  (not ((:connection-closed? repl))))

(defn stop-repl []
  (when-let [close-conn (:close-connection repl)]
    (close-conn)))
