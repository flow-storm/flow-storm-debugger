(ns flow-storm-debugger.components.ui
  (:require [com.stuartsierra.component :as sierra.component]
            [cljfx.api :as fx]
            [cljfx.event-handler :as event-handler]
            [cljfx.renderer :as renderer]
            [cljfx.defaults :as defaults]
            [clojure.core.cache :as cache]
            [flow-storm-debugger.ui.events :as ui.events]
            [flow-storm-debugger.ui.fxs :as fxs]
            [flow-storm-debugger.ui.styles :as styles]
            [clojure.java.io :as io]))

(defn create-app "A copy of fx/create-app but that doesn't wrap event-handler in wrap-async."  
  [*context & {:keys [event-handler
                      desc-fn
                      co-effects
                      effects
                      async-agent-options
                      renderer-middleware
                      renderer-error-handler]
               :or {co-effects {}
                    effects {}
                    async-agent-options {}
                    renderer-middleware identity
                    renderer-error-handler renderer/default-error-handler}}]
  (let [handler (-> event-handler
                    (event-handler/wrap-co-effects
                      (defaults/fill-co-effects co-effects *context))
                    (event-handler/wrap-effects
                      (defaults/fill-effects effects *context)))
        renderer (fx/create-renderer
                   :error-handler renderer-error-handler
                   :middleware (comp
                                 fx/wrap-context-desc
                                 (fx/wrap-map-desc desc-fn)
                                 renderer-middleware)
                   :opts {:fx.opt/map-event-handler handler
                          :fx.opt/type->lifecycle #(or (fx/keyword->lifecycle %)
                                                       (fx/fn->lifecycle-with-context %))})]
    (fx/mount-renderer *context renderer)
    {:renderer renderer
     :handler handler}))

(defn swap-state! [ui f]
  (swap! (:state ui) fx/swap-context f))

(defrecord UI [styles watch-styles? state app main-cmp]
  sierra.component/Lifecycle
  (start [this]
    (println "Starting UI...")
    (let [state (atom (fx/create-context {:flows {}
                                          :refs {}
                                          :taps {}
                                          :selected-flow-id nil
                                          :selected-ref-id nil
                                          :selected-tap-id nil
                                          :selected-tool-idx 0
                                          :stats {:connected-clients 0
                                                  :received-traces-count 0}
                                          :open-dialog nil
                                          :styles styles}
                                         cache/lru-cache-factory))

          app (create-app state
                          :event-handler ui.events/dispatch-event
                          :effects {:save-file fxs/save-file-fx}
                          :desc-fn (fn [_] {:fx/type main-cmp})
                          :renderer-error-handler (fn [e] (println e)))
          
          this (assoc this
                      :state state
                      :app app)]

      (when watch-styles?
        (println "Watching styles")
        (add-watch #'styles/style :refresh-app
                   (fn [_ _ _ _]
                     (swap-state! this (fn [s] (assoc-in s [:styles :app-styles] (:cljfx.css/url styles/style)))))))
      
      (println "UI started.")

      this))

  (stop [this]
    (println "Stopping UI...")
    (remove-watch #'styles/style :refresh-app)
    @(fx/unmount-renderer (:state this) (-> this :app :renderer))    
    (println "UI stopped.")
    this))

(defn build-styles []
  (let [code-panel-styles (io/resource "code-panel-styles.css")
        app-styles (:cljfx.css/url styles/style)
        custom-app-styles (io/file "./flow-storm-app-styles.css")
        custom-code-panel-styles (io/file "./flow-storm-code-panel-styles.css")]
   {:app-styles        (or (when (.exists custom-app-styles) (str (.toURI custom-app-styles)))
                           app-styles)
    :font-styles       (str (io/resource "fonts.css"))
    :code-panel-styles (str (or (when (.exists custom-code-panel-styles) (.toURI custom-code-panel-styles))
                                code-panel-styles))}))

(defn ui [{:keys [main-cmp watch-styles?]}]  
  ;;(Platform/setImplicitExit true)
  (map->UI {:styles (build-styles)
            :main-cmp main-cmp
            :watch-styles? watch-styles?}))
