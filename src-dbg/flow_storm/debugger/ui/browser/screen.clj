(ns flow-storm.debugger.ui.browser.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label button add-class list-view]]
            [flow-storm.utils :refer [log-error] :as utils]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup show-message] :as ui-vars]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [clojure.string :as str])
  (:import [javafx.scene.control CheckBox SplitPane]
           [javafx.scene Node]
           [javafx.geometry Orientation Pos]
           [javafx.scene.input MouseButton]))

(defn make-inst-var [var-ns var-name]
  {:inst-type :var
   :var-ns var-ns
   :var-name var-name})

(defn make-inst-ns
  ([ns-name] (make-inst-ns ns-name nil))
  ([ns-name profile]
   {:inst-type :ns
    :profile profile
    :ns-name ns-name}))

(defn make-inst-break [fq-fn-symb]
  {:inst-type :break
   :var-ns (namespace fq-fn-symb)
   :var-name (name fq-fn-symb)})

(defn clear-instrumentation-list []
  (let [[{:keys [clear]}] (obj-lookup "browser-observable-instrumentations-list-data")]
    (clear)))

(defn add-to-instrumentation-list [inst]
  (let [[{:keys [add-all get-all-items]}] (obj-lookup "browser-observable-instrumentations-list-data")
        items (get-all-items)
        already-added? (some (fn [item]
                               (boolean
                                (= item inst)))
                             items)]
    (when-not already-added?
      (add-all [inst]))))

(defn remove-from-instrumentation-list [inst]
  (let [[{:keys [remove-all]}] (obj-lookup "browser-observable-instrumentations-list-data")]
    (remove-all [inst])))

(defn- add-breakpoint [fq-fn-symb]
  (runtime-api/add-breakpoint rt-api fq-fn-symb))

(defn- remove-breakpoint [fq-fn-symb]
  (runtime-api/remove-breakpoint rt-api fq-fn-symb))

(defn- instrument-function
  ([var-ns var-name] (instrument-function var-ns var-name {}))
  ([var-ns var-name config]
   (when (or (= var-ns "clojure.core") (= var-ns "cljs.core"))
     (show-message "Instrumenting clojure.core is almost never a good idea since the debugger itself uses the namespace and you can easily end in a infinite recursion." :warning))
   (runtime-api/instrument-var rt-api (str var-ns) (str var-name) config)))

(defn- uninstrument-function
  ([var-ns var-name] (uninstrument-function var-ns var-name {}))
  ([var-ns var-name config]
   (runtime-api/uninstrument-var rt-api (str var-ns) (str var-name) config)))

(defn- instrument-namespaces
  ([namepsaces] (instrument-namespaces namepsaces {}))
  ([namepsaces config]
   (if (some (fn [{:keys [ns-name] :as ns}] (when (= ns-name "clojure.core") ns)) namepsaces)
     (show-message "Instrumenting entire clojure.core is not a good idea. The debugger itself uses the namespace and you will end up in a infinite recursion" :warning)

     (runtime-api/instrument-namespaces rt-api (map :ns-name namepsaces) (assoc config :profile (-> namepsaces first :profile))))))

(defn- uninstrument-namespaces
  ([namespaces] (uninstrument-namespaces namespaces {}))
  ([namespaces config]
   (runtime-api/uninstrument-namespaces rt-api (map :ns-name namespaces) config)))

