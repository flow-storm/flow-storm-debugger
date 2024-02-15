(ns flow-storm.types
  (:require [flow-storm.utils :as utils]))

(defrecord ValueRef [vid])

(defn value-ref? [x]
  (instance? ValueRef x))

(defn make-value-ref [vid]
  (->ValueRef vid))

(defn add-val-preview [vref v]
  (with-meta
    vref
    {:val-preview (binding [*print-level* 4
                            *print-length* 20
                            *print-meta* false]
                    (pr-str v))}))

(defn vref-preview [vref]
  (-> vref meta :val-preview))

(defn serialize-val-ref [vref]
  (utils/format "[%d %s]"
                (:vid vref)
                (-> vref meta :val-preview pr-str)))

(defn deserialize-val-ref [[vid vprev]]
  (with-meta (make-value-ref vid)
    {:val-preview vprev}))

#?(:clj (defmethod print-method ValueRef [vref ^java.io.Writer w]
          (.write w ^String (str "#flow-storm.types/value-ref " (:vid vref))))
   
   :cljs (extend-protocol IPrintWithWriter
           ValueRef
           (-pr-writer [vref writer _]
             (write-all writer (str "#flow-storm.types/value-ref " (:vid vref))))))
