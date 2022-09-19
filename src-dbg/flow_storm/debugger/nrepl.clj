(ns flow-storm.debugger.nrepl
  (:require [nrepl.core :as nrepl]
            [nrepl.transport :as transport]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [flow-storm.utils :refer [log log-error]]))

(def log-file-path "./nrepl-client-debug")

(defn maybe-nicer-error-message [msg-str]
  (cond
    (str/includes? msg-str "No such namespace: dbg-api")
    "Debugger isn't initialized correctly. Probably it got disconnected, try reconnecting please."

    :else msg-str))

(defn connect [{:keys [host port repl-type build-id env-kind show-error] :or {host "localhost"}}]
  (let [transport (nrepl/connect :host host
                                 :port port
                                 :transport-fn #'transport/bencode)
        log-file (io/file log-file-path)
        log-output-stream (io/make-output-stream log-file {:append true
                                                           :encoding "UTF-8"})
        client (nrepl/client transport Long/MAX_VALUE)
        session (nrepl/client-session client)
        default-cljs-ns (case env-kind
                          :clj "user"
                          :cljs "cljs.user")
        eval-code-str (fn eval-code-str
                        ([code-str] (eval-code-str code-str nil))
                        ([code-str ns]
                         (let [msg (cond-> {:op "eval" :code code-str}
                                     ns (assoc :ns ns))]
                           (.write log-output-stream (.getBytes "\n\n--------->\n"))
                           (.write log-output-stream (.getBytes (pr-str msg)))
                           (let [responses (nrepl/message session msg)
                                 {:keys [err] :as res-map} (nrepl/combine-responses responses)]
                             (.write log-output-stream (.getBytes "\n<---------\n"))
                             (.write log-output-stream (.getBytes (pr-str responses)))
                             (.flush log-output-stream)
                             (if err
                               (throw (ex-info (str "nrepl evaluation error: " err) (assoc res-map :msg msg)))
                               (let [val-str (first (:value res-map))]
                                 (read-string {:read-cond :allow} val-str)))))))
        repl-type-init-command (case repl-type
                                 :shadow (format "(do (require '[shadow.cljs.devtools.api :as shadow]) (require '[flow-storm.runtime.debuggers-api :include-macros true]) (shadow/nrepl-select %s))"
                                                 build-id)

                                 ;; else it is a clj remote repl
                                 "(require '[flow-storm.runtime.debuggers-api])"
                                 )]

    (when repl-type-init-command
      (try

        (log "Initializing repl-type" repl-type)
        (eval-code-str repl-type-init-command)

        ;; Make the runtime connect a websocket back to us
        (log "Initializing, requiring flow-storm.api on remote side plus trying to connect back to us via websocket.")
        (case env-kind
          :clj (eval-code-str "(in-ns 'user)" default-cljs-ns)
          :cljs (eval-code-str "(in-ns 'cljs.user)" default-cljs-ns))
        (eval-code-str "(require '[flow-storm.api :as fsa :include-macros true])" default-cljs-ns)
        (eval-code-str "(fsa/remote-connect {})" default-cljs-ns)
        (eval-code-str "(require '[flow-storm.runtime.debuggers-api :as dbg-api :include-macros true])" default-cljs-ns)

        (catch Exception e
          (show-error (ex-message e))
          (log-error (format "%s %s" (ex-message e) (ex-data e))))))

    {:repl-eval (fn repl-eval
                  ([code] (repl-eval code nil))
                  ([code ns]
                   (let [ns (or ns default-cljs-ns)]
                     (try
                       (eval-code-str code ns)
                       (catch Exception e
                         (let [err-msg (maybe-nicer-error-message (ex-message e))]
                           (show-error (ex-message e))
                           (log-error err-msg (ex-data e))))))))
     :close-connection (fn []
                         (.close transport)
                         (.close log-output-stream))}))

(comment

  (def transport (nrepl/connect :host "localhost"
                                :port 46000
                                :transport-fn #'transport/bencode))

  (def client (nrepl/client transport Long/MAX_VALUE))

  (def session (nrepl/client-session client))

  (def res (nrepl/message session {:op "eval" :code "(require '[some.crazy :as c])"}))

  (def res (nrepl/message session {:op "eval" :code "(do (require '[shadow.cljs.devtools.api :as shadow]) (shadow/nrepl-select :browser-repl))"}))

  (def res (nrepl/message session {:op "describe"}))
  (def res (nrepl/message session {:op "ls-sessions"}))
  (def res (nrepl/message session {:op "eval" :code "(+ 1 2)"}))
  (def res (nrepl/message session {:op "eval" :code "(in-ns 'user)"}))

  (def res (nrepl/message session {:op "eval" :code "(do (require '[shadow.cljs.devtools.api :as shadow]) (shadow/watch :app) (shadow/nrepl-select :app))"}))

  (def res (nrepl/message session {:op "eval" :code "js/window"}))
  (def res (nrepl/message session {:op "eval" :code "a" :ns "cljs.user"}))


  )
