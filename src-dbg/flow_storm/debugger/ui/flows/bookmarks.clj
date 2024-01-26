(ns flow-storm.debugger.ui.flows.bookmarks
  (:require [flow-storm.debugger.state :as dbg-state :refer [store-obj obj-lookup]]
            [flow-storm.debugger.ui.utils
             :as ui-utils
             :refer [icon-button table-view label]]
            [clojure.string :as str]
            [flow-storm.utils :refer [log-error]])
  (:import [javafx.scene Scene]
           [javafx.stage Stage]
           [javafx.scene.input MouseButton]))

(defn update-bookmarks []
  (when-let [[{:keys [clear add-all]}] (obj-lookup "bookmarks_table_data")]
    (let [bookmarks (dbg-state/all-bookmarks)]
      (clear)
      (add-all (mapv (fn [b]
                       [(assoc b :cell-type :text,  :text (str (:flow-id b)))
                        (assoc b :cell-type :text,  :text (str (:thread-id b)))
                        (assoc b :cell-type :text,  :text (str (:idx b)))
                        (assoc b :cell-type :text)
                        (assoc b :cell-type :actions)])
                     bookmarks)))))

(defn bookmark-add [flow-id thread-id idx]
  (let [text (ui-utils/ask-text-dialog {:header "Add bookmark"
                                        :body "Bookmark name:"})]
    (dbg-state/add-bookmark flow-id thread-id idx text)
    (update-bookmarks)))

(defn remove-bookmarks [flow-id]
  (dbg-state/remove-bookmarks flow-id)
  (update-bookmarks))

(defn- create-bookmarks-pane []
  (let [cell-factory (fn [_ {:keys [cell-type idx text flow-id thread-id]}]
                       (case cell-type
                         :text (label text)
                         :actions (icon-button :icon-name "mdi-delete-forever"
                                               :on-click (fn []
                                                           (dbg-state/remove-bookmark flow-id thread-id idx)
                                                           (update-bookmarks)))))
        {:keys [table-view-pane] :as tv-data} (table-view
                                               {:columns             ["FlowId" "ThreadId" "Idx" "Bookmarks" ""]
                                                :columns-width-percs [0.1      0.1        0.1   0.6         0.1]
                                                :cell-factory-fn cell-factory
                                                :resize-policy :constrained
                                                :on-click (fn [mev sel-items _]
                                                            (when (and (= MouseButton/PRIMARY (.getButton mev))
                                                                       (= 2 (.getClickCount mev)))
                                                              (let [{:keys [flow-id thread-id idx]} (ffirst sel-items)
                                                                    goto-loc (requiring-resolve 'flow-storm.debugger.ui.flows.screen/goto-location)]
                                                                (goto-loc {:flow-id   flow-id
                                                                           :thread-id thread-id
                                                                           :idx       idx}))))
                                                :selection-mode :multiple
                                                :search-predicate (fn [[_ _ _ bookmark-text] search-str]
                                                                    (str/includes? bookmark-text search-str))})]
    (store-obj "bookmarks_table_data" tv-data)
    table-view-pane))

(defn show-bookmarks []
  (try
    (let [scene (Scene. (create-bookmarks-pane) 800 400)
          stage (doto (Stage.)
                  (.setTitle "FlowStorm bookmarks ")
                  (.setScene scene))]

      (dbg-state/register-and-init-stage! stage)

      (-> stage .show))

    (update-bookmarks)

    (catch Exception e
      (log-error "UI Thread exception" e))))
