(ns flow-storm.runtime.logs.utils)

(defn class-exists? [s]
  (try (Class/forName s) true (catch Throwable _ false)))

(defn resource [name]
  (some-> (Thread/currentThread)
          .getContextClassLoader
          (.getResource name)
          str))

(defn call-static [class-name method-name & args]
  (let [cls (Class/forName class-name)
        methods (filter #(= method-name (.getName %)) (.getMethods cls))
        m (first (filter #(= (count args) (count (.getParameterTypes %))) methods))]
    (when-not m
      (throw (ex-info "No matching static method"
                      {:class class-name :method method-name :argc (count args)})))
    (.invoke m nil (to-array args))))

(defn call [obj method-name & args]
  (let [methods (filter #(= method-name (.getName %)) (.getMethods (class obj)))
        m (first (filter #(= (count args) (count (.getParameterTypes %))) methods))]
    (when-not m
      (throw (ex-info "No matching method"
                      {:class (class obj) :method method-name :argc (count args)})))
    (.invoke m obj (to-array args))))
