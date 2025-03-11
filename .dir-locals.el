((clojure-mode . ((cider-clojure-cli-aliases . "dev:dev-tools:storm")
				  (clojure-dev-menu-name . "flow-storm-dev-menu")                  
                  (cider-jack-in-nrepl-middlewares . ("refactor-nrepl.middleware/wrap-refactor"
                                                      "cider.nrepl/cider-middleware"
                                                      "flow-storm.nrepl.middleware/wrap-flow-storm")))))
