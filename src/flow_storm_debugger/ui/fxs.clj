(ns flow-storm-debugger.ui.fxs)

(defn save-file-fx [{:keys [file-name file-content]} dispatch!]
  (spit file-name file-content))
