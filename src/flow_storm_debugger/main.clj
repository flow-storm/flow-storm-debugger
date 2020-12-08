(ns flow-storm-debugger.main
  (:require [com.stuartsierra.component :as sierra.component]
            [flow-storm-debugger.components.server :as server]
            [flow-storm-debugger.components.ui :as ui]
            [flow-storm-debugger.ui.screens.main :as screens.main]
            [clojure.tools.cli :as tools-cli]
            [taoensso.timbre :as log])
  (:gen-class))

(def cli-options
  [["-fs" "--font-size size" "Font Size"
    :parse-fn #(Integer/parseInt %)
    :default 13]
   ["-p" "--port PORT" "Port number"
    :default 7722
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-t" "--theme THEME" "Theme, can be light or dark"
    :default :dark
    :parse-fn #(keyword %)
    :validate [#(#{:light :dark} %) "Must be `dark` or `light`"]]
   ["-h" "--help"]
   ["-v" "--verbose" "Enable verbose mode, will log a bunch of internal information"]])

(defn -main [& args]
  (let [parsed-args (tools-cli/parse-opts args cli-options)]

    (if (or (-> parsed-args :options :help)
            (not-empty (:errors parsed-args)))

      (do
        (println "Usage : flow-storm-debugger [OPTIONS]")
        (when-let [errors (-> parsed-args :errors)]
          (println errors))
        (println (-> parsed-args :summary))
        (System/exit 0))

      (let [port      (-> parsed-args :options :port)
            font-size (-> parsed-args :options :font-size)
            theme     (-> parsed-args :options :theme)
            system (sierra.component/system-map
                    :ui (ui/ui {:main-cmp screens.main/main-screen
                                :theme theme
                                :font-size font-size})
                    :server (sierra.component/using
                             (server/http-server {:port port})
                             [:ui]))]
        
        (if (-> parsed-args :options :verbose)
          (log/merge-config! {:min-level :debug
                              :appenders {:println {:min-level :debug}}})
          (log/merge-config! {:min-level :info
                              :appenders {:println {:min-level :info}}}))

        (log/info "Starting system...")
        (sierra.component/start-system system)
        (log/info "System started.")))))
