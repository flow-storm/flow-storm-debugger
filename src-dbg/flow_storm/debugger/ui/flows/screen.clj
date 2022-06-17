(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler run-now v-box h-box label icon]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.target-commands :as target-commands])
  (:import [javafx.scene.control Tab TabPane TabPane$TabClosingPolicy]
           [javafx.geometry Side]))

(defn remove-flow [flow-id]
  (let [[^TabPane flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        [flow-tab] (obj-lookup flow-id "flow_tab")]

    (when flow-tab
      ;; remove the tab from flows_tabs_pane
      (-> flows-tabs-pane
          .getTabs
          (.remove flow-tab)))

    ;; clean ui state vars
    (ui-vars/clean-flow-objs flow-id)))

(defn create-empty-flow [flow-id]
  (let [[^TabPane flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        threads-tab-pane (doto (TabPane.)
                           (.setTabClosingPolicy TabPane$TabClosingPolicy/UNAVAILABLE))
        _ (store-obj flow-id "threads_tabs_pane" threads-tab-pane)
        flow-tab (doto (if (= flow-id dbg-state/orphans-flow-id)
                         (doto (Tab.) (.setGraphic (icon "mdi-filter")))
                         (Tab. (str "flow-" flow-id)))
                   (.setOnCloseRequest (event-handler
                                        [ev]
                                        (dbg-state/remove-flow flow-id)
                                        (remove-flow flow-id)
                                        ;; since we are destroying this tab, we don't need
                                        ;; this event to propagate anymore
                                        (.consume ev)))
                   (.setContent threads-tab-pane))]
    (store-obj flow-id "flow_tab" flow-tab)
    (-> flows-tabs-pane
        .getTabs
        (.addAll [flow-tab]))))

(defn- create-thread-controls-pane [flow-id thread-id]
  (let [first-btn (doto (ui-utils/icon-button "mdi-page-first")
                   (.setOnAction (event-handler [ev] (flow-code/jump-to-coord flow-id thread-id 0))))
        prev-btn (doto (ui-utils/icon-button "mdi-chevron-left")
                   (.setOnAction (event-handler
                                  [ev]
                                  (flow-code/jump-to-coord flow-id
                                                 thread-id
                                                 (dec (dbg-state/current-trace-idx flow-id thread-id))))))
        curr-trace-lbl (label "0")
        separator-lbl (label "/")
        thread-trace-count-lbl (label "-")
        _ (store-obj flow-id (ui-vars/thread-curr-trace-lbl-id thread-id) curr-trace-lbl)
        _ (store-obj flow-id (ui-vars/thread-trace-count-lbl-id thread-id) thread-trace-count-lbl)
        {:keys [flow/execution-expr]} (dbg-state/get-flow flow-id)
        execution-expression? (and (:ns execution-expr)
                                   (:form execution-expr))
        next-btn (doto (ui-utils/icon-button "mdi-chevron-right")
                   (.setOnAction (event-handler
                                  [ev]
                                  (flow-code/jump-to-coord flow-id
                                                 thread-id
                                                 (inc (dbg-state/current-trace-idx flow-id thread-id))))))
        last-btn (doto (ui-utils/icon-button "mdi-page-last")
                   (.setOnAction (event-handler
                                  [ev]
                                  (flow-code/jump-to-coord flow-id
                                                           thread-id
                                                           (dec (dbg-state/thread-trace-count flow-id thread-id))))))
        re-run-flow-btn (doto (ui-utils/icon-button "mdi-cached")
                          (.setOnAction (event-handler
                                         [_]
                                         (when execution-expression?
                                           (target-commands/run-command :re-run-flow {:flow-id flow-id :execution-expr execution-expr}))))
                          (.setDisable (not execution-expression?)))
        trace-pos-box (doto (h-box [curr-trace-lbl separator-lbl thread-trace-count-lbl] "trace-position-box")
                        (.setSpacing 2.0))
        controls-box (doto (h-box [first-btn prev-btn re-run-flow-btn next-btn last-btn])
                       (.setSpacing 2.0))]

    (doto (h-box [controls-box trace-pos-box] "thread-controls-pane")
      (.setSpacing 2.0))))

(defn- create-thread-pane [flow-id thread-id]
  (let [thread-pane (v-box [])
        thread-controls-pane (create-thread-controls-pane flow-id thread-id)
        thread-tools-tab-pane (doto (TabPane.)
                               (.setTabClosingPolicy TabPane$TabClosingPolicy/UNAVAILABLE)
                               (.setSide (Side/BOTTOM)))
        code-tab (doto (Tab.)
                   (.setGraphic (icon "mdi-code-parentheses"))
                   (.setContent (flow-code/create-code-pane flow-id thread-id)))
        callstack-tree-tab (doto (Tab.)
                             (.setGraphic (icon "mdi-file-tree"))
                             (.setContent (flow-tree/create-call-stack-tree-pane flow-id thread-id))
                             (.setOnSelectionChanged (event-handler [_] (flow-tree/update-call-stack-tree-pane flow-id thread-id))))
        instrument-tab (doto (Tab.)
                         (.setGraphic (icon "mdi-format-list-numbers"))
                         (.setContent (flow-fns/create-functions-pane flow-id thread-id))
                         (.setOnSelectionChanged (event-handler [_] (flow-fns/update-functions-pane flow-id thread-id))))]

    (store-obj flow-id (ui-vars/thread-tool-tab-pane-id thread-id) thread-tools-tab-pane)

    ;; make thread-tools-tab-pane take the full height
    (-> thread-tools-tab-pane
        .prefHeightProperty
        (.bind (.heightProperty thread-pane)))

    (-> thread-tools-tab-pane
        .getTabs
        (.addAll [code-tab callstack-tree-tab instrument-tab]))

    (-> thread-pane
        .getChildren
        (.addAll [thread-controls-pane thread-tools-tab-pane]))

    thread-pane))

(defn create-empty-thread [flow-id thread-id]
  (run-now
   (let [[threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")
         thread-tab-pane (create-thread-pane flow-id thread-id)
         thread-tab (doto (Tab. (str "thread-" thread-id))
                      (.setContent thread-tab-pane))]
     (-> threads-tabs-pane
           .getTabs
           (.addAll [thread-tab])))))

(defn main-pane []
  (let [tab-pane (doto (TabPane.)
                   (.setTabClosingPolicy TabPane$TabClosingPolicy/ALL_TABS))]
    (store-obj "flows_tabs_pane" tab-pane)
    tab-pane))
