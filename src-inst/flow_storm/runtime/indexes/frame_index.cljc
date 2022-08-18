(ns flow-storm.runtime.indexes.frame-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]
            [flow-storm.runtime.indexes.utils :as index-utils :refer [make-mutable-stack ms-peek ms-push ms-pop
                                                                      make-mutable-list ml-get ml-add ml-count]]))

(defprotocol CallStackFrameP
  (get-immutable-frame [_])
  (get-expr-exec [_ idx])
  (add-binding-to-frame [_ bind-trace])
  (add-expr-exec-to-frame [_ idx exec-trace]))

(deftype CallStackFrame [fn-ns
                         fn-name
                         args-vec
                         frame-idx
                         form-id
                         bindings
                         expr-executions]
  CallStackFrameP

  (get-immutable-frame [_]

    (let [frame-ret (when-let [last-expr (last expr-executions)]
                      (when (:outer-form? last-expr)
                        (:result last-expr)))]
      (cond-> {:fn-ns fn-ns
               :fn-name fn-name
               :args-vec args-vec
               :bindings (seq bindings) ;; return a immutable seq
               :expr-executions (seq expr-executions) ;; return a immutable seq
               :form-id form-id
               :frame-idx frame-idx}
        frame-ret (assoc :ret frame-ret))))

  (get-expr-exec [_ idx]
    (ml-get expr-executions idx))

  (add-binding-to-frame [_ b]
    (ml-add bindings b))

  (add-expr-exec-to-frame [this idx expr]
    (ml-add expr-executions (assoc expr :idx idx))
    (dec (ml-count expr-executions)))

  #?@(:clj
      [java.lang.Object
       (toString [_]
                 (format "(%s/%s %s) idx: %d, form-id: %s, bindings-cnt: %d, expr-executions-cnt:%d"
                         fn-ns fn-name (pr-str args-vec) frame-idx form-id (ml-count bindings) (ml-count expr-executions)))]))

(defprotocol TreeNodeP
  (get-frame [_])
  (get-node-immutable-frame [_])
  (has-childs? [_])
  (add-child [_ node])
  (get-childs [_]))

(deftype TreeNode [^CallStackFrame frame
                   childs]
  TreeNodeP

  (get-frame [_] frame)
  (get-node-immutable-frame [_] (get-immutable-frame frame))
  (has-childs? [_] (zero? (ml-count childs)))
  (add-child [_ node] (ml-add childs node))
  (get-childs [_] childs))

(defprotocol FrameIndexP
  (timeline-count [_])
  (timeline-entry [_ idx])
  (timeline-frame-seq [_])
  (timeline-seq [_])
  (frame-data [_ idx])
  
  (callstack-tree-root-node [_]))

(deftype MutableFrameIndex [root-node
                            build-stack
                            timeline]

  index-protos/BuildIndexP

  (add-form-init [_ _]) ; don't do anything for form-init 
  
  (add-fn-call [this {:keys [form-id fn-ns fn-name args-vec]}]
    (locking this
      (let [exec-exprs (make-mutable-list)
            bindings (make-mutable-list)
            new-frame (->CallStackFrame fn-ns
                                        fn-name
                                        args-vec
                                        (ml-count timeline)
                                        form-id
                                        exec-exprs
                                        bindings)
            node-childs (make-mutable-list)
            new-node (->TreeNode new-frame node-childs)
            curr-node (ms-peek build-stack)]
        (add-child curr-node new-node)
        (ml-add timeline {:timeline/type :frame :frame new-frame})
        (ms-push build-stack new-node))))
  
  (add-expr-exec [this {:keys [outer-form?] :as exec-trace}]
    (locking this
      (let [curr-node (ms-peek build-stack)
            curr-frame (get-frame curr-node)
            curr-idx (ml-count timeline)
            frame-exec-idx (add-expr-exec-to-frame curr-frame curr-idx exec-trace)]
        (ml-add timeline {:timeline/type :expr
                          :frame curr-frame
                          :frame-exec-idx frame-exec-idx
                          :outer-form? outer-form?})
        (when outer-form? (ms-pop build-stack)))))
  
  (add-bind [this bind-trace]
    (locking this
      (let [curr-node (ms-peek build-stack)
            curr-frame (get-frame curr-node)]
        (add-binding-to-frame curr-frame bind-trace))))

  FrameIndexP
  
  (timeline-count [this]
    (locking this
      (ml-count timeline)))
  
  (timeline-entry [this idx]
    (locking this
      (let [tl-entry (ml-get timeline idx)]
        (if (= :expr (:timeline/type tl-entry))

          (let [expr (get-expr-exec (:frame tl-entry) (:frame-exec-idx tl-entry))]
            {:timeline/type :expr
             :idx (:idx expr)
             :form-id (:form-id expr)
             :coor (:coor expr)
             :result (:result expr)
             :outer-form? (:outer-form? expr)
             :timestamp (:timestamp expr)})          

          (merge tl-entry
                 (get-immutable-frame (:frame tl-entry)))))))

  (timeline-frame-seq [this]
    (->> timeline
         (keep (fn [tl-entry]
                 (when-not (= :expr (:timeline/type tl-entry))
                   (get-immutable-frame (:frame tl-entry)))))))

  (timeline-seq [_]
    (seq timeline))

  (frame-data [this idx]
    (locking this
      (let [{:keys [frame]} (ml-get timeline idx)]
        (get-immutable-frame frame))))

  (callstack-tree-root-node [_]
    root-node)
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


