(defproject byu-cas "0.1.0-SNAPSHOT"
  :description "BYU CAS authentication layer for Clojure"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.jasig.cas.client/cas-client-core "3.2.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ring/ring-defaults "0.2.0"]
                 [ring-middleware-format "0.7.0"]
                 [ring/ring-defaults "0.2.0"]
                 [metosin/ring-http-response "0.6.5"]])
