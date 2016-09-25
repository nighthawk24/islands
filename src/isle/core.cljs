(ns isle.core
  (:require-macros [isle.macros :refer [spy]])
  (:require [clojure.string :as string]
            [vdom.core :refer [renderer]]
            [vdom.hooks :refer [hook]]
            [isle.math :as m]
            [isle.svg :as s]))

(enable-console-print!)

(defn bounds [node]
  (let [box (.getBBox node)]
    [(.-x box) (.-y box) (.-width box) (.-height box)]))

(defn zoom-to [[x y w h] [x' y' w' h']]
  (let [s (min (/ w' w) (/ h' h))]
    (str (s/translate (+ x' (/ w' 2)) (+ y' (/ h' 2)))
         "scale(" (* s 0.95) ")"
         (s/translate (- (+ x (/ w 2))) (- (+ y (/ h 2)))))))

(defn bounced [f]
  (fn [& args]
    (js/setTimeout #(apply f args) 0)))

(defn ui [emit {:keys [island] :as model}]
  (let [size 600]
    [:main {}
     [:div {}
      [:div {}
       [:button {:onclick #(emit :reset-points)} "New Island"]]
      [:div {}
       [:svg {:width size :height size}
        [:rect {:class "water" :width size :height size}]
        [:path {:class "island"
                :d (as-> island x
                         (map :position x)
                         (s/closed-path x))
                :hookAutoZoom (hook (bounced #(.setAttribute % "transform" (zoom-to (bounds %) [0 0 size size]))))}]]]]]))

(defn loopback [xs]
  (concat xs [(first xs)]))

(defn seed-point []
  {:offset (rand)
   :balance (rand)
   :max-offset (+ 0.05 (rand))})

(defn circle [n radius]
  (for [theta (range 0 m/tau (/ m/tau n))
        :let [{:keys [offset balance] :as seed} (seed-point)]]
    (merge seed
           {:id (gensym "radial")
            :position (let [r (* radius (+ 1 (- offset balance)))]
                        [(* r (m/cos theta))
                         (* r (m/sin theta))])
            :source {:type :radial
                     :center [0 0]
                     :angle theta
                     :radius radius}})))

(defn midpoint [a b]
  (let [offset (rand)
        max-offset (m/avg (map :max-offset [a b]))
        balance (m/avg (map :balance [a b]))]
    {:id (gensym "midpoint")
     :offset offset
     :balance balance
     :max-offset max-offset
     :position (let [[x y :as a] (:position a)
                     [x' y' :as b] (:position b)
                     len (m/dist a b)
                     vlen (* max-offset len (- offset balance))
                     [mx my] [(m/avg [x x']) (m/avg [y y'])]
                     [dx dy] (m/unit-vector [(- (- y y')) (- x x')])]
                 [(+ mx (* vlen dx))
                  (+ my (* vlen dy))])
     :source {:type :midpoint
              :left (:id a)
              :right (:id b)}}))

(defn meander
  ([[left right] max-depth] (meander [left right] max-depth 0))
  ([[left right] max-depth depth]
   (if (and (<= depth max-depth)
            (<= 2 (m/dist (:position left) (:position right))))
     (let [mid (midpoint left right)]
       (concat (meander [left mid] (inc depth))
               (meander [mid right] (inc depth))))
     [left])))

(defn island [max-depth]
  (as-> (rand-int 17) x
    (+ 3 x)
    (circle x 100)
    (loopback x)
    (partition 2 1 x)
    (mapcat #(meander % max-depth) x)))

(defonce model
  (atom {:depth 20}))

(defmulti emit (fn [t & _] t))

(defmethod emit :reset-points [_]
  (swap! model
    (fn [m]
      (assoc m :island (island 15)))))

(defonce render!
  (let [r (renderer (.getElementById js/document "app"))]
    #(r (ui emit @model))))

(defonce on-update
  (add-watch model :rerender
    (fn [_ _ _ model]
      (render! model))))

(render! @model)
