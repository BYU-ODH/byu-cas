 (ns byu-cas.core-test
     (:require [byu-cas.core :refer :all]
               [luminus.http-server :as http]))

(defn http-handler [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (:remote-addr request)})

(def easy-map
  {:handler http-handler
   :port 3000})

(def running-server (atom nil))

(defn test-with-middleware [mw-fn]
  (reset! running-server  (http/start
                   {:port 3000
                    :handler (mw-fn http-handler)})))

(defn stop []
  (if @running-server
    (http/stop @running-server)))

;https://my.byu.edu/uPortal/Login

(defn start []
  (test-with-middleware #(wrap-cas % #_"https://my.byu.edu/uPortal/Login"
                                   
                                   "humanities.byu.edu/wp-login.php"
                                   #_"https://forms.byu.edu"
                                   #_"localhost:3000"
                                   #_"kmansadventures.proboards.com"
                                   )))
