(ns user
  (:import [javafx.application Platform]
           [com.sun.javafx.logging PlatformLogger$Level]
           [flow-storm.utils :refer [log]]
           [com.sun.javafx.util Logging]))

(Platform/startup (fn [] (log "JavaFX toolkit initialized")))
