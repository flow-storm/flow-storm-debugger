(ns flow-storm.types)

(defrecord ValueRef [vid])

(defn value-ref? [x]
  (instance? ValueRef x))

(defn make-value-ref [vid]
  (->ValueRef vid))

#?(:clj (defmethod print-method ValueRef [vref ^java.io.Writer w]
          (.write w (str "#flow-storm.types/value-ref " (:vid vref))))
   
   :cljs (extend-protocol IPrintWithWriter
           ValueRef
           (-pr-writer [vref writer _]
             (write-all writer (str "#flow-storm.types/value-ref " (:vid vref))))))
