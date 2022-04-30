(ns flow-storm.debugger.target-commands
  (:require [flow-storm.core :as fs-core]))

;; All this is now implemented by calling directly to flow-storm.core
;; but needs to be decoulpled so it can be used in remote debuggers

(defn run-command [command & params]
  (let [f (case command
            :instrument-fn        fs-core/instrument-var
            :uninstrument-fn      fs-core/uninstrument-var
            :uninstrument-fn-bulk fs-core/uninstrument-vars
            :eval-form-bulk       fs-core/eval-form-bulk
            :instrument-form-bulk fs-core/instrument-form-bulk
            :re-run-flow          fs-core/re-run-flow)]
    ;; need to run this in a different thread so it doesn't block the UI thread
    (.start (Thread.
             (fn []
               (apply f params))))))
