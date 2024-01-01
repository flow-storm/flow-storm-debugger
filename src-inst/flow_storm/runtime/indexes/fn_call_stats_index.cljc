(ns flow-storm.runtime.indexes.fn-call-stats-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :refer [mh-put make-mutable-hashmap mh-contains? mh-get mh->immutable-map]]))

(deftype FnCall
    #?(:clj  [^int form-id fn-name fn-ns]
       :cljs [form-id fn-name fn-ns]) ;; if I type hint this with in, then the compiler complains on -hash that form-id is a [number int]
  

  #?@(:cljs
      [IHash
       (-hash [_]              
              (unchecked-add-int
               (unchecked-multiply-int 31 form-id)
               (hash fn-name)))
       
       IEquiv
       (-equiv
        [this other]        
        (and (= ^js/Number (.-form-id this) ^js/Number (.-form-id other))
             (= ^js/String (.-fn-name this) ^js/String (.-fn-name other))))])
  #?@(:clj
      [Object
       (hashCode [_]
                 (unchecked-add-int
                  (unchecked-multiply-int 31 form-id)
                  (.hashCode ^String fn-name)))

       (equals [_ o]
               (and (= form-id ^int (.-form-id ^FnCall o))
                    (.equals ^String fn-name ^String (.-fn-name ^FnCall o))))]))

(defrecord FnCallStatsIndex [stats]

  index-protos/RecorderP
  
  (add-fn-call [_ fn-ns fn-name form-id _]    
    (let [call (->FnCall form-id
                         fn-name
                         fn-ns)]
      
      (if (mh-contains? stats call)
        (let [cnt (mh-get stats call)]          
          (mh-put stats call (inc cnt)))
        (mh-put stats call 1))))
  
  index-protos/FnCallStatsP

  (all-stats [_]    
    (reduce-kv (fn [r ^FnCall fc cnt]
                 (let [k {:form-id (.-form-id fc)
                          :fn-name (.-fn-name fc)
                          :fn-ns (.-fn-ns fc)}]
                   (assoc r k cnt)))
               {}
               (mh->immutable-map stats))))

(defn make-index []
  (->FnCallStatsIndex (make-mutable-hashmap)))

