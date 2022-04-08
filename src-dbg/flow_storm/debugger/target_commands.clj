(ns flow-storm.debugger.target-commands)

;; All this is now implemented by calling directly to flow-storm.core
;; but needs to be decoulpled so it can be used in remote debuggers

(defn run-command [command & params]
  (let [fn-symb (case command
                  :instrument-fn        'flow-storm.core/instrument-var
                  :uninstrument-fn      'flow-storm.core/uninstrument-var
                  :uninstrument-fn-bulk 'flow-storm.core/uninstrument-vars
                  :eval-form-bulk       'flow-storm.core/eval-form-bulk
                  :instrument-form-bulk 'flow-storm.core/instrument-form-bulk
                  :re-run-flow          'flow-storm.core/re-run-flow)
        f (resolve fn-symb)]
    ;; need to run this in a different thread so it doesn't block the UI thread
    (.start (Thread.
             (fn []
               (apply f params))))))
