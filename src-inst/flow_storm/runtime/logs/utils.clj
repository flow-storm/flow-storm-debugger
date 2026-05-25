(ns flow-storm.runtime.logs.utils
  (:require [clojure.string :as str]))

(defn get-class [class-name]
  (Class/forName class-name true (.getContextClassLoader (Thread/currentThread))))

(defn class-exists? [s]
  (try (get-class s) true (catch Throwable _ false)))

(defn class-origin [class-name]
  (some-> (get-class class-name)
          .getProtectionDomain
          .getCodeSource
          .getLocation
          str))

(defn resource [name]
  (some-> (Thread/currentThread)
          .getContextClassLoader
          (.getResource name)
          str))

(defn any-class-exists? [& classes]
  (some class-exists? classes))

(defn origin-version [class-name group-path artifact]
  (when-let [vers-str (some->> (class-origin class-name)
                               (re-find (re-pattern (str ".+?/" group-path "/" artifact "/(.+?)/.*")))
                               second)]
    {:major (-> vers-str (str/split #"\.") first)
     :version-str vers-str}))

(defn class-origin-matches? [class-name pattern]
  (some-> (class-origin class-name)
          (re-find pattern)
          boolean))

(defn find-method [clazz method-name args]
  (let [methods (filter #(and (= method-name (.getName %))
                              (= (count args) (count (.getParameterTypes %))))
                        (.getMethods clazz))
        m (if (= (count methods) 1)
            (first methods)
            (if-let [args-types (-> args meta :args-types)]
              (some (fn [mm]
                      (when (= args-types (mapv #(.getName %) (.getParameterTypes mm)))
                        mm))
                    methods)
              (throw (ex-info "More than one method for that arity count, provide :args-types meta on the args vector, like ^{:args-types [\"java.lang.String\"]} [\"hello\"]" {:methods methods}))))]
    (if-not m
      (throw (ex-info "No matching static method"
                      {:class clazz :method method-name :argc (count args)}))
      m)))

(defn call-static [class-name method-name args]
  (.invoke (find-method (get-class class-name) method-name args)
           nil
           (to-array args)))

(defn call [obj method-name args]
  (.invoke (find-method (class obj) method-name args)
           obj
           (to-array args)))

(defn install-artifact-release [art-symbol]
  (try
    (let [add-lib (requiring-resolve 'clojure.repl.deps/add-lib)]
      (add-lib art-symbol {:mvn/version "RELEASE"}))
    (catch Exception e
      (throw (ex-info "clojure.repl.deps/add-lib not found. Need tools-deps on the classpath to install dependecies" {:ex e})))))
