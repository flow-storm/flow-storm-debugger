(ns flow-storm.debugger.ui.data-windows.visualizers.oscilloscope
  (:require [flow-storm.debugger.ui.components :as ui]
            [flow-storm.runtime.values :refer [sample-chan-1 sample-chan-2 frame-samples frame-samp-rate]]
            [flow-storm.debugger.ui.utils :as ui-utils])
  (:import [javafx.scene.canvas Canvas GraphicsContext]
           [javafx.animation AnimationTimer]
           [javafx.scene.paint Color]
           [javafx.scene.text Font]
           [java.util.concurrent.locks ReentrantLock]))

(set! *warn-on-reflection* true)

(defprotocol VariableSamplesRingP
  (put-sample [_ obj])
  (get-samples [_])
  (reset-limit [_ limit])
  (get-limit [_]))

(deftype VariableSamplesRing [arr
                              ^:unsynchronized-mutable ^:int limit
                              ^:unsynchronized-mutable ^:int write-head
                              ^:unsynchronized-mutable ^:double min-mag
                              ^:unsynchronized-mutable ^:double max-mag
                              lock]
  VariableSamplesRingP

  (put-sample [_ obj]
    (.lock ^ReentrantLock lock)
    (when (>= write-head limit) (set! write-head 0))
    (aset ^objects arr write-head obj)
    (set! min-mag (min min-mag (sample-chan-1 obj) (sample-chan-2 obj)))
    (set! max-mag (max max-mag (sample-chan-1 obj) (sample-chan-2 obj)))
    (set! write-head (inc write-head))
    (.unlock ^ReentrantLock lock))

  (get-samples [_]
    (let [objs-array (object-array limit)]
      (.lock ^ReentrantLock lock)
      (System/arraycopy arr write-head objs-array 0 (- limit write-head))
      (System/arraycopy arr 0 objs-array (- limit write-head) write-head)
      (.unlock ^ReentrantLock lock)
      {:sample-objects (vec objs-array)
       :min-mag min-mag
       :max-mag max-mag}))

  (reset-limit [_ l]
    (.lock ^ReentrantLock lock)
    (set! limit (max (/ (count arr) 1000) (min l (count arr))))
    (set! write-head 0)
    (.unlock ^ReentrantLock lock)
    )

  (get-limit [_] limit))

(defn make-variable-samples-ring [capacity]
  (VariableSamplesRing. (object-array capacity)
                        capacity
                        0
                        Long/MAX_VALUE
                        Long/MIN_VALUE
                        (ReentrantLock.)))


(def amp-per-div
  [0.001 0.002 0.05 0.1 0.2 0.5 1 2 5 10 20 50 100])

(def nanos-per-div
  [5 10 20 50 100 200 500                       ;; nano
   1e3 2e3 5e3 10e3 20e3 50e3 100e3 200e3 500e3 ;; micro
   1e6 2e6 5e6 10e6 20e6 50e6 100e6 200e6 500e6 ;; milli
   1e9 2e9                                      ;; sec
   ])

(def format-nanos
  {5 "5ns", 10 "10ns", 20 "20ns", 50 "50ns", 100 "100ns", 200 "200ns", 500 "500ns",
   1e3 "1us", 2e3 "2us", 5e3 "5us", 10e3 "10us", 20e3 "20us", 50e3 "50us", 100e3 "100us", 200e3 "200us", 500e3 "500us",
   1e6 "1ms", 2e6 "2ms", 5e6 "5ms", 10e6 "10ms", 20e6 "20ms", 50e6 "50ms", 100e6 "100ms", 200e6 "200ms", 500e6 "500ms",
   1e9 "1s", 2e9 "2s"})

(defn needed-window-size [frames-samp-rate nanos-per-div divisions]
  (let [nanos-per-sample (/ 1e9 frames-samp-rate)]
    (* (/ nanos-per-div nanos-per-sample)  divisions)))

