(ns user
  (:require [dev]
            [com.stuartsierra.component :as sierra.component]))

(defn stop-system! []
  (alter-var-root #'dev/system sierra.component/stop-system))

(defn start-system! []
  (alter-var-root #'dev/system sierra.component/start-system))
