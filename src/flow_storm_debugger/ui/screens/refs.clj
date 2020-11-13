(ns flow-storm-debugger.ui.screens.refs
  (:require [cljfx.api :as fx]
            [flow-storm-debugger.ui.events :as ui.events]
            [flow-storm-debugger.ui.subs.refs :as subs.refs]))

(defn refs-tabs [{:keys [fx/context]}]
  {:fx/type :label
   :text "Refs"})

(defn no-refs [{:keys [fx/context]}]
  {:fx/type :label
   :text "NO Refs"})
