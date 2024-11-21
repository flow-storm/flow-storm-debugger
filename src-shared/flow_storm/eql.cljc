(ns flow-storm.eql
  (:require [flow-storm.utils :as utils]))

(defn entity? [data]
  (and (map? data)
       (every? keyword? (keys data))))

(defn eql-query [data q]
  (cond

    (not (coll? data))
    data

    (entity? data)
    (reduce (fn [res-map sel]
              (cond
                
                (and (symbol? sel)
                     (#{'* '?} sel))
                (let [ks (into [] (keys data))]
                  (cond
                    (= '* sel) (eql-query data ks)
                    (= '? sel) ks))
                
                (not (coll? sel))                  
                (assoc res-map sel (get data sel))
                
                (map? sel)
                (reduce-kv (fn [mq-res-map k q]
                             (if (contains? data k)
                               (assoc mq-res-map k (eql-query (get data k) q))
                               mq-res-map))
                           res-map
                           sel)

                ))
            {}
            q)

    ;; non entity maps
    (map? data)
    (utils/update-values data #(eql-query % q))
    
    ;; every other collection
    :else
    (into (empty data) (map #(eql-query % q) data))))

(comment
  
  (def data
    [{:name "Bob"
      :age 41
      :houses {1 {:rooms 5
                  :address "A"}
               2 {:rooms 3
                  :address "B"}}}
     {:name "Alice"
      :age 32
      :vehicles [{:type :car
                  :wheels 4
                  :seats #{{:kind :small :position :left}
                           {:kind :small :position :right}
                           {:kind :big :position :center}}}
                 {:type :bike
                  :wheels 2}]}])

  
  (eql-query data '[*])
  (eql-query data '[:name])
  (eql-query data '[:name :age :vehicles])
  (eql-query data '[:name :age {:vehicles [:type]}])
  (eql-query data '[:name :age {:vehicles [?]}])

  (eql-query data '[:name {:vehicles [*]}])
  (eql-query data '[:name :age {:vehicles [:type {:seats [?]}]}])
  (eql-query data '[:name :age {:vehicles [:type {:seats [:kind]}]}])

  (eql-query data '[:name {:houses [:rooms]}])

  )
