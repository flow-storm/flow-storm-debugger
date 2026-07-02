(ns flow-storm.debugger.ui.flows.general
  (:require [flow-storm.debugger.state :as dbg-state :refer [obj-lookup]]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler]]
            [flow-storm.debugger.ui.components :as ui]
            [flow-storm.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pp]
            [flow-storm.form-pprinter :as form-pprinter])
  (:import [javafx.scene.control TabPane]
           [java.io File]
           [java.net URL]
           [org.fxmisc.richtext CodeArea]
           [org.fxmisc.richtext.model StyleSpansBuilder TwoDimensional$Bias]
           [javafx.scene.input MouseEvent]))

(defn select-thread-tool-tab [flow-id thread-id tab-id]
  (let [[^TabPane thread-tools-tab-pane] (obj-lookup flow-id thread-id "thread_tool_tab_pane_id")
        sel-model (.getSelectionModel thread-tools-tab-pane)
        tab (some (fn [t]
                    (when (= tab-id (.getId t ))
                      t))
                  (.getTabs thread-tools-tab-pane))]
    (ui-utils/selection-select-obj sel-model tab)
    (.requestFocus thread-tools-tab-pane)))

(defn select-main-tools-tab [tab-id]
  (let [[^TabPane main-tools-tab] (obj-lookup "main-tools-tab")
        sel-model (.getSelectionModel main-tools-tab)
        tab (some (fn [t]
                    (when (= tab-id (.getId t ))
                      t))
                  (.getTabs main-tools-tab))]
    (ui-utils/selection-select-obj sel-model tab)))

(defn show-message [msg msg-type]
  (try
    (ui-utils/run-later
      (ui/alert-dialog :type msg-type
                       :message msg
                       :buttons [:close]
                       :center-on-stage (dbg-state/main-jfx-stage)
                       :height 200
                       :width 700))
    (catch Exception _)))

(defn open-form-in-editor
  ([form] (open-form-in-editor form nil))
  ([form line]
   (try
     (let [form-file (:form/file form)
           file-path (try
                       (let [url (or (io/resource form-file)
                                     (let [file (when-let [f (io/file form-file)]
                                                  (and (.exists ^File f) f))]
                                       (.toURL ^File file)))]
                         (.toExternalForm ^URL url))
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
             (apply shell/sh (utils/quoted-string-split command \space))))))
     (catch Exception e
       (utils/log-error (.getMessage e))))))


