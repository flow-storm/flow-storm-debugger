(ns dev
  (:require [flow-storm-debugger.components.server :as server]
            [flow-storm-debugger.components.ui :as ui]
            [flow-storm-debugger.ui.screens.main :as screens.main]
            [flow-storm-debugger.ui.subs.flows :as subs.flows]
            [flow-storm-debugger.ui.subs.general :as subs.general]
            [flow-storm-debugger.ui.subs.taps :as subs.taps]
            [flow-storm-debugger.ui.subs.timeline :as subs.timeline]
            [flow-storm-debugger.ui.db :as db]
            [cljfx.api :as fx]
            [flow-storm-debugger.ui.styles :as styles]
            [clojure.pprint :as pp]
            [com.stuartsierra.component :as sierra.component]))


(def system (sierra.component/system-map
             :ui (ui/ui {:main-cmp screens.main/main-screen
                         :watch-styles? true
                         :theme :dark
                         :font-size 13})
               :server (sierra.component/using
                        (server/http-server {:port 7722})
                        [:ui])))

(defn ui-refresh []
  ((-> system :ui :app :renderer)))

(comment

  ;; TODO:
  ;; - fix events
  
  (def state-ref (-> system :ui :state))
  
  (def state (:cljfx.context/m @state-ref))

  (def ff (subs.flows/flows @state-ref))
  (:flow-id (subs.flows/selected-flow @state-ref))
  
  (subs.general/selected-tool-idx @state-ref)
  
  (subs.timeline/timeline @state-ref)
  
  (subs.taps/taps @state-ref)
  (subs.taps/taps-tabs @state-ref)
  (subs.taps/selected-tap @state-ref)
  (subs.taps/selected-tap-values @state-ref)
  (subs.taps/selected-tap-value-panel-type @state-ref)
  (subs.taps/selected-tap-value-panel-content @state-ref false)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
  (require '[flow-storm.api :as fsa])
  (fsa/connect {:tap-name "TAPPP"})
  
  (tap> "HELLO")
  (tap> {:a 41})
  (tap> state)
  
  (def a-ref (atom {:ages [1 2 3 30]}))

  (fsa/trace-ref a-ref {:ref-name "super-ref-map"})
  
  (swap! a-ref assoc :new-field 42)
  (swap! a-ref assoc :foo-field 0)
  (swap! a-ref assoc :new-other-field [1 2 3])
  (swap! a-ref assoc :crazy-field [1 2 3])
  (swap! a-ref assoc :crazy-field-bla [1 2 3])
  (swap! a-ref assoc :crazy [777])
  (swap! a-ref assoc :crazy2 [777])
  (swap! a-ref assoc :crazy3 777)
  (swap! a-ref update :new-field inc)
  (swap! a-ref dissoc :foo-field)
  
  ;; #trace (->> (range 10) (map (fn [i] (+ i 2))))

  
  ;; #trace (let [a {:a 10 :b [1 2 3]}
  ;;              b 100]
  ;;          (swap! a-ref #(update % :ages conj 42))
  ;;         (+ (:a a) b))

  ;; #trace (+ 1 (:a {:a 1}))

  (spit "./sample-state.edn" state)

  
  (defn factorial [n]
    (if (zero? n)
      1
      (* n (factorial (dec n)))))

  
  (defn bar []
    (+ 2 (* 2 (factorial 5))))

  (+ 1 (+ 3 (bar)))

  
  
  )

