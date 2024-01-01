(ns flow-storm.utils
  #?(:cljs (:require [goog.string :as gstr]
                     [clojure.string :as str]
                     [goog.string.format]
                     [goog :as g])
     :clj (:require [clojure.java.io :as io]
                    [clojure.string :as str]))
  (:refer-clojure :exclude [format update-values update-keys])
  #?(:clj (:import [java.io File LineNumberReader InputStreamReader PushbackReader]
                   [clojure.lang RT IEditableCollection PersistentArrayMap PersistentHashMap])))

(defn disable-from-profile [profile]
  (case profile
    :light #{:expr-exec :bind}
    #{}))

(defn elide-string [s max-len]
  (let [len (count s)]
    (when (pos? len)
      (cond-> (subs s 0 (min max-len len))
        (> len max-len) (str " ... ")))))

#?(:clj
   (defn hash-map? [x]
     (or (instance? PersistentArrayMap x)
         (instance? PersistentHashMap x))))

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

(defn parse-int [s]
  #?(:clj (Integer/parseInt s)
     :cljs (js/parseInt s)))

(defn str-coord->vec [str-coord]
  (if (str/blank? str-coord)
    []
    (->> (str/split str-coord #",")
         (mapv (fn [x]
                 (if (or (str/starts-with? x "K")
                         (str/starts-with? x "V"))
                   x
                   (parse-int x)))))))

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
     ([msg ^Exception e]
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

(defn get-current-thread-name []
  #?(:clj (.getName (Thread/currentThread))
     :cljs "main"))

(defn get-thread-object-by-id [thread-id]
  #?(:clj (some #(when (= (.getId %) thread-id) %) (.keySet (Thread/getAllStackTraces)))
     :cljs thread-id))

(defn get-memory-info []
  #?(:clj  {:max-heap-bytes (.maxMemory (Runtime/getRuntime))
            :heap-size-bytes (.totalMemory (Runtime/getRuntime))
            :heap-free-bytes (.freeMemory (Runtime/getRuntime))}
     :cljs {:max-heap-bytes 0
            :heap-size-bytes 0
            :heap-free-bytes 0}))

(defn storm-env? []
  #?(:clj (try
            (Class/forName "clojure.storm.Tracer")
            true
            (catch Exception _ false))
     :cljs (boolean (try (js* "cljs.storm.tracer.trace_fn_call") (catch js/Error _ false)))))

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

#?(:clj
   (defn blocking-derefable? [x]
     (instance? clojure.lang.IBlockingDeref x))
   :cljs (defn blocking-derefable? [_]
           false))

(defn pending? [x]
  #?(:clj  (instance? clojure.lang.IPending x)
     :cljs (instance? cljs.core.IPending x)))

#?(:clj
   (defn source-fn [x]    
     (try
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
                   (str text))
                 )))))
       (catch Exception _ nil))))

#?(:clj
 (defmacro deftype+
   "Same as deftype, but: read mutable fields through ILookup: (:field instance)"
   [name fields & body]
   `(do
      (deftype ~name ~fields
        
        clojure.lang.ILookup
        (valAt [_# key# notFound#]
          (case key#
            ~@(mapcat #(vector (keyword %) %) fields)
            notFound#))
        (valAt [this# key#]
          (.valAt this# key# nil)))
      
      (defn ~(symbol (str '-> name)) ~fields
        (new ~name ~@fields)
        ~@body)))
 )

#?(:clj
   (defmacro lazy-binding
     "Like clojure.core/binding but instead of a vec of vars it accepts a vec
  of symbols, and will resolve the vars with requiring-resolve"
     [bindings & body]     
     (let [vars-binds (mapcat (fn [[var-symb var-val]]
                                [`(clojure.core/requiring-resolve '~var-symb) var-val])
                              (partition 2 bindings))]
       `(let []
          (push-thread-bindings (hash-map ~@vars-binds))
         (try
           ~@body
           (finally
             (pop-thread-bindings)))))))

#?(:clj
   (defn mk-tmp-dir!
     "Creates a unique temporary directory on the filesystem. Typically in /tmp on
  *NIX systems. Returns a File object pointing to the new directory. Raises an
  exception if the directory couldn't be created after 10000 tries."
     []
     (let [base-dir     (io/file (System/getProperty "java.io.tmpdir"))
           base-name    (str (System/currentTimeMillis) "-" (long (rand 1000000000)) "-")
           tmp-base     (doto (File. (str base-dir "/" base-name)) (.mkdir))
           max-attempts 10000]
       (loop [num-attempts 1]
         (if (= num-attempts max-attempts)
           (throw (Exception. (str "Failed to create temporary directory after " max-attempts " attempts.")))
           (let [tmp-dir-name (str tmp-base num-attempts)
                 tmp-dir      (io/file tmp-dir-name)]
             (if (.mkdir tmp-dir)
               tmp-dir
               (recur (inc num-attempts)))))))))

;; So we don't depend on clojure 1.11
(defn update-values
  "m f => {k (f v) ...}

  Given a map m and a function f of 1-argument, returns a new map where the keys of m
  are mapped to result of applying f to the corresponding values of m."
  
  [m f]
  (with-meta
    (persistent!
     (reduce-kv (fn [acc k v] (assoc! acc k (f v)))
                (if (instance? IEditableCollection m)
                  (transient m)
                  (transient {}))
                m))
    (meta m)))

(defn update-keys
  "m f => {(f k) v ...}

  Given a map m and a function f of 1-argument, returns a new map whose
  keys are the result of applying f to the keys of m, mapped to the
  corresponding values of m.
  f must return a unique key for each key of m, else the behavior is undefined."
  {:added "1.11"}
  [m f]
  (let [ret (persistent!
             (reduce-kv (fn [acc k v] (assoc! acc (f k) v))
                        (transient {})
                        m))]
    (with-meta ret (meta m))))

#?(:clj
   (defn normalize-newlines [s]
     (-> s
         (.replaceAll "\\r\\n" "\n")
         (.replaceAll "\\r" "\n"))))

#?(:clj
   (defmacro env-prop [prop-name]
     (System/getProperty prop-name)))

(defn parse-thread-fn-call-limits [s]
  (when s
    (->> (str/split s #",")
         (mapv (fn [fn-desc]
                 (let [[fqfn cnt] (str/split fn-desc #":")
                       [fn-ns fn-name] (str/split fqfn #"/")]
                   [fn-ns fn-name (parse-int cnt)]))))))

(defn stringify-coord [coord-vec]
  (str/join "," coord-vec))

(defn lerp [from to t]
  (+ from (* t (- to from))))

(defn inverse-lerp [from to n]
  (float
   (/ (- n from)
      (- to from))))
