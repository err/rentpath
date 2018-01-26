(ns rentpath.scores.datomic
  "Datomic bits of the scores service. Includes an async
  datomic writer process."
  (:require [datomic.api :as d]
            [clojure.core.async :as a]
            [integrant.core :as ig]
            [clojure.tools.logging :as log]))

(def schema
  [{:db/ident :user/id
    :db/doc "The user's id"
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}
   {:db/ident :user/username
    :db/doc "The user's name"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :user/events
    :db/doc "The list of events associated with this user"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true
    :db/index true}
   {:db/ident :event/type
    :db/doc "The type of event"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :event/time
    :db/doc "The clock time when the event was received"
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :user/current-total-score
    :db/doc "The current de-normalized score (for faster reads)"
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :inc-by
    :db/doc
    "A fn to increment by a certain amount.

    Takes a tx-id and an id in order to give the user
    control over how the entity is identified. (Useful when
    you're not sure if the entity exists yet or not.)"
    :db/fn #db/fn {:lang "clojure"
                   :params [db tx-id id attr amt]
                   :code (let [{a attr
                                :or {a 0}} (d/entity db id)]
                           [[:db/add tx-id attr (+ a amt)]])}}])

(defn async-writer
  "Async Writer Process for Datomic."
  [datomic-uri in]
  (a/go-loop []
    ;; todo: add batching as applicable
    (when-some [{:keys [facts] :as _v} (a/<! in)]
      (try @(d/transact-async (d/connect datomic-uri)
                              facts)
           (catch Exception e
             ;; todo: add better error handling (e.g. retries, backoff, etc)
             (log/error e
                        "Error in datomic transaction process"
                        {:facts facts})))
      (recur))))

(defmethod ig/init-key ::async-writer [_ {:keys [:datomic/uri
                                                   ::chan-in]}]
  (async-writer uri
                chan-in))
