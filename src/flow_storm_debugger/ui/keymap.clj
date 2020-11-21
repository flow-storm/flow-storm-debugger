(ns flow-storm-debugger.ui.keymap
  (:import [javafx.scene.input KeyCode KeyEvent]))

(defn key-event->key-desc [^KeyEvent kevt]
  (.getText kevt))


(def keymap
  {
   "x" :flow-storm-debugger.ui.events/remove-selected-flow
   "X" :flow-storm-debugger.ui.events/remove-all-flows
   })
