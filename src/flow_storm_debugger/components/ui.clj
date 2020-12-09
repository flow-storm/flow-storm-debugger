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
            [clojure.java.io :as io]
            [taoensso.timbre :as log]))

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
                    (fx/wrap-co-effects
                     {:fx/context (fn [] (deref *context))})
                    (fx/wrap-effects
                     (merge
                      {:context (fn [v _] (reset! *context v))
                       :dispatch (fn [v dispatch!] (dispatch! v))}
                      effects)))
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

(defn deref-state [state-ref]
  (:cljfx.context/m @state-ref))

(defn swap-state! [state-ref f]
  (swap! state-ref fx/swap-context f))

(defrecord UI [font-size theme watch-styles? state app main-cmp]
  sierra.component/Lifecycle
  (start [this]
    (log/info "Starting UI...")
    (reset! styles/theme theme)
    (reset! styles/font-size font-size)
    (let [styles (styles/build-styles)
          state (atom (fx/create-context {:flows {}
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
                                         (fn [base]
                                           ;; if this number is too small we are not going to be able
                                           ;; to cache many subscriptions and they will have to be
                                           ;; constantly recalculated affecting UX
                                           (cache/lru-cache-factory base :threshold 10000))))

          app (create-app state
                          :event-handler ui.events/dispatch-event
                          :effects {:save-file fxs/save-file-fx}
                          :desc-fn (fn [_] {:fx/type main-cmp})
                          ;; :renderer-error-handler (fn [e] (println e))
                          )
          
          this (assoc this
                      :state state
                      :app app)]

      (when watch-styles?
        (log/info "Watching styles")
        (add-watch #'styles/style :refresh-app
                   (fn [_ _ _ _]
                     (swap-state! state (fn [s] (assoc-in s [:styles :app-styles] (:cljfx.css/url @styles/style)))))))
      
      (log/info "UI started.")

      this))

  (stop [this]
    (log/info "Stopping UI...")
    (remove-watch #'styles/style :refresh-app)
    @(fx/unmount-renderer (:state this) (-> this :app :renderer))    
    (log/info "UI stopped.")
    this))

(defn ui [{:keys [font-size theme main-cmp watch-styles?]}]  
  ;;(Platform/setImplicitExit true)
  (map->UI {:font-size font-size
            :theme theme
            :main-cmp main-cmp
            :watch-styles? watch-styles?}))