(def grid-vert-divs 10)
(def grid-horiz-divs 7)
(defn draw-anim-frame [^GraphicsContext gc margins canvas-width canvas-height
                       samples-ring nanos-per-sample *nanos-per-div-idx *amp-per-div-idx *v-offset]
  (try
    (let [draw-origin margins
          draw-width (- canvas-width (* 2 margins))
          draw-height (- canvas-height (* 2 margins))
          mid-y (+ margins (/ draw-height 2))
          grid-div-height (/ draw-height grid-horiz-divs)


          {:keys [sample-objects]} (get-samples samples-ring)
          grid-div-width (/ draw-width grid-vert-divs)
          samples-per-div (/ (get nanos-per-div @*nanos-per-div-idx)
                             nanos-per-sample)
          samples-objects-cnt (count sample-objects)
          x-step (/ grid-div-width samples-per-div)
          selected-amp-per-div (get amp-per-div @*amp-per-div-idx)
          v-offset @*v-offset]
      (.setFill gc Color/BLUEVIOLET)
      (.fillRect gc 0 0 canvas-width canvas-height)
      (.clearRect  gc draw-origin draw-origin draw-width draw-height)

      ;; grid
      (.setStroke  gc Color/GRAY)
      ;; grid vert lines
      (dotimes [i (inc grid-vert-divs)]
        (let [div-x (* i grid-div-width)]
          (.strokeLine gc (+ margins div-x) draw-origin (+ margins div-x) (+ margins draw-height))))

      ;; grid horiz lines
      (loop [i 0]
        (when (<= i grid-horiz-divs)
          (let [div-y (+ margins (* i grid-div-height))]
            (.strokeLine gc draw-origin div-y (+ margins draw-width) div-y))
          (recur (inc i))))

      ;; draw zero marker
      (let [zero-y (-> (+ v-offset mid-y)
                       (min (+ margins draw-height))
                       (max margins))]
        (.setFill  gc Color/GREENYELLOW)
        (.fillPolygon gc
                      (double-array [margins (- margins 10) (- margins 10)])
                      (double-array [zero-y   (- zero-y 10)   (+ zero-y 10)])
                      3))

      ;; samples
      (let [to (min (dec samples-objects-cnt)
                    (* samples-per-div grid-vert-divs))]
        (loop [i 0
               x margins]
          (when (< i to)
            (let [x-next (+ x x-step)
                  i-next (inc i)
                  samp-obj (get sample-objects i)
                  samp-obj-next (get sample-objects (inc i))]
              (when (and samp-obj samp-obj-next)
                (let [ch-1s      (sample-chan-1 samp-obj)
                      ch-1s-next (sample-chan-1 samp-obj-next)

                      ch-2s      (sample-chan-2 samp-obj)
                      ch-2s-next (sample-chan-2 samp-obj-next)

                      ch-1s-y      (+ v-offset (- mid-y (/ (* grid-div-height ch-1s) selected-amp-per-div)))
                      ch-1s-next-y (+ v-offset (- mid-y (/ (* grid-div-height ch-1s-next) selected-amp-per-div)))

                      ch-2s-y      (+ v-offset (- mid-y (/ (* grid-div-height ch-2s) selected-amp-per-div)))
                      ch-2s-next-y (+ v-offset (- mid-y (/ (* grid-div-height ch-2s-next) selected-amp-per-div)))]
                  (.setStroke  gc Color/BLUE)
                  (.strokeLine ^GraphicsContext gc x ch-1s-y x-next ch-1s-next-y)
                  (.setStroke  gc Color/GREEN)
                  (.strokeLine ^GraphicsContext gc x ch-2s-y x-next ch-2s-next-y)))
              (recur i-next
                     x-next))))))
    (catch Exception e
      (.printStackTrace e))))

