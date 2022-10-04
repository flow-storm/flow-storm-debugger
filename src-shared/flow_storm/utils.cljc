(ns flow-storm.utils
  #?(:cljs (:require [goog.string :as gstr]
                     [goog.string.format]
                     [goog :as g])
     :clj (:require [clojure.java.io :as io]))
  #?(:clj (:refer-clojure :exclude [format]))
  #?(:clj (:import [java.io LineNumberReader InputStreamReader PushbackReader]
                   [clojure.lang RT Reflector])))

(defn disable-from-profile [profile]
  (case profile
    :full  #{}
    :light #{:expr :binding}
    {}))

(defn elide-string [s max-len]
  (let [len (count s)]
    (when (pos? len)
      (cond-> (subs s 0 (min max-len len))
        (> len max-len) (str " ... ")))))

(defn format [& args]
  #?(:clj (apply clojure.core/format args)
     :cljs (apply gstr/format args)))

#?(:clj
   (defn colored-string [s c]
     (let [color {:red 31
                  :yellow 33}]
       (format "\033[%d;1;1m%s\033[0m" (color c) s)))

   :cljs
   (defn colored-string [_ _]
     "UNIMPLEMENTED"))

#?(:clj (defn map-like? [x] (instance? java.util.Map x)))
#?(:cljs (defn map-like? [x] (map? x)))

#?(:clj (defn seq-like? [x] (instance? java.util.List x)))
#?(:cljs (defn seq-like? [_] false))

#?(:cljs (def uuids (atom {:max-uuid 3 :strings-and-numbers {}})))

;; copying goog.getUid https://github.com/google/closure-library/blob/master/closure/goog/base.js#L1306
#?(:cljs (def flow-storm-uuid-prop (str "flow_storm_" (unsigned-bit-shift-right (* (js/Math.random) 1e9) 0))))

#?(:clj (defn object-id [o] (System/identityHashCode o))

   :cljs (defn object-id [o]
           (cond
             (or (undefined? o) (nil? o))
             0

             (boolean? o)
             (if (true? o) 1 2)

             (or (number? o) (string? o))
             (let [uuids' (swap! uuids (fn [{:keys [max-uuid strings-and-numbers] :as u}]
                                         (if (get strings-and-numbers o)
                                           u
                                           (let [next-uuid (inc max-uuid)]
                                             (-> u
                                                 (assoc :max-uuid next-uuid)
                                                 (update :strings-and-numbers assoc o next-uuid))))))]
               (get-in uuids' [:strings-and-numbers o]))

             (= "object" (g/typeOf o))
             (or (and (js/Object.prototype.hasOwnProperty.call o flow-storm-uuid-prop)
                      (aget o flow-storm-uuid-prop))
                 (let [next-uid (-> (swap! uuids update :max-uuid inc)
                                    :max-uuid)]
                   (aset o flow-storm-uuid-prop next-uid))))))

#?(:clj (def out-print-writer *out*))

#?(:clj
   (defn log [& msgs]
     (binding [*out* out-print-writer]
       (apply println msgs)))
   :cljs
   (defn log [& msgs]
     (apply js/console.log msgs)))

#?(:clj
   (defn log-error
     ([msg] (binding [*out* *err*]
              (println msg)))
     ([msg e]
      (binding [*out* *err*]
        (println msg)
        (.printStackTrace e))))
   :cljs
   (defn log-error
     ([msg] (js/console.error msg))
     ([msg e]
      (js/console.error msg e))))

(defn rnd-uuid []
  #?(:clj (java.util.UUID/randomUUID)
     :cljs (.-uuid ^cljs.core/UUID (random-uuid))))

(defn get-timestamp []
  #?(:cljs (.now js/Date)
     :clj (System/currentTimeMillis)))

(defn get-monotonic-timestamp []
  #?(:cljs (.now js/Date)
     :clj (System/nanoTime)))

(defn get-current-thread-id []
  #?(:clj (.getId (Thread/currentThread))
     :cljs 0))

(defn contains-only? [m ks]
  (empty? (apply dissoc m ks)))

(defn merge-meta

  "Non-throwing version of (vary-meta obj merge metamap-1 metamap-2 ...).
  Like `vary-meta`, this only applies to immutable objects. For
  instance, this function does nothing on atoms, because the metadata
  of an `atom` is part of the atom itself and can only be changed
  destructively."

  {:style/indent 1}
  [obj & metamaps]
  (try
    (apply vary-meta obj merge metamaps)
    #?(:clj (catch Exception _ obj)
       :cljs (catch js/Error _ obj))))

(defn derefable? [x]
  #?(:clj  (instance? clojure.lang.IDeref x)
     :cljs (instance? cljs.core.IDeref x)))

(defn walk-indexed
  "Walk through form calling (f coor element).
  The value of coor is a vector of indices representing element's
  address in the form. Unlike `clojure.walk/walk`, all metadata of
  objects in the form is preserved."
  ([f form] (walk-indexed [] f form))
  ([coor f form]
   (let [map-inner (fn [forms]
                     (map-indexed #(walk-indexed (conj coor %1) f %2)
                                  forms))
         ;; Clojure uses array-maps up to some map size (8 currently).
         ;; So for small maps we take advantage of that, otherwise fall
         ;; back to the heuristic below.
         ;; Maps are unordered, but we can try to use the keys as order
         ;; hoping they can be compared one by one and that the user
         ;; has specified them in that order. If that fails we don't
         ;; instrument the map. We also don't instrument sets.
         ;; This depends on Clojure implementation details.
         walk-indexed-map (fn [map]
                            (map-indexed (fn [i [k v]]
                                           [(walk-indexed (conj coor (* 2 i)) f k)
                                            (walk-indexed (conj coor (inc (* 2 i))) f v)])
                                         map))
         result (cond
                  (map? form) (if (<= (count form) 8)
                                (into {} (walk-indexed-map form))
                                (try
                                  (into (sorted-map) (walk-indexed-map (into (sorted-map) form)))
                                  #?(:clj (catch Exception _ form)
                                     :cljs (catch js/Error _ form))))
                  ;; Order of sets is unpredictable, unfortunately.
                  (set? form)  form
                  ;; Borrowed from clojure.walk/walk
                  (list? form) (apply list (map-inner form))
                  (map-entry? form) (vec (map-inner form))
                  (seq? form)  (doall (map-inner form))
                  (coll? form) (into (empty form) (map-inner form))
                  :else form)]
     (f coor (merge-meta result (meta form))))))

(defn tag-form-recursively
  "Recursively add coordinates to all forms"
  [form key]
  ;; Don't use `postwalk` because it destroys previous metadata.
  (walk-indexed (fn [coor frm]
                  (merge-meta frm {key coor}))
                form))


#?(:clj
   (defn source-fn [x]
     (when-let [v (resolve x)]
       (when-let [filepath (:file (meta v))]
         (let [strm (or (.getResourceAsStream (RT/baseLoader) filepath)
                        (io/input-stream filepath))]
           (when str
             (with-open [rdr (LineNumberReader. (InputStreamReader. strm))]
               (dotimes [_ (dec (:line (meta v)))] (.readLine rdr))
               (let [text (StringBuilder.)
                     pbr (proxy [PushbackReader] [rdr]
                           (read [] (let [i (proxy-super read)]
                                      (.append text (char i))
                                      i)))
                     read-opts (if (.endsWith ^String filepath "cljc") {:read-cond :allow} {})]
                 (if (= :unknown *read-eval*)
                   (throw (IllegalStateException. "Unable to read source while *read-eval* is :unknown."))
                   (read read-opts (PushbackReader. pbr)))
                 (str text)))))))))

