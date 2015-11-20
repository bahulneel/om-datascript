(ns om-datascript.core
  (:require [datascript.core :as d]
            [datascript.query :as dq]
            [datascript.parser :as dp]
            [om.core :as om :include-macros true]))

(defn cursor-able?
  [val]
  (or
      (om/cursor? val)
      (satisfies? om/IToCursor val)
      (indexed? val)
      (map? val)
      (satisfies? ICloneable val)))

(defn query
  [q db args post-fn]
  (let [result (post-fn (apply d/q q db args))]
    (if (cursor-able? result)
      result
      [result])))

(defn get-test-queries
  [q]
  (let [in (set (:in q))
        entities (distinct (->> q :where (map first)))]
    (map (fn [e] (assoc q :in (vec (conj in e))))
         entities)))

(defn check-args [q e args]
  (let [in (rest (:in q))
        args (conj args e)
        conflict? (nil? (reduce (fn [m [a v]]
                                  (when m
                                    (if-let [old-v (get m a)]
                                      (when (= v old-v) m)
                                      (assoc m a v))))
                                {}
                                (map vector in args)))]
    (when-not conflict? args)))


(defn changed?
  [q args tx-report]
  (let [q (if (sequential? q) (dp/query->map q) q)
        q (if-not (:in q) (assoc q :in ['$]) q)
        {:keys [db-before db-after tx-data]} tx-report
        qs (get-test-queries q)
        es (distinct (map :e tx-data))
        changed (first (remove false?
                               (for [q qs e es]
                                 (when-let [args (check-args q e args)]
                                   (let [before (apply d/q q db-before args)
                                         after (apply d/q q  db-after args)]
                                     (not= before after))))))]
    changed))

(defn full-path
  [cursor path]
  (apply conj (om/-path cursor) path))

(defn check-transaction
  [cursor dst-path q args post-fn tx-report]
  (when (changed? q args tx-report)
    (let [old (get-in @cursor dst-path)
          new (query q (:db-after tx-report) args post-fn)]
      (when-not (= old new)
        (om/update! cursor dst-path new)))))

(defn project
  ([conn cursor dst-path q post-fn]
     (project conn cursor dst-path q [] post-fn))
  ([conn cursor dst-path q args post-fn]
      (let [result (query q @conn args post-fn)]
        (om/update! cursor dst-path result)
        (d/listen! conn
                   (full-path cursor dst-path)
                   (partial check-transaction cursor dst-path q args post-fn)))))

(defn apply-args
  [projections args]
  (clojure.walk/prewalk (fn [x]
                          (if (and (keyword? x) (x args))
                            (x args)
                            x))
                        projections))

(defn project-onto
  ([conn cursor projections]
     (project-onto conn cursor projections {}))
  ([conn cursor projections args]
     (project-onto conn cursor projections args []))
  ([conn cursor projections args path]
     (if (sequential? projections)
       (let [projections (apply-args projections args)]
         (apply project conn cursor path projections))
       (doseq [[k p] projections]
         (project-onto conn cursor p args (conj path k))))))

(defn unproject
  [conn cursor path]
  (d/unlisten! conn (full-path cursor path)))

(defn unproject-from
  ([conn cursor projections]
     (unproject-from conn cursor projections []))
  ([conn cursor projections path]
     (if (sequential? projections)
       (unproject conn cursor path)
       (doseq [[k p] projections]
         (unproject-from conn cursor p (conj path k))))))


(defrecord OwnerCursor [owner path]
  IDeref
  (-deref [_] (om/get-state owner))
  om/ICursor
  (-path [_] path)
  (-state [_] (om/get-state owner))
  om/ITransact
  (-transact! [_ korks f tag]
    (om/update-state! owner korks f)))

(defn owner-cursor [owner path]
  (->OwnerCursor owner path))
