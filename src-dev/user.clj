(ns user
  (:import [javafx.application Platform]
           [com.sun.javafx.logging PlatformLogger$Level]
           [com.sun.javafx.util Logging]))

(.disableLogging (Logging/getJavaFXLogger))
(Platform/startup (fn [] (println "JavaFX toolkit initialized")))
