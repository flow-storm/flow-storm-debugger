(ns flow-storm-debugger.ui.utils
  (:require [clojure.string :as str]
            [cljs.tools.reader :as tools-reader]
            [zprint.core :as zp]))

(defn escape-html [s]
  (str/escape s {\< "&lt;" \> "&gt;"}))

(defn pprint-form-for-html [s]
  (try
   (-> s
       tools-reader/read-string
       zp/zprint-str
       escape-html)
   (catch :default e
     (js/console.error "Couldn't pprint for html :" e)
     (js/console.error "String" s)
     s)))