(defn oscilloscope-create [data]
  (let [first-frame (:frame data)
        preferred-size (:flow-storm.debugger.ui.data-windows.data-windows/preferred-size data)
        [canvas-width canvas-height margins] (if (= :small preferred-size)
                                               [400 280 25]
                                               [1000 700 25])
        *capturing (atom true)
        *nanos-per-div-idx (atom 17)
        *amp-per-div-idx (atom 6)
        *v-offset (atom 0)
        max-capture-size 12e6  ;; max of 12 million samples
        samples-ring (make-variable-samples-ring max-capture-size)

        canvas (Canvas. canvas-width canvas-height)
        ^GraphicsContext gc (.getGraphicsContext2D canvas)

        _ (.setFont gc (Font. "Arial" 20))
        _ (.setFill gc Color/MAGENTA)
        *curr-samp-rate (atom nil)
        anim-timer (proxy [AnimationTimer] []
                     (handle [^long now]
                       (let [nanos-per-sample (/ 1e9 @*curr-samp-rate)]
                         (draw-anim-frame gc margins canvas-width canvas-height
                                          samples-ring nanos-per-sample *nanos-per-div-idx *amp-per-div-idx *v-offset))))

        samp-rate-lbl (ui/label :text "")
        nanos-per-div-lbl (ui/label :text "")
        capture-samples-lbl (ui/label :text "")
        units-per-vert-div-lbl (ui/label :text "")
        refresh-settings (fn []
                           (let [selected-nanos (get nanos-per-div @*nanos-per-div-idx)
                                 curr-samp-rate @*curr-samp-rate
                                 needed-ws (needed-window-size
                                            curr-samp-rate
                                            selected-nanos
                                            grid-vert-divs)
                                 capture-samples (max (min needed-ws max-capture-size) 0)]

                             (reset-limit samples-ring capture-samples)
                             (ui-utils/set-text samp-rate-lbl (str curr-samp-rate " samps/sec"))
                             (ui-utils/set-text capture-samples-lbl (str "Samples:" capture-samples))
                             (ui-utils/set-text nanos-per-div-lbl (str "H: " (format-nanos selected-nanos)))
                             (ui-utils/set-text units-per-vert-div-lbl (str "V: " (get amp-per-div @*amp-per-div-idx)))))
        add-frame (fn add-frame [frame]
                    (when @*capturing
                      (reset! *curr-samp-rate (frame-samp-rate frame))
                      (let [samples (frame-samples frame)
                            samples-cnt (count samples)]
                        (loop [i 0]
                          (when (< i samples-cnt)
                            (put-sample samples-ring (get samples i))
                            (recur (long (inc i))))))))

        capture-pause-btn (ui/icon-button :icon-name (if @*capturing "mdi-pause" "mdi-record")
                                          :tooltip "Start/Stop capturing"
                                          :classes ["record-btn"])

        _ (ui-utils/set-button-action capture-pause-btn
                                      (fn []
                                        (let [c (swap! *capturing not)]
                                          (ui-utils/update-button-icon
                                           capture-pause-btn
                                           (if c
                                             "mdi-pause"
                                             "mdi-record")))))
        controls (ui/h-box :childs [capture-pause-btn
                                    (ui/button :label "H-"
                                               :on-click (fn []
                                                           (swap! *nanos-per-div-idx (fn [i] (min (inc i) (dec (count nanos-per-div)))))
                                                           (refresh-settings)))
                                    (ui/button :label "H+"
                                               :on-click (fn []
                                                           (swap! *nanos-per-div-idx (fn [i] (max (dec i) 0)))
                                                           (refresh-settings)))
                                    (ui/button :label "V-"
                                               :on-click (fn []
                                                           (swap! *amp-per-div-idx (fn [i] (min (inc i) (dec (count amp-per-div)))))
                                                           (refresh-settings)))
                                    (ui/button :label "V+"
                                               :on-click (fn []
                                                           (swap! *amp-per-div-idx (fn [i] (max (dec i) 0)))
                                                           (refresh-settings)))
                                    (ui/button :label "DOWN"
                                               :on-click (fn [] (swap! *v-offset + 10)))
                                    (ui/button :label "UP"
                                               :on-click (fn [] (swap! *v-offset - 10)))]
                           :align :center
                           :spacing 5)
        settings-labels (ui/h-box :childs [samp-rate-lbl
                                           nanos-per-div-lbl
                                           units-per-vert-div-lbl
                                           capture-samples-lbl]
                                  :align :center
                                  :spacing 5)
        box (ui/border-pane :top controls
                            :center canvas
                            :bottom settings-labels)]
    (add-frame first-frame)
    (refresh-settings)
    (.start anim-timer)
    {:fx/node box
     :add-frame add-frame
     :stop-threads (fn []
                     (.stop anim-timer))}))

(defn oscilloscope-update [_ {:keys [add-frame]} {:keys [new-val]}]
  (add-frame new-val))

(defn oscilloscope-destroy [{:keys [stop-threads]}]
  (stop-threads)
  (println "Scope stopped"))
