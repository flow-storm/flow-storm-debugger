(ns flow-storm.utils)

(defn read-trace-tag [form]
  `(flow-storm.core/instrument ~form))

(defn read-rtrace-tag [form]
  `(flow-storm.api/runi ~form))

#?(:clj
   (defn colored-string [s c]
     (let [color {:red 31
                  :yellow 33}]
       (format "\033[%d;1;1m%s\033[0m" (color c) s)))

   :cljs
   (defn colored-string [_ _]
     "UNIMPLEMENTED"))

#?(:clj
   (defn log [msg]
     (println msg))
   :cljs
   (defn log [msg]
     (js/console.log msg)))

#?(:clj
   (defn log-error
     ([msg] (binding [*out* *err*]
              (println msg)))
     ([msg e]
      (binding [*out* *err*]
        (println msg)
        (.printStackTrace e))))
   :cljs
   (defn log-error
     ([msg] (js/console.error msg))
     ([msg e]
      (js/console.error msg e))))
