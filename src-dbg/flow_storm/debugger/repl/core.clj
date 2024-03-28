(ns flow-storm.debugger.repl.core

  "Stateful component that handles debugger connection to repls.
  `start-repl` will be called at startup and will use the current repl
  config in `state` to start a repl connection.

  Depending on the repl configuration in `state` it can start Clojure and ClojureScript
  repl connections.

  It can do runtime initialization sequences with `init-repl` to prepare the runtime
  part by executing some repl instructions.

  The main functions after the repl is ready are :
  - `safe-eval-code-str` for evaluating Clojure
  - `safe-cljs-eval-code-str` for evaluating ClojureScript
  "

  (:require [flow-storm.state-management :refer [defstate]]
            [flow-storm.debugger.repl.nrepl :as nrepl]
            [flow-storm.debugger.websocket]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io])
  (:import [java.io OutputStream]))

(declare start-repl)
(declare stop-repl)
(declare init-repl)

(def repl-watchdog-interval 3000)

(def log-file-path "./repl-client-debug")

(declare repl)
(defstate repl
  :start (fn [config] (start-repl config))
  :stop (fn [] (stop-repl)))

(defn default-repl-ns []
  (let [env-kind (dbg-state/env-kind)]
    (case env-kind :clj "user" :cljs "shadow.user")))

(defn eval-code-str

  "Evaluate `code-str` in the connected Clojure repl.
  Will throw if anything goes wrong.

  `ns` can be used to customize what namespace `code-str`should be executed in.
  Returns the result object."

  ([code-str] (eval-code-str code-str nil))
  ([code-str ns]
   (if-not (:repl-ready? (dbg-state/connection-status))

     (utils/log-error "No repl available. You need a repl connection to use this functionality. Checkout the user guide.")

     (let [ns (or ns (default-repl-ns))]
       (when-let [repl-eval (:repl-eval repl)]
         (try
           (repl-eval code-str ns)
           (catch clojure.lang.ExceptionInfo ei
             (throw (ex-info "Error evaluating code on :clj repl"
                             (assoc (ex-data ei)
                                    :code-str code-str
                                    :ns ns))))))))))

(defn safe-eval-code-str

  "Wrapper of `eval-code-str` that will not throw. Will just log
  an error in case of exception."

  [& args]
  (try
    (apply eval-code-str args)
    (catch Exception e (utils/log-error (.getMessage e) e))))

(defn safe-cljs-eval-code-str

  "Eval `code-str` in the clojurescript repl through the connected
  clojure repl.

  `ns` can be used to customize what namespace `code-str`should be executed in.

  In case anything goes wrong it will not throw, just log an error.

  Returns the result object. If it is a js object it will return a string."

  ([code-str] (safe-cljs-eval-code-str code-str nil))
  ([code-str ns]
   (let [ns (or ns "cljs.user")]
     (if-let [repl-eval-cljs (:repl-eval-cljs repl)]
       (try
         (repl-eval-cljs code-str ns)
         (catch Exception e
           (utils/log-error (.getMessage e) e)
           (throw (ex-info "Error evaluating code on :cljs repl"
                           {:code-str code-str
                            :ns ns
                            :cause e}))))
       (utils/log-error "No cljs repl available")))))

(defn make-cljs-repl-init-sequence []
  [{:code "(do (in-ns 'shadow.user) nil)"                                      :ns nil         :repl-kind :clj}
   {:code "(require '[flow-storm.runtime.debuggers-api])"                      :ns nil         :repl-kind :clj}
   {:code "(require '[flow-storm.runtime.debuggers-api :include-macros true])" :ns "cljs.user" :repl-kind :cljs}])

(defn make-clj-repl-init-sequence []
  (let [opts (select-keys (dbg-state/debugger-config) [:debugger-host :debugger-ws-port])]
    [{:code "(do (in-ns 'user) nil)"                                                      :ns nil    :repl-kind :clj}
     {:code "(require '[flow-storm.runtime.debuggers-api])"                               :ns "user" :repl-kind :clj}
     {:code (format "(flow-storm.runtime.debuggers-api/remote-connect %s)" (pr-str opts)) :ns "user" :repl-kind :clj}]))

(defn init-repl
  ([env-kind] (init-repl env-kind (:repl-eval repl) (:repl-eval-cljs repl)))
  ([env-kind repl-eval repl-eval-cljs]
   (let [repl-init-sequence (case env-kind
                              :clj  (make-clj-repl-init-sequence)
                              :cljs (make-cljs-repl-init-sequence))]
     (doseq [{:keys [code ns repl-kind]} repl-init-sequence]
       (case repl-kind
         :clj  (repl-eval code ns)
         :cljs (repl-eval-cljs code ns))))))


