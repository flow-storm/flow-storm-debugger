(ns flow-storm-debugger.highlighter)

(defn inc-last [v]
  (update v (dec (count v)) inc))

;; Leaving quotes out for now
;; There is a bug when we try to highlight functions that
;; have documentation, and specialy parenthesis inside the docstring
;; TODO: fix the bug
(def separator #{\space \tab \newline \,})
(def open-par    #{ \[ \( \{  }) ;; \"
(def closing-par #{ \] \) \}  }) ;; \"
(def close-par { \[ \], \( \), \{ \} }) ;; \" \"

(defn skip-separator [s idx]
  (loop [[c & rs] s
         i idx]
    (cond
      (not c) nil
      (separator c) (recur rs (inc i))
      :else [(str c (apply str rs)) i])))

(defn find-coor-idx [form-str target-coor]
  (loop [[c & rs] form-str
         sidx 0
         coor []
         in-str? false]
    (cond

      (not c) nil

      (= target-coor coor) sidx

      (and in-str? (not= c \")) ;; if we are in a string skip everything until we find a quot
      (recur rs (inc sidx) coor true)

      (= c \")
      (recur rs (inc sidx) coor (not in-str?)) ;; toggle in-str? when we see a quot

      (open-par c)
      (recur rs (inc sidx) (conj coor 0) in-str?)

      (closing-par c)
      (recur rs (inc sidx) (vec (butlast coor)) in-str?)

      (separator c)
      (when-let [[s i] (skip-separator rs sidx)]
        (recur s (inc i) (inc-last coor) in-str?))

      :else
      (recur rs (inc sidx) coor in-str?))))

(defn next-token
  "assumes expr-str start with the beginning of a token"
  [expr-str]

  (loop [[c & rs] expr-str
         token ""]
    (if (or (not c) (separator c) (open-par c) (closing-par c))
      token
      (recur rs (str token c)))))

(defn next-expr [expr-str]
  (let [open (first expr-str)
        close (close-par open)]
    (loop [[c & rs] (rest expr-str)
           expr (str open)
           lvl 1]
      (cond
        (zero? lvl) expr
        (not c)     nil
        (= open c)  (recur rs (str expr c) (inc lvl))
        (= close c) (recur rs (str expr c) (dec lvl))
        :else       (recur rs (str expr c) lvl)))))

(defn find-expr-indexes [expr-str coor]
  (let [start-idx (find-coor-idx expr-str coor)
        [c :as str-from-coord] (subs expr-str start-idx)
        coord-expr (if (open-par c)
                     (next-expr str-from-coord)
                     (next-token str-from-coord))]
    [start-idx (+ start-idx (count coord-expr))]))

(defn highlight-expr [expr-str coor beg-str end-str]
  (let [[start-idx end-idx] (find-expr-indexes expr-str coor)]
    (str (subs expr-str 0 start-idx)
         beg-str
         (subs expr-str start-idx end-idx)
         end-str
         (subs expr-str end-idx))))


(comment
  (require '[flow-storm.api :as a])
  (a/connect)

  (find-coor-idx "(1 2 3)" [1])
  (find-coor-idx "(token 2 3)" [1])
  (find-coor-idx "(token otro 3)" [1])
  (find-coor-idx "(token (otro) 3)" [1])
  (find-coor-idx "(token (otro 3 {:a 5}) 3)" [2])
  (find-coor-idx "(token (otro 3 {:a 5}) 3)" [1 2 1])
  (find-coor-idx "(token  \n (otro \t 3 {:a 5}) 3)" [1 2 1])

  (find-expr-indexes "(token (otro 3 {:a 5}) 3)" [1 2])

  (highlight-expr "(let [a (+ 1 2) b (+ a a)] (map inc (range 10)))" [1 1] "|" "|")
  (highlight-expr "(->> (range 10)
                               (map inc)
                               (filter odd?)
                               (reduce +))" [4 1] "|" "|")

  (highlight-expr "(defn walk-indexed \"Some string (pars) \" [a b] (+ a b))" [4] "|" "|")
  (highlight-expr "(defn walk-indexed \"Some string \" [a b] (+ a b))" [4] "|" "|")


  )
