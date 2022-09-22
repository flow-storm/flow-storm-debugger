(ns flow-storm.debugger.repl.nrepl
  (:require [nrepl.core :as nrepl]
            [nrepl.transport :as transport]))

(defn connect [{:keys [host port] :or {host "localhost"}}]
  (let [transport (nrepl/connect :host host
                                 :port port
                                 :transport-fn #'transport/bencode)
        client (nrepl/client transport Long/MAX_VALUE)
        session (nrepl/client-session client)]

    {:repl-eval (fn repl-eval [code-str ns]
                  (let [msg (cond-> {:op "eval" :code code-str}
                              ns (assoc :ns ns))
                        responses (nrepl/message session msg)
                        {:keys [err] :as res-map} (nrepl/combine-responses responses)]
                    (if err
                      (throw (ex-info (str "nrepl evaluation error: " err) (assoc res-map :msg msg)))
                      (first (:value res-map)))))

     :close-connection (fn []
                         (.close transport))}))

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