(defn- update-selected-fn-detail-pane [{:keys [added ns name file static line arglists doc]}]
  (let [[browser-instrument-button]       (obj-lookup "browser-instrument-button")
        [browser-instrument-rec-button]   (obj-lookup "browser-instrument-rec-button")
        [browser-break-button]            (obj-lookup "browser-break-button")
        [selected-fn-fq-name-label]       (obj-lookup "browser-selected-fn-fq-name-label")
        [selected-fn-added-label]         (obj-lookup "browser-selected-fn-added-label")
        [selected-fn-file-label]          (obj-lookup "browser-selected-fn-file-label")
        [selected-fn-static-label]        (obj-lookup "browser-selected-fn-static-label")
        [selected-fn-args-list-v-box]     (obj-lookup "browser-selected-fn-args-list-v-box")
        [selected-fn-doc-label]           (obj-lookup "browser-selected-fn-doc-label")

        args-lists-labels (map (fn [al] (label (str al))) arglists)]

    (add-class browser-instrument-button "enable")
    (add-class browser-instrument-rec-button "enable")
    (add-class browser-break-button "enable")
    (.setOnAction browser-instrument-button (event-handler [_] (instrument-function ns name)))
    (.setOnAction browser-instrument-rec-button (event-handler [_] (instrument-function ns name {:deep? true})))
    (.setOnAction browser-break-button (event-handler [_] (add-breakpoint (symbol (str ns) (str name)))))

    (.setText selected-fn-fq-name-label (format "%s" #_ns name))
    (when added
      (.setText selected-fn-added-label (format "Added: %s" added)))
    (.setText selected-fn-file-label (format "File: %s:%d" file line))
    (when static
      (.setText selected-fn-static-label "Static: true"))
    (-> selected-fn-args-list-v-box .getChildren .clear)
    (.addAll (.getChildren selected-fn-args-list-v-box)
             (into-array Node args-lists-labels))
    (.setText selected-fn-doc-label doc)))

(defn- update-vars-pane [vars]
  (let [[{:keys [clear add-all]}] (obj-lookup "browser-observable-vars-list-data")]
    (clear)
    (add-all (sort-by :var-name vars))))

(defn- update-namespaces-pane [namespaces]
  (let [[{:keys [clear add-all]}] (obj-lookup "browser-observable-namespaces-list-data")]
    (clear)
    (add-all (sort namespaces))))

(defn get-var-meta [{:keys [var-name var-ns]}]
  (let [var-meta (runtime-api/get-var-meta rt-api var-ns var-name)]
    (ui-utils/run-later (update-selected-fn-detail-pane var-meta))))

(defn- get-all-vars-for-ns [ns-name]
  (let [all-vars (->> (runtime-api/get-all-vars-for-ns rt-api ns-name)
                      (map (fn [vn]
                             (make-inst-var ns-name vn))))]
    (ui-utils/run-later (update-vars-pane all-vars))))

(defn get-all-namespaces []
  (let [all-namespaces (runtime-api/get-all-namespaces rt-api)]
    (ui-utils/run-later (update-namespaces-pane all-namespaces))))

(defn create-namespaces-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell ns-name]
                                       (.setText list-cell nil)
                                       (.setGraphic list-cell (label ns-name)))
                    :on-click (fn [mev sel-items {:keys [list-view-pane]}]
                                (cond (= MouseButton/PRIMARY (.getButton mev))
                                      (let [sel-cnt (count sel-items)]
                                        (when (= 1 sel-cnt)
                                          (get-all-vars-for-ns (first sel-items))))

                                      (and (= MouseButton/SECONDARY (.getButton mev))
                                           (not ui-vars/clojure-storm-env?))
                                      (let [ctx-menu-instrument-ns-light {:text "Instrument namespace :light"
                                                                          :on-click (fn []
                                                                                      (instrument-namespaces (map #(make-inst-ns % :light) sel-items)))}
                                            ctx-menu-instrument-ns-full {:text "Instrument namespace :full"
                                                                         :on-click (fn []
                                                                                     (instrument-namespaces (map #(make-inst-ns % :full) sel-items)))}
                                            ctx-menu (ui-utils/make-context-menu [ctx-menu-instrument-ns-light
                                                                                  ctx-menu-instrument-ns-full])]

                                        (.show ctx-menu
                                               list-view-pane
                                               (.getScreenX mev)
                                               (.getScreenY mev)))))
                    :selection-mode :multiple
                    :search-predicate (fn [ns-name search-str]
                                        (str/includes? ns-name search-str))})]

    (store-obj "browser-observable-namespaces-list-data" lv-data)

    list-view-pane))

(defn create-vars-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell {:keys [var-name]}]
                                       (.setText list-cell nil)
                                       (.setGraphic list-cell (label var-name)))
                    :on-click (fn [mev sel-items _]
                                (cond (= MouseButton/PRIMARY (.getButton mev))
                                      (let [sel-cnt (count sel-items)]
                                        (when (= 1 sel-cnt)
                                          (get-var-meta (first sel-items))))))
                    :selection-mode :single
                    :search-predicate (fn [{:keys [var-name]} search-str]
                                        (str/includes? var-name search-str))})]

    (store-obj "browser-observable-vars-list-data" lv-data)
    list-view-pane))

