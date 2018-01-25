(ns rentpath.scores.server.router
  (:require [clojure.string :as str]))

(defn route-spec->map
  "Given a spec with colon-prepended names for route params
  (e.g. 'foo/:foo') and a uri, return the map of param names to values.

  If the spec doesn't match, returns nil. If the spec matches, but
  there are no route params, returns {}.

  Param names are keywordized, so make sure they are valid keywords."
  [spec uri]
  (let [pat #":([^/]+)"
        vs (re-find (re-pattern (str "^"
                                     (str/replace spec
                                                  pat
                                                  "([^/]+)")
                                     "/?$"))
                    uri)]
    (when (seq vs)
      (zipmap (map (fn [[_whole grp]]
                     (keyword grp))
                   (re-seq pat
                           spec))
              (rest vs)))))

(defn route-req
  "Routes a ring request based on the provided routes.

  routes are a map of http-method -> route-spec -> handler,
  like so:

  {:get {\"foo/:foo\" (fn [{:keys [foo]} req]
                        ...)}

   :post {\"bar/:bar/:baz\" (fn [{:keys [bar baz]} req]
                              ...)}}

  Handlers are invoked with the parsed route-spec (see
  `route-spec->map`) and the request."
  [routes
   {:keys [request-method
           uri] :as req}]
  (let [nf {:status 404
            :headers {"Content-Type" "text/html"}
            :body "Not Found"}]
    (if-let [rs (get routes
                     request-method)]
      (if-let [[h r] (some (fn [[spec hand]]
                             (when-let [r (route-spec->map spec
                                                           uri)]
                               [hand r]))
                           rs)]
        (try (h r req)
             (catch Exception e
               ;; todo: add real logging
               (println "An error occurred during handling.")
               (println (.getMessage e))
               (.printStackTrace e)
               {:status 500
                :header {"Content-Type" "text/html"}
                :body "An error occurred."}))
        nf)
      nf)))
