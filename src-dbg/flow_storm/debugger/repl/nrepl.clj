(ns flow-storm.debugger.repl.nrepl
  (:require [nrepl.core :as nrepl]
            [nrepl.transport :as transport]))

(defn connect [{:keys [runtime-host port] :or {runtime-host "localhost"}}]
  (let [transport (nrepl/connect :host runtime-host
                                 :port port
                                 :transport-fn #'transport/bencode)
        ;; non of our commands should take longer than 10secs to execute
        ;; if more, we consider it a repl timeout and give up
        client (nrepl/client transport 10000)
        session (nrepl/client-session client)]

    {:repl-eval (fn repl-eval [code-str ns]
                  (try
                    (let [msg (cond-> {:op "eval" :code code-str}
                                ns (assoc :ns ns))
                          responses (nrepl/message session msg)
                          {:keys [err] :as res-map} (nrepl/combine-responses responses)]
                      (if (empty? responses)

                        (throw (ex-info "nrepl timeout" {:error/type :repl/timeout}))

                        (if err
                          (throw (ex-info (str "nrepl evaluation error: " err)
                                          (assoc res-map
                                                 :error/type :repl/evaluation-error
                                                 :msg msg)))
                          (first (:value res-map)))))
                    (catch java.net.SocketException se
                      (throw (ex-info (.getMessage se)
                                      {:error/type :repl/socket-exception})))))

     :close-connection (fn []
                         (.close transport))}))

(comment

  (def transport (nrepl/connect :host "localhost"
                                :port 9000
                                :transport-fn #'transport/bencode))

  (def client (nrepl/client transport Long/MAX_VALUE))
  (def client (nrepl/client transport 3000))

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
