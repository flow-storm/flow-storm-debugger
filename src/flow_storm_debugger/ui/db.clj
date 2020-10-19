(ns flow-storm-debugger.ui.db
  (:require [cljfx.api :as fx]
            [clojure.core.cache :as cache]
            [flow-storm-debugger.ui.styles :as styles]))

(defonce *state
  (atom (fx/create-context {:flows {}
                            :selected-flow-id nil                            
                            :stats {:connected-clients 0
                                    :received-traces-count 0}
                            :open-dialog nil
                            :style styles/style}
                           cache/lru-cache-factory)))
