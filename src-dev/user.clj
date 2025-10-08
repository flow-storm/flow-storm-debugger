(ns user
  (:import [javafx.application Platform]
           [com.sun.javafx.logging PlatformLogger$Level]
           [com.sun.javafx.util Logging])
  (:require [flow-storm.utils :refer [log]]))

(Platform/startup (fn [] (log "JavaFX toolkit initialized")))
