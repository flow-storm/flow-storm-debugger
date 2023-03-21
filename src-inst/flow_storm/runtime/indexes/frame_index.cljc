(ns flow-storm.runtime.indexes.frame-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils :refer [make-mutable-stack ms-peek ms-push ms-pop
                                                                      make-mutable-list ml-get ml-add ml-count]]))

(deftype CallStackFrame [fn-ns
                         fn-name
                         args-vec
                         frame-idx
                         form-id
                         bindings
                         expr-executions]
  
  index-protos/CallStackFrameP

  (get-immutable-frame [_]

    (let [frame-ret (when-let [last-expr (last expr-executions)]
                      (when (:outer-form? last-expr)
                        (:result last-expr)))
          root-frame? (and (nil? fn-ns) (nil? fn-name) (nil? form-id))]
      (cond-> {:fn-ns fn-ns
               :fn-name fn-name
               :args-vec args-vec
               :bindings (into [] bindings) ;; return a immutable seq
               :expr-executions (into [] expr-executions) ;; return a immutable seq
               :form-id form-id
               :frame-idx frame-idx}
        frame-ret (assoc :ret frame-ret)
        root-frame? (assoc :root? true))))

  (get-expr-exec [_ idx]
    (ml-get expr-executions idx))

  (add-binding-to-frame [_ b]
    (ml-add bindings b))

  (add-expr-exec-to-frame [_ idx expr]
    (ml-add expr-executions (assoc expr :idx idx))
    (dec (ml-count expr-executions)))

  #?@(:clj
      [java.lang.Object
       (toString [_]
                 (format "(%s/%s %s) idx: %d, form-id: %s, bindings-cnt: %d, expr-executions-cnt:%d"
                         fn-ns fn-name (pr-str args-vec) frame-idx form-id (ml-count bindings) (ml-count expr-executions)))]))

(deftype TreeNode [^CallStackFrame frame
                   childs]
  
  index-protos/TreeNodeP

  (get-frame [_] frame)
  (get-node-immutable-frame [_] (index-protos/get-immutable-frame frame))
  (has-childs? [_] (zero? (ml-count childs)))
  (add-child [_ node]
    (locking childs
      (ml-add childs node)))
  (get-childs [_]
    (locking childs
      (doall (seq childs)))))

(deftype MutableFrameIndex [root-node
                            build-stack
                            timeline]

  index-protos/BuildIndexP

  (add-form-init [_ _]) ; don't do anything for form-init 
  
  (add-fn-call [this {:keys [form-id fn-ns fn-name args-vec]}]
    (locking this
      (let [exec-exprs (make-mutable-list)
            bindings (make-mutable-list)
            frame-idx (ml-count timeline)
            new-frame (->CallStackFrame fn-ns
                                        fn-name
                                        args-vec
                                        frame-idx
                                        form-id
                                        exec-exprs
                                        bindings)
            node-childs (make-mutable-list)
            new-node (->TreeNode new-frame node-childs)
            curr-node (ms-peek build-stack)]
        (index-protos/add-child curr-node new-node)
        (ml-add timeline {:timeline/type :frame :frame new-frame})
        (ms-push build-stack new-node))))
  
  (add-expr-exec [this {:keys [outer-form?] :as exec-trace}]
    (locking this
      (let [curr-node (ms-peek build-stack)
            curr-frame (index-protos/get-frame curr-node)
            curr-idx (ml-count timeline)
            frame-exec-idx (index-protos/add-expr-exec-to-frame curr-frame curr-idx exec-trace)]
        (ml-add timeline {:timeline/type :expr
                          :frame curr-frame
                          :frame-exec-idx frame-exec-idx
                          :outer-form? outer-form?})
        (when outer-form? (ms-pop build-stack)))))
  
  (add-bind [this bind-trace]
    (locking this
      (let [curr-node (ms-peek build-stack)
            curr-frame (index-protos/get-frame curr-node)]
        (index-protos/add-binding-to-frame curr-frame bind-trace))))

  index-protos/FrameIndexP
  
  (timeline-count [this]
    (locking this
      (ml-count timeline)))
  
  (timeline-entry [this idx]
    (locking this
      (let [tl-entry (ml-get timeline idx)]
        (if (= :expr (:timeline/type tl-entry))

          (let [expr (index-protos/get-expr-exec (:frame tl-entry) (:frame-exec-idx tl-entry))]
            {:timeline/type :expr
             :idx (:idx expr)
             :form-id (:form-id expr)
             :coor (:coor expr)
             :result (:result expr)
             :outer-form? (:outer-form? expr)
             :timestamp (:timestamp expr)})          

          (merge tl-entry
                 (index-protos/get-immutable-frame (:frame tl-entry)))))))

  (timeline-frame-seq [this]
    (locking this
      (->> timeline
           (keep (fn [tl-entry]
                   (when-not (= :expr (:timeline/type tl-entry))
                     (index-protos/get-immutable-frame (:frame tl-entry)))))
           doall)))

  (timeline-seq [this]
    (locking this
      (doall (seq timeline))))

  (frame-data [this idx]
    (locking this
      (let [{:keys [frame]} (ml-get timeline idx)]
        (index-protos/get-immutable-frame frame))))

  (callstack-tree-root-node [this]
    (locking this
      root-node))
  )

(defn make-index []
  (let [root-frame (->CallStackFrame nil nil nil nil nil (make-mutable-list) (make-mutable-list))
        root-node (->TreeNode root-frame (make-mutable-list))
        build-stack (make-mutable-stack)
        timeline (make-mutable-list)]
    (ms-push build-stack root-node)
    (->MutableFrameIndex root-node
                         build-stack
                         timeline)))


