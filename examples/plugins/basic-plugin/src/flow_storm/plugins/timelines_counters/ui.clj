(ns flow-storm.plugins.timelines-counters.ui
  (:require [flow-storm.debugger.ui.plugins :as fs-plugins]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [clojure.string :as str])
  (:import [javafx.scene.control Label Button TextField]
           [javafx.scene.layout HBox VBox]
           [javafx.event EventHandler]
           [javafx.scene Node]))

(fs-plugins/register-plugin
 :timelines-counter
 {:label "Timelines counter"
  :on-create (fn [_]
               (let [counts-lbl (Label. "")
                     flow-id-txt (TextField. "0")
                     refresh-btn (doto (Button. "Refresh")
                                   (.setOnAction
                                    (reify javafx.event.EventHandler
                                      (handle [_ _]
                                        (->> (runtime-api/call-by-fn-key rt-api
                                                                         :plugins.timelines-counters/timelines-counts
                                                                         [(parse-long (.getText flow-id-txt))])
                                             (mapv (fn [[thread-id cnt]]
                                                     (format "ThreadId: %d, Timeline Count: %d" thread-id cnt)))
                                             (str/join "\n")
                                             (.setText counts-lbl))))))
                     tools-box (HBox. (into-array Node [(Label. "FlowId:") flow-id-txt refresh-btn]))
                     main-box (VBox. (into-array Node [tools-box
                                                       counts-lbl]))]
                 {:fx/node main-box}))})
