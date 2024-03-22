(ns flow-storm.debugger.ui.flows.general
  (:require [flow-storm.debugger.state :as dbg-state :refer [obj-lookup]]
            [flow-storm.debugger.ui.utils :as ui-utils]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :as shell]))

(defn select-thread-tool-tab [flow-id thread-id tool]
  (let [[thread-tools-tab-pane] (obj-lookup flow-id thread-id "thread_tool_tab_pane_id")
        sel-model (.getSelectionModel thread-tools-tab-pane)
        idx (case tool
              :call-tree 0
              :code 1
              :functions 2)]
    (.select sel-model idx)
    (.requestFocus thread-tools-tab-pane)))

(defn select-main-tools-tab [tool]
  (let [[main-tools-tab] (obj-lookup "main-tools-tab")
        sel-model (.getSelectionModel main-tools-tab)]
    (case tool
      :flows (.select sel-model 0)
      :browser (.select sel-model 1)
      :taps (.select sel-model 2)
      :docs (.select sel-model 3))))

(defn show-message [msg msg-type]
  (try
    (ui-utils/run-later
     (let [dialog (ui-utils/alert-dialog {:type msg-type
                                          :message msg
                                          :buttons [:close]
                                          :center-on-stage (dbg-state/main-jfx-stage)})]
       (.show dialog)))
    (catch Exception _)))

(defn open-form-in-editor
  ([form] (open-form-in-editor form nil))
  ([form line]
   (try
     (let [form-file (:form/file form)
           file-path (try
                       (.toExternalForm
                        (or (io/resource form-file)
                            (.toURL (when-let [f (io/file form-file)]
                                      (and (.exists f) f)))))
                       (catch Exception _ nil))]

       (if-not file-path

         (show-message "There is no file info associated with this form. Maybe it was typed at the repl?" :warning)

         (let [editor-jar-pattern (System/getProperty "flowstorm.jarEditorCommand")
               editor-file-pattern  (System/getProperty "flowstorm.fileEditorCommand")
               form-line (or line (some-> form :form/form meta :line))
               ;; If form-file is inside a jar it, file-path will be like :
               ;;    jar:file:/home/jmonetta/.m2/repository/org/clojure/data.codec/0.2.0/data.codec-0.2.0.jar!/clojure/data/codec/base64.clj
               ;; while if it is in your source directories it will be like :
               ;;    file:/home/jmonetta/my-projects/flow-storm-debugger/src-dev/dev_tester.clj

               command (cond

                         (str/starts-with? file-path "jar:file:/")
                         (if editor-jar-pattern

                           (let [[_ jar-path file-path] (re-find #"jar:file:(/.+\.jar)\!/(.+)" file-path)]
                             (-> editor-jar-pattern
                                 (str/replace "<<JAR>>" jar-path)
                                 (str/replace "<<FILE>>" file-path)
                                 (str/replace "<<LINE>>" (str (or form-line 0)))))
                           (do
                             (show-message "No editor set to open jar files. Please provide the jvm option flowstorm.jarEditorCommand. Refer to the user guide for more info." :info)
                             nil))

                         (str/starts-with? file-path "file:/")
                         (if editor-file-pattern
                           (let [[_ file-path] (re-find #"file:(/.+)" file-path)]
                             (-> editor-file-pattern
                                 (str/replace "<<FILE>>" file-path)
                                 (str/replace "<<LINE>>" (str (or form-line 0)))))
                           (do
                             (show-message "No editor set to open files. Please provide the jvm option flowstorm.fileEditorCommand. Refer to the user guide for more info." :info)
                             nil))

                         :else (throw (Exception. (str "Don't know how to open this file " form-file))))]

           (when command
             (utils/log (str "Running : " command))
             (apply shell/sh (str/split command #" "))))))
     (catch Exception e
       (utils/log-error (.getMessage e))))))
