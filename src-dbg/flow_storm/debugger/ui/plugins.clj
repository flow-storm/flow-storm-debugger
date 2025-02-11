(ns flow-storm.debugger.ui.plugins)

(defonce *plugins (atom {}))

(defn register-plugin [key {:keys [label on-create after-mount dark-css-resource light-css-resource]}]
  (swap! *plugins assoc key {:plugin/key key
                             :plugin/label label
                             :plugin/on-create on-create
                             :plugin/after-mount after-mount
                             :plugin/dark-css-resource dark-css-resource
                             :plugin/light-css-resource light-css-resource}))

(defn plugins []
  (vals @*plugins))
