(ns dev-core-async
  (:require [clojure.core.async :as async]
            [dorothy.core :as dot]
            [dorothy.jvm :refer (render save! show!)]
            [clojure.string :as str]))

(defn produce [c]
  (async/go-loop [[i & r] (range 10)]
    (if i
      (do
        (async/>! c i)
        (recur r))
      (async/>! c :done))))

(defn consume [c]
  (async/go-loop [i (async/<! c)]
    (if i
      (let [s (str i " thing")
            j (let [h (range 10)
                    w (map inc h)]
                (reduce + w))]
        (println "Got " s)
        (recur (async/<! c)))
      (println "Done"))))

(comment
  (def ch (async/chan 20))
  (produce ch)
  (consume ch)

  )
(comment
  (-> (dot/digraph {:id :G}
                   [(dot/graph-attrs {:compound "true"})
                    (dot/subgraph :cluster0 [{:style :filled, :color :lightgrey, :label "block-1"}
                                             {}
                                             [:a :b :c]])
                    (dot/subgraph :cluster1 [{:style :filled, :color :lightgrey, :label "block-2"}
                                             {}
                                             [:b :d]])
                    [:a {:label "da node"}]
                    [:a :d {:lhead "cluster1"}]

                    ])
      dot/dot
      #_println
      show!))

(defmulti inst-attrs-and-links (fn [i _] (type i)))

(defn create-node [id kind data]
  (if (map? data)
    (let [data-vals (mapv (fn [[k v]] (str (name k) ":" (str v))) data)]
      [(str id) {:label (format "%s \n %s \n\n" kind (str/join "\n" data-vals))
                 :fontsize 10
                 :style :filled
                 :shape :box
                 :fillcolor (case kind
                              :RawCode "#acddde"
                              :Call "#d6cdea"
                              :Return "#d6cdea"
                              :CustomTerminator "#ffe7c7"
                              :CondBr "#fef8dd"
                              "#bbbbbb")}])
    [(str id) {:label (str "NOT-MAP " (type data))
               :fontsize 10}]))

(defmethod inst-attrs-and-links :default
  [{:keys [id] :as data} _]
  [(create-node id :UK {:type (type data)})])

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.Jmp
  [{:keys [id block] :as data} blocks]
  [(create-node id :Jmp data)
   [(str id) (-> (get blocks block) first :id str)]])

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.Const
  [{:keys [id value] :as data} _]
  [(create-node id :Const data)])

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.CustomTerminator
  [{:keys [id f blk] :as data} blocks]
  [(create-node id :CustomTerminator data)
   [(str id) (-> (get blocks blk) first :id str)]])

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.RawCode
  [{:keys [id ast] :as data} _]
  (let [form (-> ast :form)]
    [(create-node id :RawCode (cond-> {:id id
                                       :form form}
                                (meta form) (assoc :form-meta (meta form))))]))

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.CondBr
  [{:keys [id then-block else-block] :as data} blocks]
  [(create-node id :CondBr data)
   [(str id) (-> (get blocks then-block) first :id str)]
   [(str id) (-> (get blocks else-block) first :id str)]])

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.Return
  [{:keys [id] :as data} _]
  [(create-node id :Return data)])

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.Call
  [{:keys [id] :as data} _]
  [(create-node id :Call data)])

(defmethod inst-attrs-and-links clojure.core.async.impl.ioc_macros.Recur
  [{:keys [id recur-nodes] :as data} _]
  (into [(create-node id :Recur data)]
        (mapv (fn [rid] [(str id) (str rid)]) recur-nodes)))

(defn sm->graph [{:keys [blocks start-block]:as sm}]
  (let [subgraphs (reduce-kv
                   (fn [r bid insts]
                     (conj r
                           (dot/subgraph (keyword (format "cluster_%d" bid))
                                         [{:style :filled, :color :lightgrey, :label (format "block-%d" bid)}
                                          {}
                                          (mapv (comp str :id) insts)])))
                   []
                   (:blocks sm))
        nodes-attrs-and-links (reduce-kv
                               (fn [r bid insts]
                                 (into r (mapcat (fn [i] (inst-attrs-and-links i (:blocks sm))) insts)))
                               []
                               blocks)]
    (dot/digraph (-> [(dot/graph-attrs {:compound "true"})]
                     (into subgraphs)
                     (into nodes-attrs-and-links)
                     (into [[:start (-> (get blocks start-block) first :id str)]])))))
#_(tap> sm)

(comment

  (def t
    (-> user/sm
        sm->graph
        dot/dot
        (save! "go-loop.svg" {:format :svg})
        #_show!))
  )
