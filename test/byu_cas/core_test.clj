(ns byu-cas.core-test
  (:require [byu-cas.core :refer :all]
            [luminus.http-server :as http]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params  :refer [wrap-params]]))

(def req-holder (atom nil))

(defn http-handler [request]
  (do
    (println "resetting req-holder...")
    (reset! req-holder request)
    (println "new value is: " @req-holder))
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (:remote-addr request)})

(defn generate-app []
  (-> http-handler
      (wrap-cas #_"https://my.byu.edu/uPortal/Login"
                #_"humanities.byu.edu/wp-login.php"
                #_"https://forms.byu.edu"
                "http://localhost:3000"
                "https://news.ycombinator.com")
      (wrap-session)
      (wrap-params))) 

(def easy-map
  {:handler http-handler
   :port 3000})

(def running-server (atom nil))

(defn stop []
  (if @running-server
    (do (http/stop @running-server))))

(defn start []
  (stop)
  (reset! running-server (http/start
                          {:port 3000
                           :handler (generate-app)})))



;https://my.byu.edu/uPortal/Login
