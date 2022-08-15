(ns flow-storm.events
  (:require [flow-storm.remote-websocket-client :as remote-websocket-client]
            [flow-storm.runtime.values :as rt-values]))

(def debugger-events-processor-ns 'flow-storm.debugger.events-processor)

(def tap-fn

  "Keep a reference to the added tap function so we can remove it"

  (atom nil))

(defn send-event-to-debugger [ev]
  (let [packet [:event ev]]
    (if (remote-websocket-client/remote-connected?)

      ;; send the packet remotely
      (remote-websocket-client/send-event-to-debugger packet)

      ;; "send" locally (just call a function)
      #?(:clj
         (let [local-process-event (resolve (symbol (name debugger-events-processor-ns) "process-event"))]
           (local-process-event ev))
         :cljs nil)))) ;; just so clj-kondo doesn't complain

(defn tap-value [remote? v]
  (send-event-to-debugger [:tap {:value (rt-values/make-value v remote?)}]))

(defn setup-tap! [remote?]
  (let [new-tap-fn (partial tap-value remote?)]
    (add-tap new-tap-fn)
    (reset! tap-fn new-tap-fn)))

(defn remove-tap! []
  (when-let [tf @tap-fn]
      (remove-tap tf)))

