(ns flow-storm.debugger.nrepl
  (:require [nrepl.core :as nrepl]
            [nrepl.transport :as transport]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def log-file-path "./nrepl-client-debug")
(defn connect [{:keys [host port repl-type build-id] :or {host "localhost"}}]
  (let [transport (nrepl/connect :host host
                                 :port port
                                 :transport-fn #'transport/bencode)
        log-file (io/file log-file-path)
        log-output-stream (io/make-output-stream (io/file log-file-path) {:append true
                                                                          :encoding "UTF-8"})
        client (nrepl/client transport Long/MAX_VALUE)
        session (nrepl/client-session client)
        send-msg (fn [msg]
                   (.write log-output-stream (.getBytes "\n\n--------->\n"))
                   (.write log-output-stream (.getBytes (pr-str msg)))
                   (let [res (nrepl/message session msg)]
                     (.write log-output-stream (.getBytes "\n<---------\n"))
                     (.write log-output-stream (.getBytes (pr-str res)))
                     (.flush log-output-stream)
                     res))
        repl-type-init-command (case repl-type
                                 :shadow (format "(do (require '[shadow.cljs.devtools.api :as shadow]) (require '[flow-storm.runtime.debuggers-api :include-macros true]) (shadow/nrepl-select %s))"
                                                 build-id)
                                 nil)]

    (when repl-type-init-command
      (println "Initializing repl-type" repl-type)
      (let [res (send-msg {:op "eval" :code repl-type-init-command})]
        (println "repl-type response : " res)))

    ;; Make the runtime connect a websocket back to us
    (println "Initializing, requiring flow-storm.api on remote side plus trying to connect back to us via websocket.")

    (send-msg {:op "eval" :code "(require '[flow-storm.api :as fsa :include-macros true])"})
    (send-msg {:op "eval" :code "(fsa/remote-connect {})"})
    (send-msg {:op "eval" :code "(require '[flow-storm.runtime.debuggers-api :as dbg-api :include-macros true])"})

    {:repl-eval (fn repl-eval
                  ([env-kind code] (repl-eval env-kind code nil))
                  ([env-kind code ns]
                   (let [ns (or ns (case env-kind
                                     :clj "user"
                                     :cljs "cljs.user"))]

                     (try
                       (let [[m1 m2 :as res] (send-msg {:op "eval" :code code :ns ns})
                             ret (if (and (not (:err m1))
                                          (= (:status m2) ["done"]))

                                   (try
                                     (edn/read-string (:value m1))
                                     (catch Exception e
                                       ;; if what we evaluated doesn't return valid edn
                                       ;; just return the value string
                                       (:value m1))))]
                         ret)
                       (catch Exception e
                         (tap> e))))))
     :close-connection (fn []
                         (.close transport)
                         (.close log-output-stream))}))

(comment

  (def transport (nrepl/connect :host "localhost"
                                :port 9000
                                :transport-fn #'transport/bencode))

  (def client (nrepl/client transport Long/MAX_VALUE))

  (def session (nrepl/client-session client))

  (def res (nrepl/message session {:op "eval" :code "(do (require '[shadow.cljs.devtools.api :as shadow]) (shadow/nrepl-select :browser-repl))"}))

  (def res (nrepl/message session {:op "describe"}))
  (def res (nrepl/message session {:op "ls-sessions"}))
  (def res (nrepl/message session {:op "eval" :code "(+ 1 2)"}))
  (def res (nrepl/message session {:op "eval" :code "(in-ns 'user)"}))

  (def res (nrepl/message session {:op "eval" :code "(do (require '[shadow.cljs.devtools.api :as shadow]) (shadow/watch :app) (shadow/nrepl-select :app))"}))

  (def res (nrepl/message session {:op "eval" :code "js/window"}))
  (def res (nrepl/message session {:op "eval" :code "a" :ns "cljs.user"}))


  )
