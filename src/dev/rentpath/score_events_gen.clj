(ns rentpath.score-events-gen
  "Generators relevant to the scores service."
  (:require [clj-http.client :as http]
            [clojure.test.check.generators :as gen]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def gen-user
  (gen/fmap (fn [[uid un]]
              {"user_id" uid
               "username" un})
            (gen/tuple gen/uuid
                       (gen/such-that (complement str/blank?)
                                      gen/string-alphanumeric))))
(def gen-event
  (let [users (gen/sample gen-user
                          100)]
    (gen/fmap (fn [[u ev]]
                (merge u
                       {"event_type" ev}))
              (gen/tuple (gen/elements users)
                         (gen/elements #{"PushEvent"
                                         "PullRequestReviewCommentEvent"
                                         "WatchEvent"
                                         "CreateEvent"})))))

(defn -main
  "Load 1000 events from 100 users into the dev system."
  [& args]
  (doseq [e (gen/sample gen-event
                        1000)]
    (http/post "http://localhost:5000/event-hook"
               {:headers {"Content-Type" "application/json"}
                :body (json/encode e)})))
