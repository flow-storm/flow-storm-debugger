(ns flow-storm.debugger.ui.browser.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [add-class]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.utils :refer [log-error] :as utils]
            [flow-storm.debugger.state :refer [store-obj obj-lookup] :as dbg-state]
            [flow-storm.debugger.ui.flows.general :refer [show-message]]
            [flow-storm.debugger.runtime-api :as runtime-api :refer [rt-api]]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

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

(defn- add-breakpoint [fq-fn-symb opts]
  (runtime-api/add-breakpoint rt-api fq-fn-symb opts))

(defn- remove-breakpoint [fq-fn-symb opts]
  (runtime-api/remove-breakpoint rt-api fq-fn-symb opts))

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

(defmacro disabled-with-storm [& forms]
  `(if-not (dbg-state/clojure-storm-env?)
     (do ~@forms)
     (show-message "This functionality is disabled when running with ClojureStorm or ClojureScriptStorm" :warning)))

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

        args-lists-labels (map (fn [al] (ui/label :text (str al))) arglists)]

    (add-class browser-instrument-button "enable")
    (add-class browser-instrument-rec-button "enable")
    (add-class browser-break-button "enable")
    (ui-utils/set-button-action browser-instrument-button (fn [] (disabled-with-storm (instrument-function ns name))))
    (ui-utils/set-button-action browser-instrument-rec-button (fn [] (disabled-with-storm (instrument-function ns name {:deep? true}))))
    (ui-utils/set-button-action browser-break-button (fn [] (add-breakpoint (symbol (str ns) (str name)) {})))

    (ui-utils/set-text selected-fn-fq-name-label (format "%s" #_ns name))
    (when added
      (ui-utils/set-text selected-fn-added-label (format "Added: %s" added)))
    (ui-utils/set-text selected-fn-file-label (format "File: %s:%d" file line))
    (when static
      (ui-utils/set-text selected-fn-static-label "Static: true"))

    (-> selected-fn-args-list-v-box ui-utils/pane-children ui-utils/observable-clear)

    (ui-utils/add-childrens-to-pane selected-fn-args-list-v-box args-lists-labels)

    (ui-utils/set-text selected-fn-doc-label doc)))

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
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell ns-name]
                                      (-> list-cell
                                          (ui-utils/set-text  nil)
                                          (ui-utils/set-graphic (ui/label :text ns-name))))
                      :on-click (fn [mev sel-items {:keys [list-view-pane]}]
                                  (when (and (ui-utils/mouse-secondary? mev)
                                             (not (dbg-state/clojure-storm-env?)))
                                    (let [ctx-menu-instrument-ns-light {:text "Instrument namespace :light"
                                                                        :on-click (fn []
                                                                                    (instrument-namespaces (map #(make-inst-ns % :light) sel-items)))}
                                          ctx-menu-instrument-ns-full {:text "Instrument namespace :full"
                                                                       :on-click (fn []
                                                                                   (instrument-namespaces (map #(make-inst-ns % :full) sel-items)))}
                                          ctx-menu (ui/context-menu :items [ctx-menu-instrument-ns-light
                                                                            ctx-menu-instrument-ns-full])]

                                      (ui-utils/show-context-menu :menu ctx-menu
                                                                  :parent list-view-pane
                                                                  :mouse-ev mev))))
                      :on-selection-change (fn [_ sel-ns] (when sel-ns (get-all-vars-for-ns sel-ns)))
                      :selection-mode :multiple
                      :search-predicate (fn [ns-name search-str]
                                          (str/includes? ns-name search-str)))]

    (store-obj "browser-observable-namespaces-list-data" lv-data)

    list-view-pane))

(defn create-vars-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (ui/list-view :editable? false
                      :cell-factory (fn [list-cell {:keys [var-name]}]
                                      (-> list-cell
                                          (ui-utils/set-text nil)
                                          (ui-utils/set-graphic (ui/label :text var-name))))
                      :on-selection-change (fn [_ sel-var]
                                             (when sel-var
                                               (get-var-meta sel-var)))
                      :selection-mode :single
                      :search-predicate (fn [{:keys [var-name]} search-str]
                                          (str/includes? var-name search-str)))]

    (store-obj "browser-observable-vars-list-data" lv-data)
    list-view-pane))

(defn create-fn-details-pane []
  (let [selected-fn-fq-name-label (ui/label :text "" :class "browser-fn-fq-name")
        inst-button (ui/button :label "Instrument" :classes ["browser-instrument-btn" "btn-sm"])
        break-button (ui/button :label "Break"
                                :classes ["browser-break-btn" "btn-sm"]
                                :tooltip "Add a breakpoint to this function. Threads hitting this function will be paused")
        inst-rec-button (ui/button :label "Instrument recursively" :classes ["browser-instrument-btn" "btn-sm"])
        btns-box (ui/h-box :childs [inst-button inst-rec-button break-button]
                           :class "browser-var-buttons"
                           :spacing 5)

        name-box (ui/h-box :childs [selected-fn-fq-name-label]
                           :align :center-left)

        selected-fn-added-label (ui/label :text "" :class "browser-fn-attr")
        selected-fn-file-label (ui/label :text "" :class "browser-fn-attr")
        selected-fn-static-label (ui/label :text "" :class "browser-fn-attr")
        selected-fn-args-list-v-box (ui/v-box :childs []
                                              :class "browser-fn-args-box")
        selected-fn-doc-label (ui/label :text "" :class "browser-fn-attr")

        selected-fn-detail-pane (ui/v-box
                                 :childs [name-box
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
                                inst-lbl (ui/h-box :childs [(ui/label :text "VAR INST:"                      :class "browser-instr-type-lbl")
                                                            (ui/label :text (format "%s/%s" var-ns var-name) :class "browser-instr-label")]
                                                   :spacing 10)

                                inst-del-btn (ui/button :label "del"
                                                        :classes ["browser-instr-del-btn" "btn-sm"]
                                                        :on-click (fn [] (uninstrument-function var-ns var-name)))]
                            (ui/h-box :childs [inst-lbl inst-del-btn]
                                      :spacing 10
                                      :align :center-left))
                     :ns (let [{:keys [ns-name] :as inst-ns} inst
                               inst-lbl (ui/h-box :childs [(ui/label :text "NS INST:" :class "browser-instr-type-lbl")
                                                           (ui/label :text ns-name    :class "browser-instr-label")]
                                                  :spacing 10)

                               inst-del-btn (ui/button :label "del"
                                                       :classes ["browser-instr-del-btn" "btn-sm"]
                                                       :on-click (fn [] (uninstrument-namespaces [inst-ns])))]
                           (ui/h-box :childs [inst-lbl inst-del-btn]
                                     :spacing 10
                                     :align :center-left))
                     :break (let [{:keys [var-ns var-name]} inst
                                  inst-lbl (ui/h-box :childs [(ui/label :text "VAR BREAK:"                     :class "browser-instr-type-lbl")
                                                              (ui/label :text (format "%s/%s" var-ns var-name) :class "browser-instr-label")]
                                                     :spacing 10)

                                  inst-del-btn (ui/button :label "del"
                                                          :classes ["browser-instr-del-btn" "btn-sm"]
                                                          :on-click (fn [] (remove-breakpoint (symbol var-ns var-name) {})))]
                              (ui/h-box :childs [inst-lbl inst-del-btn]
                                        :spacing 10
                                        :align :center-left)))]
      (ui-utils/set-graphic list-cell inst-box))
    (catch Exception e (log-error e))))

(defn- create-instrumentations-pane []
  (let [{:keys [list-view-pane get-all-items] :as lv-data} (ui/list-view :editable? false
                                                                         :selection-mode :single
                                                                         :cell-factory instrumentations-cell-factory)
        delete-all-btn (ui/button :label "Delete all"
                                  :on-click (fn []
                                              (let [type-groups (group-by :inst-type (get-all-items))
                                                    del-namespaces (:ns type-groups)
                                                    del-vars (:var type-groups)
                                                    del-brks (:break type-groups)]

                                                (uninstrument-namespaces del-namespaces)

                                                (doseq [v del-vars]
                                                  (uninstrument-function (:var-ns v) (:var-name v)))

                                                (doseq [b del-brks]
                                                  (remove-breakpoint (symbol (:var-ns b) (:var-name b)) {})))))
        en-dis-chk (ui/check-box :selected? true)

        instrumentations-tools (ui/h-box :childs [(ui/label :text "Enable all")
                                                  en-dis-chk
                                                  delete-all-btn]
                                         :class "browser-instr-tools-box"
                                         :spacing 10
                                         :align :center-left)

        pane (ui/v-box
              :childs [(ui/label :text "Instrumentations")
                       instrumentations-tools
                       list-view-pane])]

    (ui-utils/set-button-action en-dis-chk
                                (fn []
                                  (let [type-groups (group-by :inst-type (get-all-items))
                                        change-namespaces (:ns type-groups)
                                        change-vars (:var type-groups)
                                        breakpoints (:break type-groups)]

                                    (when (seq change-namespaces)
                                      (if (ui-utils/checkbox-checked? en-dis-chk)
                                        (instrument-namespaces change-namespaces {:disable-events? true})
                                        (uninstrument-namespaces change-namespaces {:disable-events? true})))

                                    (doseq [v change-vars]
                                      (if (ui-utils/checkbox-checked? en-dis-chk)
                                        (instrument-function (:var-ns v) (:var-name v) {:disable-events? true})
                                        (uninstrument-function (:var-ns v) (:var-name v) {:disable-events? true})))

                                    (doseq [{:keys [var-ns var-name]} breakpoints]
                                      (if (ui-utils/checkbox-checked? en-dis-chk)
                                        (add-breakpoint (symbol var-ns var-name) {:disable-events? true})
                                        (remove-breakpoint (symbol var-ns var-name) {:disable-events? true}))))))

    (store-obj "browser-observable-instrumentations-list-data" lv-data)

    pane))

(defn main-pane []
  (let [namespaces-pane (create-namespaces-pane)
        vars-pane (create-vars-pane)
        selected-fn-detail-pane (create-fn-details-pane)
        inst-pane (create-instrumentations-pane)
        top-split-pane (ui/split :orientation :horizontal
                                 :childs [namespaces-pane vars-pane selected-fn-detail-pane]
                                 :sizes [0.3 0.6])
        top-bottom-split-pane (ui/split :orientation :vertical
                                        :childs [top-split-pane inst-pane]
                                        :sizes [0.7])]
    top-bottom-split-pane))
