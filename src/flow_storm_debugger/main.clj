(ns flow-storm-debugger.main
  (:require [com.stuartsierra.component :as sierra.component]
            [flow-storm-debugger.components.server :as server]
            [flow-storm-debugger.components.ui :as ui]
            [flow-storm-debugger.ui.screens.main :as screens.main])
  (:gen-class))

(defn -main [& args]
  (let [port 7722]
    (if (= (first args) "--spit-style-files")

      (do ;; TODO: fix this
        #_(spit "./flow-storm-code-panel-styles.css" (slurp code-panel-styles) )
        #_(spit "./flow-storm-app-styles.css" (slurp app-styles))
        (println "You can customize application styles by editing ./flow-storm-app-styles.css")
        (println "You can customize code browser styles by editing ./flow-storm-code-panel-styles.css")
        (System/exit 0))
      
      (let [system (sierra.component/system-map
                    :ui (ui/ui {:main-cmp screens.main/main-screen})
                    :server (sierra.component/using
                             (server/http-server {:port port})
                             [:ui]))]
        (println "Starting system...")
        (sierra.component/start-system system)
        (println "System started.")))))
