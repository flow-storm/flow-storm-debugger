;; Most functions here were copied from the amazing clj-reload by Nikita Prokopov (Tonsky)
;; https://github.com/tonsky/clj-reload
;; MIT License

;; Copyright (c) 2024 Nikita Prokopov

;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:

;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.

;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.

(ns flow-storm.ns-reload-utils
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [flow-storm.utils :refer [log]])
  (:import
    [clojure.lang LineNumberingPushbackReader]
    [java.io File StringReader]
    [java.net URL]))

(defn- file-reader ^LineNumberingPushbackReader [f]
  (LineNumberingPushbackReader.
   (io/reader (io/file f))))

(defn- string-reader ^LineNumberingPushbackReader [^String s]
  (LineNumberingPushbackReader.
   (StringReader. s)))

(defn- throwable? [o]
  (instance? Throwable o))

(defn- update! [m k f & args]
  (assoc! m k (apply f (m k) args)))

(def conjs
  (fnil conj #{}))

(def intos
  (fnil into #{}))

(defn- assoc-some [m & kvs]
  (reduce
    (fn [m [k v]]
      (cond-> m
        (some? v) (assoc k v)))
    m
    (partition 2 kvs)))

(defn- some-set [& vals]
  (not-empty
    (set (filter some? vals))))


(def dummy-resolver
  (reify clojure.lang.LispReader$Resolver
    (currentNS [_]
      'user)
    (resolveClass [_ sym]
      sym)
    (resolveAlias [_ sym]
      sym)
    (resolveVar [_ sym]
      sym)))

(def reader-opts
  {:read-cond :allow
   :features  #{:clj}
   :eof       ::eof})

(defn- read-form [reader]
  (binding [*suppress-read*   true
            *reader-resolver* dummy-resolver]
    (read reader-opts reader)))

(defn- parse-require-form [form]
  (loop [body   (next form)
         result (transient #{})]
    (let [[decl & body'] body]
      (cond
        (empty? body)
        (persistent! result)

        (symbol? decl) ;; a.b.c
        (recur body' (conj! result decl))

        (not (sequential? decl))
        (do
          (log "Unexpected" (first form) "form:" (pr-str decl))
          (recur body' result))

        (not (symbol? (first decl)))
        (do
          (log "Unexpected" (first form) "form:" (pr-str decl))
          (recur body' result))

        (or
          (nil? (second decl))      ;; [a.b.d]
          (keyword? (second decl))) ;; [a.b.e :as e]
        (if (= :as-alias (second decl)) ;; [a.b.e :as-alias e]
          (recur body' result)
          (recur body' (conj! result (first decl))))

        :else ;; [a.b f [g :as g]]
        (let [prefix  (first decl)
              symbols (->> (next decl)
                        (remove #(and (sequential? %) (= :as-alias (second %)))) ;; [a.b [g :as-alias g]]
                        (map #(if (symbol? %) % (first %)))
                        (map #(symbol (str (name prefix) "." (name %)))))]
          (recur body' (reduce conj! result symbols)))))))

(defn- parse-ns-form [form]
  (let [name (second form)]
    (loop [body     (nnext form)
           requires (transient #{})]
      (let [[form & body'] body
            tag (when (list? form)
                  (first form))]
        (cond
          (empty? body)
          [name (not-empty (persistent! requires))]

          (#{:require :use} tag)
          (recur body' (reduce conj! requires (parse-require-form form)))

          :else
          (recur body' requires))))))

(defn- expand-quotes [form]
  (walk/postwalk
    #(if (and (sequential? %) (not (vector? %)) (= 'quote (first %)))
       (second %)
       %)
    form))

(defn- read-file
  "Returns {<symbol> NS} or Exception"
  ([file]
   (with-open [rdr (file-reader file)]
     (read-file rdr file)))
  ([rdr file]
   (try
     (loop [ns   nil
            nses {}]
       (let [form (read-form rdr)
             tag  (when (list? form)
                    (first form))]
         (cond
           (= ::eof form)
           nses

           (= 'ns tag)
           (let [[ns requires] (parse-ns-form form)
                 requires      (disj requires ns)]
             (recur ns (update nses ns assoc-some
                               :meta     (meta ns)
                               :requires requires
                               :ns-files (some-set file))))

           (= 'in-ns tag)
           (let [[_ ns] (expand-quotes form)]
             (recur ns (update nses ns assoc-some
                               :in-ns-files (some-set file))))

           (and (nil? ns) (#{'require 'use} tag))
           (throw (ex-info (str "Unexpected " tag " before ns definition in " file) {:form form}))

           (#{'require 'use} tag)
           (let [requires' (parse-require-form (expand-quotes form))
                 requires' (disj requires' ns)]
             (recur ns (update-in nses [ns :requires] intos requires')))

           (or
            (= 'defonce tag)
            (list? form))
           (let [[_ name] form]
             (recur ns (assoc-in nses [ns :keep name] {:tag  tag
                                                       :form form})))

           :else
           (recur ns nses))))
     (catch Exception e
       (log "Failed to read" (.getPath file) (.getMessage e))
       (ex-info (str "Failed to read" (.getPath file)) {:file file} e)))))

(defn- read-file-or-resource

  [path]

  (try
    (when-let [f-url (or (io/resource path) (some-> (io/file path) (.toURL)))]
      (let [rdr (string-reader (slurp (io/reader f-url)))]
        (read-file rdr f-url)))
    (catch Exception _ (log "Failed to read" path "ignoring...") nil)))

(defn- dependees
  "Inverts the requies graph. Returns {ns -> #{downstream-ns ...}}"
  [namespaces]
  (let [*m (volatile! (transient {}))]
    (doseq [[from {tos :requires}] namespaces]
      (vswap! *m update! from #(or % #{}))
      (doseq [to tos
              :when (namespaces to)]
        (vswap! *m update! to conjs from)))
    (persistent! @*m)))

(declare topo-sort)

(defn- report-cycle [deps all-deps]
  (let [circular (filterv
                   (fn [node]
                     (try
                       (topo-sort (dissoc deps node) (fn [_ _] (throw (ex-info "Part of cycle" {}))))
                       true
                       (catch Exception _
                         false)))
                   (keys deps))]
    (throw (ex-info (str "Cycle detected: " (str/join ", " (sort circular))) {:nodes circular :deps all-deps}))))

(defn- topo-sort
  ([deps]
   (topo-sort deps report-cycle))
  ([all-deps on-cycle]
   (loop [res  (transient [])
          deps all-deps]
     (if (empty? deps)
       (persistent! res)
       (let [ends  (reduce into #{} (vals deps))
             roots (->> (keys deps) (remove ends) (sort))]
         (if (seq roots)
           (recur (reduce conj! res roots) (reduce dissoc deps roots))
           (on-cycle deps all-deps)))))))

(defn- topo-sort-fn
  "Accepts dependees map {ns -> #{downsteram-ns ...}},
   returns a fn that topologically sorts dependencies"
  [deps]
  (let [sorted (topo-sort deps)]
    (fn [coll]
      (filter (set coll) sorted))))

(defn- deep-dependees-set
  "Given a set of some initial namespaces and a dependees map like the one
  calculated by `dependees`, return a set off all trasitively reached namespaces
  including those on the initial set (initial-nss)."
  [initial-nss ns-dependees]
  (->> initial-nss
       (mapcat (fn [ns]
                 (deep-dependees-set (get ns-dependees ns) ns-dependees) ))
       (reduce conj initial-nss)))

(defn- ns-unload [ns]
  (log "Unloading" ns)
  (remove-ns ns)
  (dosync
   (alter @#'clojure.core/*loaded-libs* disj ns)))

(defn- ns-load-file [content ns file-name]
  (let [[_ ext] (re-matches #".*\.([^.]+)" file-name)
        path    (-> ns str (str/replace #"\-" "_") (str/replace #"\." "/") (str "." ext))]
    (Compiler/load (StringReader. content) path file-name)))

(defn- ns-load [ns file-or-url]
  (log "Loading" ns)
  (try
    (ns-load-file (slurp file-or-url) ns (if (instance? java.io.File file-or-url)
                                           (.getName ^File file-or-url)
                                           (.getFile ^URL  file-or-url)))

    nil
    (catch Throwable t
      (log "  failed to load" ns t)
      t)))

(def before-reload-callback nil)
(def after-reload-callback nil)

(defn set-before-reload-callback! [cb]
  (alter-var-root #'before-reload-callback (constantly cb)))

(defn set-after-reload-callback! [cb]
  (alter-var-root #'after-reload-callback (constantly cb)))

(defn reload-all

  "Reload all loaded namespaces that contains at least one var, which matches
  regex, and any other namespaces depending on them."

  [regex]

  (let [;; collect all loaded namespaces resources files paths set
        all-paths (->> (all-ns)
                       (reduce (fn [files ns]
                                 (reduce (fn [files' ns-var]
                                           (if-let [f-path (some-> ns-var meta :file)]
                                             (conj files' f-path)
                                             files'))
                                         files
                                         (vals (ns-interns ns))))
                               #{}))

        ;; build the namespaces map
        namespaces (reduce (fn [nss path]
                             (let [res (read-file-or-resource path)]
                               ;; throwables here are caused for example by reading
                               ;; files which contains "#{`ns 'ns}". This is because
                               ;; of reading with dummy-resolver
                               (if-not (throwable? res)
                                 (merge nss res)
                                 nss)))
                           {}
                           all-paths)
        dependees  (dependees namespaces)
        topo-sort  (topo-sort-fn dependees)
        matched-ns (->> (keys namespaces)
                        (filterv (fn [ns] (re-matches regex (name ns))))
                        (into #{}))
        to-reload   (->> (deep-dependees-set matched-ns dependees)
                         topo-sort)
        to-unload (reverse to-reload)]

    (when before-reload-callback (before-reload-callback))
    (doseq [ns to-unload]
      (ns-unload ns))

    (doseq [ns to-reload]
      (doseq [ns-files (get-in namespaces [ns :ns-files])]
        (ns-load ns ns-files)))

    (when after-reload-callback (after-reload-callback))))

(comment
  (reload-all #"hanse.*")
  )
