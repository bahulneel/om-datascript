(ns om-datascript.core
  (:require [datascript :as d]
            [datascript.query :as dq]
            [om.core :as om :include-macros true]))

(defn query
  [q db post-fn]
  (let [result (post-fn (d/q q db))]
    (if (or (map? result) (vector? result))
      result
      [result])))

(defn get-test-queries
  [q]
  (let [entities (->> q :where (map first))]
    (map (fn [e] (update-in q [:in] conj e))
         entities)))

(defn changed?
  [q tx-report]
  (let [q (if (sequential? q) (dq/parse-query q) q)
        q (if-not (:in q) (assoc q :in ['$]) q)
        {:keys [db-before db-after tx-data]} tx-report
        qs (get-test-queries q)
        es (distinct (map :e tx-data))
        changed (first (remove false?
                               (for [q qs e es]
                                 (let [before (d/q q db-before e)
                                       after (d/q q db-after e)]
                                   (not= before after)))))]
    changed))

(defn full-path
  [cursor path]
  (apply conj (om/-path cursor) path))

(defn check-transaction
  [cursor dst-path q post-fn tx-report]
  (when (changed? q tx-report)
    (let [old (get-in @cursor dst-path)
          new (query q (:db-after tx-report) post-fn)]
      (when-not (= old new)
        (om/transact! cursor dst-path (constantly new))))))

(defn project
  [conn cursor dst-path q post-fn]
  (let [result (query q @conn post-fn)]
    (om/transact! cursor dst-path (constantly result))
    (d/listen! conn
               (full-path cursor dst-path)
               (partial check-transaction cursor dst-path q post-fn))))

(defn unproject
  [conn cursor path]
  (d/unlisten! conn (full-path cursor path)))

