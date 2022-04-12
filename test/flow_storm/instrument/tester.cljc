(ns flow-storm.instrument.tester)

(defprotocol Shape
  (area [_]))

;; Defrecord implementing a protocol
(defrecord Rectangle [w h]  
  Shape
  (area [r]
    (* (:w r) (:h r))))

(defrecord Circle [r])

;; Extending a protocol
(extend-protocol Shape

  Circle
  (area [c]
    (* Math/PI (Math/pow (:r c) 2))))

(deftype Triangle [b h])

;; Extending a type
(extend-type Triangle
  Shape
  (area [t]
    (/ (* (.-b t) (.-h t)) 2)))

(defn area-sum [shapes]
  (reduce + (map area shapes)))

(defmulti perimeter :type)

(defmethod perimeter :rectangle [{:keys [w h]}]
  (* 2 (+ w h)))

(defmethod perimeter :circle [{:keys [r]}]
  (* 2 Math/PI r))

(defn perimeter-sum [shapes]
  (reduce + (map perimeter shapes)))

(defn build-lazy
  ([] (build-lazy 0))
  ([n]
   (lazy-seq
    (when (< n 2)
      (cons n (build-lazy (+ n 2)))))))

(defn do-all []
  (let [map-shapes [{:type :circle :r 5}
                    {:type :rectangle :w 7 :h 8}]
        rec-shapes [(->Circle 5)
                    (->Rectangle 7 8)
                    (->Triangle 10 5)]]
    (+
     (perimeter-sum map-shapes)
     (area-sum rec-shapes)
     (reduce + (build-lazy)))))

(comment
(do-all)
  


  
  )
