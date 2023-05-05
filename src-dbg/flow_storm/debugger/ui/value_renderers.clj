(ns flow-storm.debugger.ui.value-renderers)

(def renderers-registry (atom {}))

(defn register-renderer [id-key pred render-fn]
  (swap! renderers-registry assoc id-key {:pred pred :render-fn render-fn}))

(defn renderers-for-val [v]
  (reduce-kv (fn [rr idk {:keys [pred] :as r}]
               (if (pred v)
                 (assoc rr idk r)
                 rr))
             {}
             @renderers-registry))

(defn load-morse-renderers []
  )