(defn- maybe-unwrap-runi-tokens

  "Unwrap <EXPR> from (fn* flowstorm-runi ([] <EXPR>)) if it is wrapped,
  returns print-tokens otherwise.
  Works at print-tokens level instead of at form level."

  [print-tokens]

  ;; This is as hacky as it gets but it is also for the vanilla non fn expressions
  ;; typed at the repl, like (+ 1 2)
  (if-let [runi-token-idx (some (fn [[i {:keys [text]}]]
                                  (when (= text "flowstorm-runi")
                                    i))
                                (map vector (range) (take 10 print-tokens)))]
    (let [expands-into-multiple-lines? (= runi-token-idx 5)
          wrap-beg (if expands-into-multiple-lines? 13 9)
          wrap-end (- (count print-tokens) 2)
          sub-tokens (subvec print-tokens wrap-beg wrap-end)
          expr-offset (+ (count "(fn* flowstorm-runi ([] ")
                         (if expands-into-multiple-lines?
                           4
                           0))]
      (->> sub-tokens
           ;; since we removed some tokens we need to move all idx-from back
           (map (fn [{:keys [idx-from] :as tok}]
                  (if idx-from
                    (update tok :idx-from #(- % expr-offset))
                    tok)))))

    print-tokens))

(defn- calculate-execution-idx-range [spans curr-coord]
  (when curr-coord
    (let [[s1 s2 :as hl-coords-spans] (->> spans
                                          (map-indexed (fn [i s] (assoc s :i i)))
                                          (filter (fn [{:keys [coord]}] (= coord curr-coord))))]
     (case (count hl-coords-spans)
       1 [(:idx-from s1) (+ (:idx-from s1) (:len s1))]
       2 [(:idx-from s1) (+ (:idx-from s2) (:len s2))]
       nil))))

(defn- build-style-spans

  "Given coord-spans as generated by `form-pprinter/coord-spans` build
  StyleSpans to be used in RichTextFX CodeAreas"

  [coord-spans curr-coord]

  (let [^StyleSpansBuilder spb (StyleSpansBuilder.)
        [exec-from exec-to] (calculate-execution-idx-range coord-spans curr-coord)]
    (doseq [{:keys [idx-from len coord interesting? tab?]} coord-spans]
      (let [executing? (and exec-from exec-to
                            (<= exec-from idx-from (+ idx-from len) exec-to))
            color-classes (cond-> ["code-token"]
                            (and coord (not interesting?))
                            (conj "possible")

                            (and executing? (not tab?))
                            (conj "executing")

                            (and executing? tab?)
                            (conj "executing-dim")

                            interesting?
                            (conj "interesting"))]

        (.add spb color-classes len)))

    (.create spb)))


(defn build-form-paint-and-arm-fn

  "Builds a form-paint-fn function that when called with coorded maps and a curr-coord
  will repaint and arm the form-code-area with the interesting and current coord tokens. "

  [scroll-pane form ^CodeArea form-code-area {:keys [interesting-coord-click-handler uninteresting-coord-click-handler background-click-handler]}]
  (let [print-tokens (binding [pp/*print-right-margin* 80]
                       (-> (form-pprinter/pprint-tokens (:form/form form))
                           ;; if it is a wrapped repl expression discard some tokens that the user
                           ;; isn't interested in
                           maybe-unwrap-runi-tokens))
        code-text (form-pprinter/to-string print-tokens)]
    (.appendText form-code-area code-text)
    (fn [coorded-maps curr-coord]
      (let [interesting-coords (group-by :coord coorded-maps)
            spans (->> print-tokens
                       (map (fn [{:keys [coord] :as tok}]
                              (if (contains? interesting-coords coord)
                                (assoc tok :interesting? true)
                                tok)))
                       form-pprinter/coord-spans)

            curr-idx  (some (fn [{:keys [coord idx-from]}]
                              (when (= coord curr-coord)
                                idx-from))
                            spans)
            style-spans (build-style-spans spans curr-coord)]
        (when curr-idx
          (.moveTo form-code-area curr-idx)
          (.requestFollowCaret form-code-area)

          (let [caret-pos (.getCaretPosition form-code-area)
                caret-pos-2d (.offsetToPosition form-code-area caret-pos TwoDimensional$Bias/Forward)
                caret-line (.getMajor caret-pos-2d)
                area-lines (-> form-code-area .getParagraphs .size)
                caret-area-perc (if (pos? area-lines) (float (/ caret-line area-lines)) 0)]
            (ui-utils/ensure-node-visible-in-scroll-pane scroll-pane form-code-area caret-area-perc)))

        (.setStyleSpans form-code-area 0 0 style-spans)

        (.setOnMouseClicked form-code-area
                            (event-handler
                                [^MouseEvent mev]
                              (let [char-hit (.hit form-code-area (.getX mev) (.getY mev))
                                    opt-char-idx (.getCharacterIndex char-hit)]

                                (if (.isPresent opt-char-idx)
                                  (let [char-idx (.getAsInt opt-char-idx)
                                        clicked-span (->> spans
                                                          (some (fn [{:keys [idx-from len] :as span}]
                                                                  (when (and (>= char-idx idx-from)
                                                                             (< char-idx (+ idx-from len)))
                                                                    span))))]
                                    (when-let [coord (:coord clicked-span)]
                                      (if (:interesting? clicked-span)
                                        (let [clicked-coord-exprs (get interesting-coords coord)]
                                          (when interesting-coord-click-handler
                                            (interesting-coord-click-handler mev clicked-coord-exprs (:line clicked-span))))
                                        (when uninteresting-coord-click-handler
                                          (uninteresting-coord-click-handler mev coord)))))

                                  (when background-click-handler
                                    (background-click-handler mev))))))))))
