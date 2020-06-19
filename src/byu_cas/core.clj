(ns byu-cas.core
  (:require [clojure.tools.logging :as log]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [redirect]]
            [clojure.pprint :as pprint]
            [clojure.string :refer [join] :as s])
  (:import (org.jasig.cas.client.validation Cas10TicketValidator
                                            TicketValidationException)))

(def BYU-CAS-server "https://cas.byu.edu/cas")

(defn BYU-CAS-service
  "Returns a function that returns s because functions are required"
  [s]
  #(str s))

(def artifact-parameter-name "ticket")
(def const-cas-assertion "_const_cas_assertion_")

(defprotocol Validator
  (validate [v ticket service]))

(extend-type Cas10TicketValidator
  Validator
  (validate [v ticket service] (.validate v ticket service)))

;; (defn validator-maker [cas-server-fn]
;;   (Cas10TicketValidator. (cas-server-fn)))
(defn validator-maker
  ([] (validator-maker BYU-CAS-server))
  ([server] (Cas10TicketValidator. server)))

(defn- valid? [request]
  (or (get-in request [:session const-cas-assertion])
      (get-in request [:session (keyword const-cas-assertion)])
      (get-in request [:query-params artifact-parameter-name])
      (get-in request [:query-params (keyword artifact-parameter-name)])))

(defn authentication-filter
  "Checks that the request is carrying CAS credentials (but does not validate them)"
  ([handler service no-redirect?]
   (authentication-filter handler service no-redirect? BYU-CAS-server))
  ([handler service no-redirect? cas-server]
   (fn [request]
     (if (valid? request)
       (handler request)
       (if (no-redirect? request)
         {:status 403}
         (redirect (str cas-server "/login?service=" service)))))))

(defn adds-assertion-to-response [resp assertion]
  (assoc-in resp [:session const-cas-assertion] assertion))

#_(defn adds-assertion-to-request [req assertion]
  (update-in req [:query-params] assoc const-cas-assertion assertion))

(defn ticket [r] (or (get-in r [:query-params artifact-parameter-name])
                     (get-in r [:query-params (keyword artifact-parameter-name)])))

(defn ticket-validation-filter [handler service]
  (let [ticket-validator (validator-maker)]
    (fn [{:keys [query-params uri] :as request}]
      (if-let [t (ticket request)]
        (try
          (let [assertion (validate ticket-validator t service)]
            (-> (redirect (construct-url uri (dissoc query-params "ticket")))
                (adds-assertion-to-response assertion)))
          (catch TicketValidationException e
            (println "failed validation")
            (log/error "Ticket validation exception " e)
            {:status 403}))
        (handler request)))))

(defn user-principal-filter
  "Takes username and cas-info from request, and moves them into :username and :cas-info keys in the top level of the request map (rather than being buried in :query-params or :session)."
  [handler]
  (fn [request]
    (if-let [assertion (or
                        (get-in request [:query-params const-cas-assertion])
                        (get-in request [:query-params (keyword const-cas-assertion)])
                        (get-in request [:session const-cas-assertion])
                        (get-in request [:session (keyword const-cas-assertion)]))]
      (do
        (handler (-> request
                            (assoc :username (.getName (.getPrincipal assertion)))
                                        ;(assoc :cas-info (.getAttributes assertion))
                            (assoc :cas-info (.getAttributes (.getPrincipal assertion))))))
      (handler request))))

(defn construct-url [uri query-params]
  (str uri
       (when-not (empty? query-params)
         (->> query-params
              (map (fn [[k v]] (str (name k) "=" (str v))))
              (join \&)
              (str \?)))))

(defn cas
  "Middleware that requires the user to authenticate with a CAS server.

  The users's username is added to the request map under the :username key.

  Accepts the following options:

    :enabled      - when false, the middleware does nothing
    :no-redirect? - if this predicate function returns true for a request, a
                    403 Forbidden response will be returned instead of a 302
                    Found redirect
    :server 	  - the target cas server"
  [handler service & {:as options}]
  (let [options (merge {:enabled true
                        :no-redirect? (constantly false)
                        :server BYU-CAS-server}
                       options)]
    (if (:enabled options)
      (-> handler
        user-principal-filter
        (authentication-filter service (:no-redirect? options) (:server options))
        (ticket-validation-filter service)
        #_(removes-url-token)
        (wrap-params))
      handler)))


(defn wrap-remove-cas-code [handler]
  (fn [req]
    (let [session-code-path [:session :cas-code]
          get-url-code (fn [] (get-in req [:params :code]))
          get-internalized-code (fn [] (get-in req session-code-path))
          url-code (get-url-code)]
      (cond
        ;; (and (get-internalized-code)
        ;;      (get-url-code))
        ;; (redirect "http://localhost:3000") ;; TODO strip from the URL
        ;;
        (get-internalized-code) 
        (do 
          (handler req))
        ;;
        url-code
        (do
          (handler (assoc-in req session-code-path url-code)))))))


(defn wrap-cas
  "ring middleware to wrap with cas; requires a service string in addition to the handler.
  e.g. (-> handler (wrap-cas \"mysite.com\"))"
  ([handler service-string] (wrap-cas handler service-string BYU-CAS-server))
  ([handler service-string server]
   (cas handler service-string :server server)))
