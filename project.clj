(defproject byu-odh/byu-cas "1"
  :description "BYU CAS authentication layer for Clojure"
  :url "https://github.com/BYU-ODH/byu-cas"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.jasig.cas.client/cas-client-core "3.6.1"]
                 [org.clojure/tools.logging "0.5.0"]
                 [ring/ring-defaults "0.3.2"]
                 [ring-middleware-format "0.7.4"]
                 [ring/ring-defaults "0.3.2"]
                 [metosin/ring-http-response "0.9.1"]
                 [luminus-jetty "0.1.9"]]
  :source-paths ["src" "test"]
  :plugins [[lein-codox "0.10.7"]]
  :codox {:source-paths ["src"]
          :output-path "docs"})
