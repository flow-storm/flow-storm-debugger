(ns flow-storm.preload
  (:require [flow-storm.runtime.debuggers-api :as dbg-api]))

(def dbg-port (js/parseInt
               (if js/window
                 (let [page-params (-> js/window .-location .-search)
                       url-params (js/URLSearchParams. page-params)]
                   (or (.get url-params "flowstorm_ws_port") "7722"))
                 ;; for node js
                 "7722")))

(dbg-api/start-runtime)
(dbg-api/remote-connect {:debugger-host "localhost" :debugger-ws-port dbg-port})
