[![Clojars Project](http://clojars.org/om-datascript/latest-version.svg)](http://clojars.org/om-datascript)

Sample usage

```clojure
(ns om-datascript.demo
  (:require [datascript :as d]
            [om-datascript.core :as od]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]))

(def schema {})
(def conn (d/create-conn schema))

(def app (atom {}))

(d/transact! conn [{:db/id 1
                    :title "Hello world!"}
                   {:db/id 2
                    :count 0}])

(defn get-conn [owner]
  (om/get-shared owner :conn))

(defn increment [e owner count]
  (d/transact! (get-conn owner)
               [[:db/add 2 :count (inc count)]
                {:db/id -1
                 :order (rand)
                 :type :message
                 :clicked false
                 :msg (str "Clicked with count of " count)}]))

(defn update-msg [e owner eid]
  (d/transact! (get-conn owner)
               [{:db/id eid :msg "I got clicked" :clicked true}]))

(defn remove-msg [e owner eid]
  (d/transact! (get-conn owner)
               [[:db.fn/retractEntity eid]]))

(def msg-projections
  {:msg ['[:find ?text ?clicked
            :in $ ?m
            :where
           [?m :msg ?text]
           [?m :clicked ?clicked]]
          [:$id]
          first]})

(defn msg [[id] owner]
  (let [conn (get-conn owner)
        cursor (od/owner-cursor owner [::msg id])]
    (reify
      om/IWillMount
      (will-mount [_]
        (od/project-onto conn cursor msg-projections {:$id id}))
      om/IWillUnmount
      (will-unmount [_]
        (od/unproject-from conn cursor msg-projections))
      om/IRenderState
      (render-state [_ state]
        (let [[text clicked] (:msg state)]
          (dom/li nil
                  (dom/span nil (str id " - " text " "))
                  (if clicked
                    (dom/button {:onClick #(remove-msg % owner id)} "--")
                    (dom/button {:onClick #(update-msg % owner id)} "<<"))))))))

(def app-projections
  {:info {:title ['[:find ?title
                    :where
                    [?e :title ?title]]
                  ffirst]
          :count ['[:find ?count
                    :where
                    [?e :count ?count]]
                  ffirst]}
   :messages ['[:find ?m ?order
                    :where
                    [?m :type :message]
                    [?m :order ?order]]
              (partial sort-by last)]})

(defn app-view [app owner]
  (let [conn (get-conn owner)]
    (reify
      om/IWillMount
      (will-mount [_]
        (od/project-onto conn app app-projections))
      om/IWillUnmount
      (will-unmount [_]
        (od/unproject-from conn app app-projections))
      om/IRender
      (render [_]
        (let [count (first (get-in app [:info :count]))]
          (when (= 10 count)
            (od/unproject conn app [:info :count]))
          (dom/div nil
                   (dom/h2 nil (first (get-in app [:info :title])))
                   (dom/div nil
                            (dom/span nil count)
                            (dom/button {:style {:marginLeft "5px"}
                                         :onClick #(increment % owner count)}
                                        "+"))
                   (dom/ul nil
                           (om/build-all msg (get app :messages) {:key 0}))))))))

(defn main []
  (om/root app-view app
           {:shared {:conn conn}
            :target (. js/document (getElementById "app"))}))
```