(defn- connect-and-init [{:keys [repl-type runtime-host port build-id on-repl-up]}]
  (let [runtime-host (or runtime-host "localhost")
        env-kind (if (#{:shadow} repl-type) :cljs :clj) ;; HACKY, this logic is replicated in `state`
        repl-kind :nrepl ;; HACKY, this logic is replicated in `state`
        log-file (io/file log-file-path)
        ^OutputStream log-output-stream (io/make-output-stream log-file {:append true
                                                                         :encoding "UTF-8"})
        connect (fn [] (case repl-kind
                         :nrepl (nrepl/connect runtime-host port)))

        ;; repl here will be a map with :repl-eval (fn [code-str ns] ) and :close-connection (fn [])
        ;; :repl-eval fn will eval on the specific repl and return the value always as a string
        {:keys [repl-eval close-connection]} (connect)
        eval-clj (fn [code-str ns]
                   (when-not (= code-str ":watch-dog-ping")
                     (.write log-output-stream (.getBytes (format "\n\n---- [ %s ] ---->\n" ns)))
                     (.write log-output-stream (.getBytes (pr-str code-str)))
                     (.flush log-output-stream))

                   (let [response (repl-eval code-str ns)]

                     (when-not (= code-str ":watch-dog-ping")
                       (.write log-output-stream (.getBytes "\n<---------\n"))
                       (.write log-output-stream (.getBytes (pr-str response)))
                       (.flush log-output-stream))

                     (try
                       (when response (read-string {} response))
                       (catch Exception e
                         (utils/log-error (format "Error reading the response %s. CAUSE : %s"
                                                  response
                                                  (.getMessage e)))))))
        eval-cljs (fn [code-str ns]
                    (eval-clj (format "((requiring-resolve 'hansel.instrument.utils/eval-in-ns-fn-cljs) '%s '%s %s)"
                                      ns
                                      code-str
                                      (pr-str {:build-id build-id}))
                              "shadow.user"))
        repl-comp {:repl-eval eval-clj
                   :repl-eval-cljs eval-cljs
                   :close-connection (fn []
                                       (close-connection)
                                       (.close log-output-stream))}]

    (utils/log "Initializing repl...")
    (try
      (init-repl env-kind eval-clj eval-cljs)
      (catch Exception e
        (utils/log-error "There was a problem initializing the remote runtime via repl" e)))

    (let [repl-ok? (= :watch-dog-ping (eval-clj ":watch-dog-ping" "user"))]
      (utils/log (str "Repl ok? : " repl-ok?))
      (when repl-ok?
        (on-repl-up)))

    repl-comp))

(defn- watchdog-loop [{:keys [on-repl-down] :as config}]
  (let [repl-watchdog-thread
        (Thread.
         (fn []
           (utils/log "Starting the repl watchdog loop")
           (try
             (loop []
               (let [repl-ok? (try
                                (= :watch-dog-ping (eval-code-str ":watch-dog-ping" "user"))
                                (catch clojure.lang.ExceptionInfo ei
                                  (let [{:keys [error/type] :as exd} (ex-data ei)]
                                    (utils/log (format "[WATCHDOG] error executing ping. %s %s" type exd))
                                    ;; just return that the repl is not ok when we got a socket-exception from
                                    ;; the underlying repl
                                    (not= type :repl/socket-exception)))
                                (catch Exception e
                                  (utils/log-error "This is completely unexpected :")
                                  (.printStackTrace e)
                                  false))]
                 (when-not repl-ok?
                   (utils/log "[WATCHDOG] repl looks down, trying to reconnect ...")

                   (on-repl-down)
                   (stop-repl)
                   (try
                     (let [new-repl-cmp (connect-and-init config)]
                       (alter-var-root #'repl (constantly new-repl-cmp)))
                     (catch Exception e
                       (utils/log (format "Couldn't restart repl (%s), retrying in %d ms" (.getMessage e) repl-watchdog-interval)))))
                 (Thread/sleep repl-watchdog-interval))
               (recur))
             (catch java.lang.InterruptedException _
               (utils/log "FlowStorm Repl Watchdog thread interrupted"))
             (catch Exception e (.printStackTrace e)))))
        repl-watchdog-interrupt (fn [] (.interrupt repl-watchdog-thread))]

    (.setName repl-watchdog-thread "FlowStorm Repl Watchdog")
    (.start repl-watchdog-thread)
    repl-watchdog-interrupt))

(defn start-repl [{:keys [port] :as config}]
  (when port
    (let [repl-comp (connect-and-init config)]
      (watchdog-loop config)
      repl-comp)))

(defn stop-repl []
  (when-let [close-conn (:close-connection repl)]
    (close-conn)))
