(ns flow-storm-debugger.ui.db
  (:require [cljfx.api :as fx]
            [clojure.core.cache :as cache]))

(defonce *state
  (atom (fx/create-context {:flows {}
                            :refs {}
                            :selected-flow-id nil
                            :selected-ref-id nil
                            :selected-tool-idx 0
                            :stats {:connected-clients 0
                                    :received-traces-count 0}
                            :open-dialog nil}
                           cache/lru-cache-factory)))
