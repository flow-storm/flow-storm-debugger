(ns flow-storm.debugger.ui.commons
  (:require [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.ui.utils :refer [set-clipboard]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [clojure.string :as str]))

(defn def-val
  ([val] (def-val val {:stage (dbg-state/main-jfx-stage)}))
  ([val {:keys [stage]}]
   (let [val-name (ui/ask-text-dialog
                   :header "Def var with name. You can use / to provide a namespace, otherwise will be defined under [cljs.]user "
                   :body "Var name :"
                   :width  500
                   :height 100
                   :center-on-stage stage)]
     (when-not (str/blank? val-name)
       (runtime-api/def-value rt-api (symbol val-name) val)))))

(defn copy-val [val-ref]
  (let [val-pprint (->> (runtime-api/val-pprint rt-api val-ref {:print-length 10000 :print-level 500 :pprint? true})
                        :val-str)]
    (set-clipboard val-pprint)))
