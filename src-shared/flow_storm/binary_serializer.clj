;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Important                                  ;;
;;                                            ;;
;; This is currently unused. Just experiments ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns flow-storm.binary-serializer
  (:import [java.io DataOutputStream]))

(defn serialize-init-trace [^DataOutputStream dos [_ {:keys [flow-id form-id form ns timestamp]}]]
  (let [ns-bytes (.getBytes ^String ns "UTF-8")
        ns-cnt (count ns-bytes)
        form-bytes (.getBytes ^String form "UTF-8")
        form-cnt (count form-bytes)]
    (doto dos
      (.writeByte 0) ;; 0 - :init-trace

      (.writeInt  flow-id)
      (.writeInt  form-id)
      (.writeLong timestamp)

      (.writeChar ns-cnt) ;; two bytes for ns length
      (.write ns-bytes 0 ns-cnt)

      (.writeInt form-cnt) ;; 4 bytes for form length
      (.write form-bytes 0 form-cnt))))

(defn serialize-exec-trace [^DataOutputStream dos [_ {:keys [flow-id form-id coor thread-id timestamp result outer-form?]}]]
  (let [result-bytes (.getBytes ^String result "UTF-8")
        result-cnt (count result-bytes)]
    (doto dos
      (.writeByte 1) ;; 1 - :exec-trace

      (.writeInt  flow-id)
      (.writeInt  form-id)
      (.writeLong timestamp)
      (.writeLong thread-id)
      (.writeBoolean (boolean outer-form?))

      (.writeInt result-cnt) ;; 8 bytes for resutl length
      (.write result-bytes 0 result-cnt))

    (.writeByte dos (count coor)) ;; 1 byte for the coor length
    (doseq [c coor]
      ;; 1 byte for each coordinate
      (.writeByte dos c ))))

(defn serialize-fn-call-trace [^DataOutputStream dos [_ {:keys [flow-id form-id thread-id timestamp fn-name fn-ns args-vec]}]]
  (let [fn-name-bytes (.getBytes ^String fn-name "UTF-8")
        fn-name-cnt (count fn-name-bytes)

        ns-bytes (.getBytes ^String fn-ns "UTF-8")
        ns-cnt (count ns-bytes)

        args-bytes (.getBytes ^String args-vec "UTF-8")
        args-cnt (count args-bytes)]
    (doto dos
      (.writeByte 0) ;; 2 - :fn-call-trace

      (.writeInt  flow-id)
      (.writeInt  form-id)
      (.writeLong timestamp)
      (.writeLong thread-id)

      (.writeChar fn-name-cnt) ;; two bytes for fn-name length
      (.write fn-name-bytes 0 fn-name-cnt)

      (.writeChar ns-cnt) ;; two bytes for fn-ns length
      (.write ns-bytes 0 ns-cnt)

      (.writeChar args-cnt) ;; two bytes for args-vec length
      (.write args-bytes 0 args-cnt))))

(defn serialize-bind-trace [^DataOutputStream dos [_ {:keys [flow-id form-id thread-id timestamp symbol value coor]}]]
  (let [symbol-bytes (.getBytes ^String symbol "UTF-8")
        symbol-cnt (count symbol-bytes)

        value-bytes (.getBytes ^String value "UTF-8")
        value-cnt (count value-bytes)]
    (doto dos
      (.writeByte 3) ;; 3 - :bind-trace

      (.writeInt  flow-id)
      (.writeInt  form-id)
      (.writeLong timestamp)
      (.writeLong thread-id)

      (.writeByte symbol-cnt) ;; 1 byte for symbol length
      (.write symbol-bytes 0 symbol-cnt)

      (.writeLong value-cnt) ;; 8 byte for value length
      (.write value-bytes 0 value-cnt))

    (.writeByte dos (count coor)) ;; 1 byte for the coor length
    (doseq [c coor]
      ;; 1 byte for each coordinate
      (.writeByte dos c ))))


(defn serialize-trace [file-dos [ttype :as trace]]
  (case ttype
    :init-trace    (serialize-init-trace file-dos trace)
    :exec-trace    (serialize-exec-trace file-dos trace)
    :fn-call-trace (serialize-fn-call-trace file-dos trace)
    :bind-trace    (serialize-bind-trace file-dos trace)
    (throw (Exception. (str "Trace type not implemented yet " ttype)))))
