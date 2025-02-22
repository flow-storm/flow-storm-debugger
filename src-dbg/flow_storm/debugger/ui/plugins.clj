(ns flow-storm.debugger.ui.plugins
  (:require [flow-storm.utils :as utils :refer [log-error]]))

(defonce *plugins (atom {}))

(defn register-plugin [key {:keys [label on-create on-focus on-flow-clear css-resource dark-css-resource light-css-resource]}]
  (swap! *plugins assoc key {:plugin/key key
                             :plugin/label label
                             :plugin/on-create on-create
                             :plugin/on-focus on-focus
                             :plugin/on-flow-clear on-flow-clear
                             :plugin/create-result nil
                             :plugin/css-resource css-resource
                             :plugin/dark-css-resource dark-css-resource
                             :plugin/light-css-resource light-css-resource}))

(defn create-plugin [plugin-key]
  (try
    (when-let [{:keys [plugin/on-create]} (get @*plugins plugin-key)]
      (let [create-res (on-create nil)]
        (swap! *plugins assoc-in [plugin-key :plugin/create-result] create-res)
        create-res))
    (catch Exception e
      (log-error (str "Error creating plugin " plugin-key e )))))

(defn plugins []
  (vals @*plugins))
