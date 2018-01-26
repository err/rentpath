(ns rentpath.scores.http-server
  "Web Application for the scores service."
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [compojure.core :as cmpj]
            [compojure.handler :as handler]
            [datomic.api :as d]
            [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :as wrap-json])
  (:import (org.eclipse.jetty.server Server)
           (java.util UUID Date)))

(defn event->facts [{user-id "user_id"
                     username "username"
                     event-type "event_type"}]
  (let [event-type->score #(get {"PushEvent" 5
                                 "PullRequestReviewCommentEvent" 4
                                 "WatchEvent" 3
                                 "CreateEvent" 2}
                                %
                                1)
        uid (UUID/fromString user-id)]
    [{:db/id "user"
      :user/id uid
      :user/username username
      :user/events {:db/id "event"
                    :event/type event-type
                    :event/time (Date.)}}

     ;; For now, this will guarantee correctness.
     ;; If this becomes a bottleneck, give up on absolute correctness
     ;; and move this arithmetic elsewhere (e.g. the appserver).
     ;; (My guess is it will likely never be an issue.)
     [:inc-by
      "user"
      [:user/id uid]
      :user/current-total-score
      (event-type->score event-type)]]))

(defn event-hook
  "Put the relevant facts for this event on a channel for
   async processing."
  [async-chan event]
  (a/put! async-chan
          {:facts (event->facts event)}))

(defn routes [{:keys [:datomic/uri
                      ::async-chan]}]
  (cmpj/routes
    (cmpj/POST "/event-hook" [:as {:keys [body]}]
      (event-hook async-chan
                  body)
      {:status 200
       :body {"status" "success"}})
    (cmpj/GET "/score/:user-id" [user-id]
      (let [uid (UUID/fromString user-id)]
        {:status 200
         :body {"status" "success"
                "current_score" (or (:user/current-total-score
                                      (d/entity (d/db (d/connect uri))
                                                [:user/id uid]))
                                    0)}}))
    (cmpj/GET "/top-users" []
      (let [db (d/db (d/connect uri))]
        {:status 200
         :body {"status" "success"
                ;; todo: optimize this (perhaps de-normalize to keep a ref of top scores)
                "top_users" (into []
                                  (comp (take 10)
                                        (map (fn [{e :e}]
                                               (:user/id (d/entity db e)))))
                                  (reverse (d/datoms db
                                                     :avet
                                                     :user/current-total-score)))}}))
    (cmpj/GET "*" []
      {:status 404
       :body {"status" "not-found"}})
    (cmpj/POST "*" []
      {:status 404
       :body {"status" "not-found"}})))

(defmethod ig/init-key ::jetty [_ {:keys [handler] :as opts}]
  (jetty/run-jetty handler (dissoc opts :handler)))

(defmethod ig/halt-key! ::jetty [_ srv]
  (.stop ^Server srv))

(defmethod ig/init-key ::handler [_ opts]
  ;; todo: exception handling
  (-> (handler/api (routes opts))
      (wrap-json/wrap-json-body {:keywords? false})
      (wrap-json/wrap-json-response)))
