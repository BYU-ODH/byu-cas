(ns byu-cas.core
  (:require [clojure.tools.logging :as log]
            [ring.util.response :refer [redirect]]
            [clojure.pprint :as pprint]
            [clojure.string :refer [join] :as s]
            [tick.alpha.api :as t])
  (:import (org.jasig.cas.client.validation Cas10TicketValidator    
                                            TicketValidationException)))

;Cas10TicketValidator: https://github.com/apereo/java-cas-client/tree/master/cas-client-core/src/main/java/org/jasig/cas/client/validation
;Cas10TicketValidator < AbstractCasProtocolUrlBasedTicketValidator  < AbstractUrlBasedTicketValidator implements TicketValidator


(defn pprints-mw [handler]
  (fn [req]
    (println "req:" )
    (pprint/pprint req)
    (let [resp (handler req)]
      (println "resp:")
      (pprint/pprint resp)
      resp)))


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

(defn construct-url [uri query-params]
  (str uri
       (when-not (empty? query-params)
         (->> query-params
              (map (fn [[k v]] (str (name k) "=" (str v))))
              (join \&)
              (str \?)))))

(defn extract-url [request]
  (str "http://" (get-in request [:headers "host"])
       (:uri request)))

(defn authentication-filter
  "Checks that the request is carrying CAS credentials (but does not validate them)"
  ([handler no-redirect?]
   (authentication-filter handler  no-redirect? BYU-CAS-server))
  ([handler no-redirect? cas-server]
   (fn [request]
     (if (valid? request)
       (handler request)
       (if (no-redirect? request)
         {:status 403}
         (redirect (str cas-server "/login?service=" (extract-url request))))))))

(defn adds-assertion-to-response [resp assertion]
  (assoc-in resp [:session const-cas-assertion] assertion))

(defn ticket [r] (or (get-in r [:query-params artifact-parameter-name])
                     (get-in r [:query-params (keyword artifact-parameter-name)])))


(defn ticket-validation-filter [handler success-hook]
  (let [ticket-validator (validator-maker)]
    (fn [{:keys [query-params uri] :as request}]
      (if-let [t (ticket request)]
        (try
          (let [url-str (extract-url request)
                assertion (validate ticket-validator t url-str )] 
            (-> (redirect (construct-url uri (dissoc query-params "ticket")))
                (adds-assertion-to-response assertion)
                success-hook))
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


(defn is-logged-in?
  "Takes a request and determines whether the requesting user is logged in or not."
  [req]
   (get-in req [:session const-cas-assertion]))

(defn- logs-out
  "Modifies a response map so as to end the user session.  Note that this does NOT end the CAS session, so users visiting your application will be redirected to CAS (per authentication-filter), and back to your application, only now authorized.  use with gateway parameter only

see ring.middleware.session/bare-session-response if curious how ring sessions work.   https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/session.clj"
  [resp]
  (assoc resp :session nil))

(defn logout-resp
  "Produces a response map that logs user out of the application (by ending the session) and CAS (by redirecting to the CAS logout endpoint).  Optionally takes a redirect URL which CAS uses to redirect the user (again!) after logout.  Redirect URL should be the *full* URL, including \"https:\""
  ([]
   (logout-resp nil))
  ([redirect-url]
   (let [added-str (if redirect-url
                     (str "?service=" redirect-url)
                     "")]
     (logs-out
      (redirect (str "https://cas.byu.edu/cas/logout" added-str))))))


(defn logout-filter [handler]
  (fn [req]
    (if (and (:session req)
             (or
              (when-let [logout-time (-> req :session :logout-time)]
                (t/> (t/now) logout-time))
              (= "true" (get-in req [:query-params "logout"]))))
      (logout-resp)
      (handler req))))

(defn set-timeout
  "Takes a number of minutes and a response.  Sets a time in :session, after which user will be logged out."
  [duration resp]
  (if (= :none duration)
    resp
    (assoc-in resp [:session :logout-time]
              (t/+ (t/now)
                   (t/new-duration duration :minutes)))))

(defn dependency-string [wrapped]
  (str "wrap-cas requires wrap-" wrapped " to work.  Please make sure to insert Ring's (wrap-" wrapped ") into your middleware stack, after (wrap-cas), like so:

(-> handler
    (wrap-cas)
    (wrap-" wrapped "))

  See https://github.com/ring-clojure/ring/tree/master/ring-core/src/ring/middleware"))

(defn dependency-filter [handler]
  (fn [req]
    (cond (not (contains? req :session))
          (throw (new RuntimeException (dependency-string "session")))

          (not ((every-pred :params :query-params :form-params) req))
          (throw (new RuntimeException (dependency-string "params")))

          :else
          (handler req))))

(defn cas
  ([handler]
   (cas handler {}))
  ([handler options]
   (let [options (merge {:enabled true
                         :no-redirect? (constantly false)
                         :server BYU-CAS-server}
                        options)]
     (if-not (:enabled options)
       handler
       (-> handler
           user-principal-filter
           (authentication-filter  (:no-redirect? options) (options :server BYU-CAS-server))
           (ticket-validation-filter (partial set-timeout (options :timeout 120)))
           (logout-filter)
           (dependency-filter)
           #_(pprints-mw))))))


(defn wrap-cas
  "Middleware that requires the user to authenticate with a CAS server.
  Is dependent on wrap-params and wrap-session; the general call will look something like

  (-> handler
      (wrap-cas :timeout 120)
      (wrap-session)
      (wrap-params))


  The users's username is added to the request map under the :username key.

  Accepts the following options:

    :enabled      - when false, the middleware does nothing
    :no-redirect? - if this predicate function returns true for a request, a
                    403 Forbidden response will be returned instead of a 302
                    Found redirect
    :server 	  - the target cas server
    :timeout      - takes a number representing the  length (in minutes) of the timeout period.  BYU recommends 120 (two hours), see README
  "
  ([& args]
   (apply cas args)))
