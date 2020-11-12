(ns flow-storm-debugger.ui.keymap
  (:require [flow-storm-debugger.ui.events :as events])
  (:import [javafx.scene.input KeyCode KeyEvent]))

(defn key-event->key-desc [^KeyEvent kevt]
  (.getText kevt))


(def keymap
  {
   "x" ::events/remove-selected-flow
   "X" ::events/remove-all-flows
   })
