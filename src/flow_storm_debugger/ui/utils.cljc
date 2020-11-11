(ns flow-storm-debugger.ui.utils
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            #?(:cljs [cljs.tools.reader :as tools-reader])
            [zprint.core :as zp]))

(defn escape-html [s]
  (str/escape s {\< "&lt;"
                 \> "&gt;"
                 \& "&amp;"}))

(defn get-timestamp []
  #?(:cljs (.getTime (js/Date.))
     :clj (inst-ms (java.util.Date.))))

(defn read-form [s]
  (try
    (edn/read-string {:default (fn [tag val]
                                 ;; assuming that all tagged structures support meta
                                 (with-meta val {:tag tag}))}
                     s)
    (catch Exception e (println "Couldn't pprint" s) s)))

(defn pprint-form [form]
  (zp/zprint-str form {:map {:sort? false}}))  ;; don't sort keys since it breaks coordinates

(defn pprint-form-str [s]
  (pprint-form (read-form s)))

(defn pprint-form-for-html [s]
  (escape-html (pprint-form-str s)))

(defn remove-vals
  "Removes all key entries from map where the value is v"
  [m v]
  (reduce-kv (fn [r mk mv]
               (if (not= mv v)
                 (assoc r mk mv)
                 r))
             {}
             m))

(defn parent-coor? 
  "If parent-coor and child-coor are code coordinate vectors
  returns true if parent-coor is parent of child-coor." 
  [parent-coor child-coor]
  (and (not= parent-coor child-coor)
       (< (count parent-coor)
          (count child-coor))
       (every? true? (map = parent-coor child-coor))))
