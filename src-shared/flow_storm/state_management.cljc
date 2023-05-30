(ns flow-storm.state-management
  "Like a smaller simplified version of mount so
  we don't bring that dependency with us.
  Since it is a tool lets aim for no libs.")

;; state-name -> {:order :status :start :stop :var}
;; :status [:started | :stopped]
(def states (atom {}))
(def last-order (atom 0))

#?(:clj
   (defn alter-state-var [state-var new-val]
     (alter-var-root state-var (constantly new-val))))

(defn start-state [{:keys [status start var]} config]
  (when-not (= status :started)
    #_(println "Starting " var)    
    (let [new-state (start config)]
      (alter-state-var var new-state))
    (swap! states assoc-in [(str var) :status] :started)))

(defn stop-state [{:keys [status stop var]}]
  (when-not (= status :stopped)
    #_(println "Stopping " var)    
    (let [new-state (stop)]      
      (alter-state-var var new-state))
    (swap! states assoc-in [(str var) :status] :stopped)))

(defn current-state [state-name]
  (when-let [state (get @states state-name)]    
    (deref (:var state))))

(defn register-state [state-name state-map]
  (let [s (get @states state-name)
        order (or (:order s)
                  (swap! last-order inc))
        new-state-map (assoc state-map :order order)]

    ;; stop the current state if needed before we lose the var
    (when s (alter-state-var (:var s) (current-state state-name)))
    
    (swap! states assoc state-name new-state-map)))

(defn start [{:keys [only config]}]
  (let [all-states @states
        effective-states (if only
                           (select-keys all-states (mapv str only))
                           all-states)
        ordered-states (->> effective-states vals (sort-by :order <))]
    (doseq [s ordered-states]      
      (start-state s config))))

(defn stop [{:keys [only]}]
  (let [all-states @states
        effective-states (if only
                           (select-keys all-states (mapv str only))
                           all-states)
        ordered-states (->> effective-states
                            vals
                            (filter (fn [{:keys [status]}]
                                      (= status :started)))
                            (sort-by :order >))]
    (doseq [s ordered-states]
      (stop-state s))))


#?(:clj
   (defmacro defstate [var-name & {:keys [start stop]}]
     `(do
        (defonce ~var-name nil)
        (let [state-name# (str (var ~var-name))]          
          (register-state state-name# {:var (var ~var-name)
                                       :start ~start
                                       :stop ~stop})))))


(comment
  
  ;; (defstate s1
  ;;   :start (fn [_] (println "Starting s1") :s1)
  ;;   :stop  (fn [] (println "Stopping s1")))

  ;; (defstate s2
  ;;   :start (fn [cfg] (println "Starting s2 with config" cfg) :s2)
  ;;   :stop  (fn [] (println "Stopping s2")))

  ;; (defstate s3
  ;;   :start (fn [_] (println "Starting s3") :s3)
  ;;   :stop  (fn [] (println "Stopping s3")))

  ;; (start {:config {:a 10}
  ;;         :only [#'s1 #'s2]})
  ;; (stop)
  )
