 (ns byu-cas.core-test
     (:require [byu-cas.core :refer :all]
                 [luminus.http-server :as http]
                 [ring.middleware.session :refer [wrap-session]]))

(defn http-handler [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (:remote-addr request)})

(defn generate-app []
  (-> http-handler
      (wrap-cas #_"https://my.byu.edu/uPortal/Login"
                #_"humanities.byu.edu/wp-login.php"
                #_"https://forms.byu.edu"
                "http://localhost:3000"
                #_"kmansadventures.proboards.com")
      (wrap-session))) 

(def easy-map
  {:handler http-handler
   :port 3000})

(def running-server (atom nil))

(defn start []
  (reset! running-server (http/start
                          {:port 3000
                           :handler (generate-app)})))

(defn stop []
  (if @running-server
    (http/stop @running-server)))

;https://my.byu.edu/uPortal/Login
