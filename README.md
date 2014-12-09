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

(d/transact! conn
                 [{:db/id 1
                   :title "Hello world!"}
                  {:db/id 2
                   :count 0}])

(defn get-conn [owner]
  (om/get-shared owner :conn))

(defn increment [e owner count]
  (d/transact! (get-conn owner)
               [[:db/add 2 :count (inc count)]]))

(defn app-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [conn (get-conn owner)]
        (od/project conn app
                    [:title]
                    '[:find ?title
                      :where
                      [?e :title ?title]]
                    ffirst)
        (od/project conn app
                    [:count]
                    '[:find ?count
                      :where
                      [?e :count ?count]]
                    ffirst)))
    IWillUnmount
    (will-unmount [_]
      (let [conn (get-conn owner)]
        (od/unproject conn app [:title])
        (od/unproject conn app [:count])))
    om/IRender
    (render [_]
      (let [count (first (get app :count))]
        (when (= 10 count)
          (od/unproject conn app [:count]))
        (dom/div nil
                 (dom/h2 nil (first (get app :title)))
                 (dom/div nil
                          (dom/span nil count)
                          (dom/button {:style {:marginLeft "5px"}
                                       :onClick #(increment % owner count)}
                                      "+")))))))
(defn main []
  (om/root app-view app
           {:shared {:conn conn}
            :target (. js/document (getElementById "app"))}))
```

