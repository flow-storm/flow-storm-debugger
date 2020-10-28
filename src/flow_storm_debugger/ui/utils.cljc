(ns flow-storm-debugger.ui.utils
  (:require [clojure.string :as str]
            #?(:cljs [cljs.tools.reader :as tools-reader])
            [zprint.core :as zp]))

(defn escape-html [s]
  (str/escape s {\< "&lt;" \> "&gt;"}))

(defn read-str [s]
  #?(:cljs (tools-reader/read-string s)
     :clj (read-string s)))

(defn get-timestamp []
  #?(:cljs (.getTime (js/Date.))
     :clj (inst-ms (java.util.Date.))))

(defn pprint-form-for-html [s]
  (try
   (-> s
       read-str
       zp/zprint-str
       escape-html)
   #?(:cljs (catch :default e (js/console.warn "Couldn't pprint: " s) s)
      :clj (catch Exception e (println "Couldn't pprint" s) s))))

(defn remove-vals
  "Removes all key entries from map where the value is v"
  [m v]
  (reduce-kv (fn [r mk mv]
               (if (not= mv v)
                 (assoc r mk mv)
                 r))
             {}
             m))
