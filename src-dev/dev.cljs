(ns dev
  (:require [flow-storm.json-serializer :as ser]
            [flow-storm.trace-types :as tt]
            [flow-storm.api :as fs-api]))

#trace
(defn factorial [n]
  (if (zero? n)
    1
    (* n (factorial (dec n)))))

(defn -main [& args]
  (fs-api/remote-connect)
  (fs-api/run {:ns "dev"}
    (js/console.log (factorial 5))))

;; (comment
;;   (def t (tt/->FnCallTrace 0 1 "foo" "foo-ns" 11 [42 41] 0))

;;   (ser/serialize t)
;;   (ser/deserialize (ser/serialize t))
;;   (ser/deserialize (ser/serialize #"h.*"))

;;   (fs-api/remote-connect)
;;   (fs-api/close-remote-connection)

;;   (fs-api/run {:ns "dev"}
;;     (factorial 5))

;;   "[\"~#flow_storm.trace_types.FnCallTrace\",[\"^ \",\"~:flow-id\",0,\"~:form-id\",1,\"~:fn-name\",\"foo\",\"~:fn-ns\",\"foo-ns\",\"~:thread-id\",11,\"~:args-vec\",[42,41],\"~:timestamp\",0]]"
;;   "[\"~#flow_storm.trace_types.FnCallTrace\",[\"^ \",\"~:flow-id\",0,\"~:fn-name\",\"foo\",\"~:thread-id\",11,\"~:form-id\",1,\"~:fn-ns\",\"foo-ns\",\"~:args-vec\",[42,41],\"~:timestamp\",0]]"
;;   )
