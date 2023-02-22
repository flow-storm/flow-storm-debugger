(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label icon tab-pane tab text-field]]
            [flow-storm.debugger.state :as dbg-state]))

(defn remove-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        [flow-tab] (obj-lookup flow-id "flow_tab")]

    (when flow-tab

      ;; remove the tab from flows_tabs_pane
      (-> flows-tabs-pane
          .getTabs
          (.remove flow-tab)))

    ;; clean ui state vars
    (ui-vars/clean-flow-objs flow-id)))

(defn fully-remove-flow [flow-id]
  ;; remove it from our state
  (dbg-state/remove-flow flow-id)

  ;; let runtime know we are not interested in this flow anymore
  (runtime-api/discard-flow rt-api flow-id)

  ;; remove it from the ui
  (remove-flow flow-id))

(defn create-empty-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        threads-tab-pane (tab-pane {:closing-policy :all-tabs
                                    :drag-policy :reorder})
        flow-tab (if (nil? flow-id)
                   (tab {:graphic (icon "mdi-filter") :content threads-tab-pane})
                   (tab {:text (str "flow-" flow-id) :content threads-tab-pane}))]

    (.setOnCloseRequest flow-tab
                        (event-handler
                         [ev]
                         (fully-remove-flow flow-id)
                         ;; since we are destroying this tab, we don't need
                         ;; this event to propagate anymore
                         (.consume ev)))

    (store-obj flow-id "threads_tabs_pane" threads-tab-pane)
    (store-obj flow-id "flow_tab" flow-tab)
    (-> flows-tabs-pane
        .getTabs
        (.addAll [flow-tab]))))

(defn- create-thread-controls-pane [flow-id thread-id]
  (let [first-btn (ui-utils/icon-button :icon-name "mdi-page-first"
                                        :on-click (fn [] (flow-code/jump-to-coord flow-id thread-id 0)))
        prev-btn (ui-utils/icon-button :icon-name "mdi-chevron-left"
                                       :on-click (fn [] (flow-code/step-prev flow-id thread-id)))

        curr-trace-text-field (doto (text-field {:initial-text "1"
                                            :on-return-key (fn [idx-str]
                                                             (flow-code/jump-to-coord flow-id
                                                                                      thread-id
                                                                                      (dec (Long/parseLong idx-str))))
                                                 :align :right})
                                (.setPrefWidth 80))

        separator-lbl (label "/")
        thread-trace-count-lbl (label "?")
        _ (store-obj flow-id (ui-vars/thread-curr-trace-tf-id thread-id) curr-trace-text-field)
        _ (store-obj flow-id (ui-vars/thread-trace-count-lbl-id thread-id) thread-trace-count-lbl)
        {:keys [flow/execution-expr]} (dbg-state/get-flow flow-id)
        execution-expression? (and (:ns execution-expr)
                                   (:form execution-expr))
        next-btn (ui-utils/icon-button :icon-name "mdi-chevron-right"
                                       :on-click (fn [] (flow-code/step-next flow-id thread-id)))

        last-btn (ui-utils/icon-button :icon-name "mdi-page-last"
                                       :on-click (fn []
                                                   (let [tl-count (runtime-api/timeline-count rt-api flow-id thread-id )]
                                                     (flow-code/jump-to-coord flow-id
                                                                              thread-id
                                                                              (dec tl-count)))))

        re-run-flow-btn (ui-utils/icon-button :icon-name "mdi-cached"
                                              :on-click (fn []
                                                          (when execution-expression?
                                                            (runtime-api/eval-form rt-api (:form execution-expr) {:instrument? false
                                                                                                                  :ns (:ns execution-expr)})))
                                              :disable (not execution-expression?))


        trace-pos-box (doto (h-box [curr-trace-text-field separator-lbl thread-trace-count-lbl] "trace-position-box")
                        (.setSpacing 2.0))
        controls-box (doto (h-box [first-btn prev-btn re-run-flow-btn next-btn last-btn])
                       (.setSpacing 2.0))]

    (doto (h-box [controls-box trace-pos-box] "thread-controls-pane")
      (.setSpacing 2.0))))

(defn- create-thread-pane [flow-id thread-id]
  (let [thread-controls-pane (create-thread-controls-pane flow-id thread-id)
        code-tab (tab {:graphic (icon "mdi-code-parentheses")
                       :content (flow-code/create-code-pane flow-id thread-id)})

        callstack-tree-tab (tab {:graphic (icon "mdi-file-tree")
                                 :content (flow-tree/create-call-stack-tree-pane flow-id thread-id)
                                 :on-selection-changed (event-handler [_] (flow-tree/update-call-stack-tree-pane flow-id thread-id))})

        instrument-tab (tab {:graphic (icon "mdi-format-list-numbers")
                             :content (flow-fns/create-functions-pane flow-id thread-id)
                             :on-selection-changed (event-handler [_] (flow-fns/update-functions-pane flow-id thread-id))})
        thread-tools-tab-pane (tab-pane {:tabs [callstack-tree-tab code-tab instrument-tab]
                                         :side :bottom
                                         :closing-policy :unavailable})
        thread-pane (v-box [thread-controls-pane thread-tools-tab-pane])]

    (store-obj flow-id (ui-vars/thread-tool-tab-pane-id thread-id) thread-tools-tab-pane)

    ;; make thread-tools-tab-pane take the full height
    (-> thread-tools-tab-pane
        .prefHeightProperty
        (.bind (.heightProperty thread-pane)))

    thread-pane))

(defn create-empty-thread [flow-id thread-id]
  (let [[threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")
        thread-tab-pane (create-thread-pane flow-id thread-id)
        thread-tab (tab {:text (str "thread-" thread-id)
                         :content thread-tab-pane})]
    (-> threads-tabs-pane
        .getTabs
        (.addAll [thread-tab]))))

(defn main-pane []
  (let [t-pane (tab-pane {:closing-policy :all-tabs
                          :side :top})]
    (store-obj "flows_tabs_pane" t-pane)
    t-pane))
