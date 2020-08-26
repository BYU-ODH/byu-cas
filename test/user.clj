(ns user
  (:require [byu-cas.core :refer :all]
            [luminus.http-server :as http]
            [ring.middleware.session :refer [wrap-session]]
            [clojure.pprint :as pprint]
            [ring.middleware.params  :refer [wrap-params]]))

(def req-holder (atom nil))

(defn http-handler [request]
  (do
    (do (println "resetting req-holder...")
        (reset! req-holder request)
        (println "new value is: " )
        (pprint/pprint @req-holder)))
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (:remote-addr request)})

(defn generate-app []
  (-> http-handler
      (wrap-cas {:service identity})
      (wrap-session)
      (wrap-params))) 

(def easy-map
  {:handler http-handler
   :port 3000})

(defonce running-server (atom nil))

(defn stop []
  (if @running-server
    (do (http/stop @running-server))))

(defn start []
  (stop)
  (reset! running-server (http/start
                          {:port 3000
                           :handler (generate-app)})))



                                        ;https://my.byu.edu/uPortal/Login