(defn create-fn-details-pane []
  (let [selected-fn-fq-name-label (label "" "browser-fn-fq-name")
        inst-button (button :label "Instrument" :class "browser-instrument-btn")
        break-button (button :label "Break"
                             :class "browser-break-btn"
                             :tooltip "Add a breakpoint to this function. Threads hitting this function will be paused")
        inst-rec-button (button :label "Instrument recursively" :class "browser-instrument-btn")
        btns-box (doto (h-box (if ui-vars/clojure-storm-env?
                                [break-button]
                                [inst-button inst-rec-button break-button])
                              "browser-var-buttons")
                   (.setSpacing 5))
        name-box (doto (h-box [selected-fn-fq-name-label])
                   (.setAlignment Pos/CENTER_LEFT))
        selected-fn-added-label (label "" "browser-fn-attr")
        selected-fn-file-label (label "" "browser-fn-attr")
        selected-fn-static-label (label "" "browser-fn-attr")
        selected-fn-args-list-v-box (v-box [] "browser-fn-args-box")
        selected-fn-doc-label (label "" "browser-fn-attr")

        selected-fn-detail-pane (v-box [name-box
                                        btns-box
                                        selected-fn-args-list-v-box
                                        selected-fn-added-label
                                        selected-fn-doc-label
                                        selected-fn-file-label
                                        selected-fn-static-label])]

    (store-obj "browser-instrument-button" inst-button)
    (store-obj "browser-break-button" break-button)
    (store-obj "browser-instrument-rec-button" inst-rec-button)
    (store-obj "browser-selected-fn-fq-name-label" selected-fn-fq-name-label)
    (store-obj "browser-selected-fn-added-label" selected-fn-added-label)
    (store-obj "browser-selected-fn-file-label" selected-fn-file-label)
    (store-obj "browser-selected-fn-static-label" selected-fn-static-label)
    (store-obj "browser-selected-fn-args-list-v-box" selected-fn-args-list-v-box)
    (store-obj "browser-selected-fn-doc-label" selected-fn-doc-label)

    selected-fn-detail-pane))

(defn- instrumentations-cell-factory [list-cell {:keys [inst-type] :as inst}]
  (try
    (let [inst-box (case inst-type
                     :var (let [{:keys [var-name var-ns]} inst
                                inst-lbl (doto (h-box [(label "VAR INST:" "browser-instr-type-lbl")
                                                       (label (format "%s/%s" var-ns var-name) "browser-instr-label")])
                                           (.setSpacing 10))
                                inst-del-btn (button :label "del"
                                                     :class "browser-instr-del-btn"
                                                     :on-click (fn [] (uninstrument-function var-ns var-name)))]
                            (doto (h-box [inst-lbl inst-del-btn])
                              (.setSpacing 10)
                              (.setAlignment Pos/CENTER_LEFT)))
                     :ns (let [{:keys [ns-name] :as inst-ns} inst
                               inst-lbl (doto (h-box [(label "NS INST:" "browser-instr-type-lbl")
                                                      (label ns-name "browser-instr-label")])
                                          (.setSpacing 10))
                               inst-del-btn (button :label "del"
                                                    :class "browser-instr-del-btn"
                                                    :on-click (fn [] (uninstrument-namespaces [inst-ns])))]
                           (doto (h-box [inst-lbl inst-del-btn])
                             (.setSpacing 10)
                             (.setAlignment Pos/CENTER_LEFT)))
                     :break (let [{:keys [var-ns var-name]} inst
                                  inst-lbl (doto (h-box [(label "VAR BREAK:" "browser-instr-type-lbl")
                                                         (label (format "%s/%s" var-ns var-name) "browser-instr-label")])
                                             (.setSpacing 10))
                                  inst-del-btn (button :label "del"
                                                       :class "browser-instr-del-btn"
                                                       :on-click (fn [] (remove-breakpoint (symbol var-ns var-name))))]
                              (doto (h-box [inst-lbl inst-del-btn])
                                (.setSpacing 10)
                                (.setAlignment Pos/CENTER_LEFT))))]
      (.setGraphic ^Node list-cell inst-box))
    (catch Exception e (log-error e))))

