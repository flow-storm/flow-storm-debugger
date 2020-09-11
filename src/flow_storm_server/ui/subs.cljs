(ns flow-storm-server.ui.subs
  (:require [re-frame.core :refer [reg-sub]]))


(reg-sub ::state (fn [db _] db))
