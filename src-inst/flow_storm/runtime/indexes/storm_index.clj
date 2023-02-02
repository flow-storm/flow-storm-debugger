(ns flow-storm.runtime.indexes.storm-index
  (:require [flow-storm.runtime.indexes.protocols :as index-protos]))

(deftype WrappedCallStackFrame [#_^clojure.storm.CallStackFrame frame]

  index-protos/CallStackFrameP

  (get-immutable-frame [_]

    )

  (get-expr-exec [_ idx]
    )

  )

(deftype WrappedTreeNode [#_^clojure.storm.CallTreeNode node]

  index-protos/TreeNodeP

  (get-frame [_]
    )

  (get-node-immutable-frame [_]
    )
  (has-childs? [_]
    )
  (get-childs [_]
    ))

(deftype StormIndex []

  index-protos/FrameIndexP
  (timeline-count [_])
  (timeline-entry [_ idx])
  (timeline-frame-seq [_])
  (timeline-seq [_])
  (frame-data [_ idx])

  (callstack-tree-root-node [_])

  index-protos/FnCallStatsP
  (all-stats [_])

  index-protos/ThreadRegistryP
  (all-threads [_])
  (get-thread-indexes [_ flow-id thread-id])
  (flow-exists? [_ flow-id])

  index-protos/FormRegistryP
  (all-forms [_])
  (get-form [_ form-id]))

(defn make-index []
  (->StormIndex))
