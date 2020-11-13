(ns flow-storm-debugger.ui.screens.components
  (:require [cljfx.api :as fx]
            [cljfx.mutator :as fx.mutator]
            [cljfx.lifecycle :as fx.lifecycle]
            [cljfx.prop :as fx.prop])
  (:import [org.kordamp.ikonli.javafx FontIcon]
           [javafx.scene.web WebView]))

(defn font-icon [_]
  ;; Check icons here
  ;; https://kordamp.org/ikonli/cheat-sheet-materialdesign.html
  {:fx/type fx/ext-instance-factory :create #(FontIcon.)})

(def ext-with-html
  (fx/make-ext-with-props
    {:html (fx.prop/make
            (fx.mutator/setter (fn [web-view html] (.loadContent (.getEngine ^WebView web-view) html)))
            fx.lifecycle/scalar)
     :css-uri (fx.prop/make
               (fx.mutator/setter (fn [web-view css-uri] (.setUserStyleSheetLocation (.getEngine ^WebView web-view) css-uri)))
               fx.lifecycle/scalar)}))
