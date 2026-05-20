(ns flow-storm.runtime.logs.utils)

(defn class-exists? [s]
  (try (Class/forName s) true (catch Throwable _ false)))

(defn resource [name]
  (some-> (Thread/currentThread)
          .getContextClassLoader
          (.getResource name)
          str))

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
  (let [cls (Class/forName class-name)]
    (.invoke (find-method (Class/forName class-name) method-name args)
             nil
             (to-array args))))

(defn call [obj method-name args]
  (.invoke (find-method (class obj) method-name args)
           obj
           (to-array args)))

(defn class-origin [class-name]
  (try
    (some-> (Class/forName class-name)
            .getProtectionDomain
            .getCodeSource
            .getLocation
            str)
    (catch Throwable _ nil)))

(defn install-artifact-latest [art-symbol]
  (try
    (let [add-lib (requiring-resolve 'clojure.repl.deps/add-lib)]
      (add-lib art-symbol))
    (catch Exception e
      (throw (ex-info "clojure.repl.deps/add-lib not found. Need tools-deps on the classpath to install dependecies" {:ex e})))))