(defn- create-instrumentations-pane []
  (let [{:keys [list-view-pane get-all-items] :as lv-data} (list-view {:editable? false
                                                                      :selection-mode :single
                                                                      :cell-factory-fn instrumentations-cell-factory})
        delete-all-btn (button :label "Delete all"
                               :on-click (fn []
                                           (let [type-groups (group-by :inst-type (get-all-items))
                                                 del-namespaces (:ns type-groups)
                                                 del-vars (:var type-groups)
                                                 del-brks (:break type-groups)]

                                             (uninstrument-namespaces del-namespaces)

                                             (doseq [v del-vars]
                                               (uninstrument-function (:var-ns v) (:var-name v)))

                                             (doseq [b del-brks]
                                               (remove-breakpoint (symbol (:var-ns b) (:var-name b)))))))
        en-dis-chk (doto (CheckBox.)
                     (.setSelected true))
        _ (.setOnAction en-dis-chk
                        (event-handler
                         [_]
                         (let [type-groups (group-by :inst-type (get-all-items))
                               change-namespaces   (:ns type-groups)
                               change-vars (:var type-groups)]

                           (when (seq change-namespaces)
                             (if (.isSelected en-dis-chk)
                               (instrument-namespaces change-namespaces {:disable-events? true})
                               (uninstrument-namespaces change-namespaces {:disable-events? true})))

                           (doseq [v change-vars]
                             (if (.isSelected en-dis-chk)
                               (instrument-function (:var-ns v) (:var-name v) {:disable-events? true})
                               (uninstrument-function (:var-ns v) (:var-name v) {:disable-events? true}))))))
        instrumentations-tools (doto (h-box [(label "Enable all")
                                             en-dis-chk
                                             delete-all-btn]
                                            "browser-instr-tools-box")
                                 (.setSpacing 10)
                                 (.setAlignment Pos/CENTER_LEFT))
        pane (v-box [(label "Instrumentations")
                     instrumentations-tools
                     list-view-pane])]

    (store-obj "browser-observable-instrumentations-list-data" lv-data)

    pane))

(defn main-pane []
  (let [namespaces-pane (create-namespaces-pane)
        vars-pane (create-vars-pane)
        selected-fn-detail-pane (create-fn-details-pane)
        inst-pane (create-instrumentations-pane)
        top-split-pane (doto (SplitPane.)
                         (.setOrientation (Orientation/HORIZONTAL)))
        top-bottom-split-pane (doto (SplitPane.)
                                (.setOrientation (Orientation/VERTICAL)))]

    (-> top-split-pane
        .getItems
        (.addAll [namespaces-pane vars-pane selected-fn-detail-pane]))

    (-> top-bottom-split-pane
        .getItems
        (.addAll [top-split-pane
                  inst-pane]))

    (.setDividerPosition top-split-pane 0 0.3)
    (.setDividerPosition top-split-pane 1 0.6)

    (.setDividerPosition top-bottom-split-pane 0 0.7)

    top-bottom-split-pane
    ))
