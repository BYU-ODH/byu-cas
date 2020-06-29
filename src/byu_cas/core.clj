(ns byu-cas.core
  (:require [clojure.tools.logging :as log]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [redirect]]
            [clojure.pprint :as pprint]
            [clojure.string :refer [join] :as s])
  (:import (org.jasig.cas.client.validation Cas10TicketValidator    
                                            TicketValidationException)))

;Cas10TicketValidator: https://github.com/apereo/java-cas-client/tree/master/cas-client-core/src/main/java/org/jasig/cas/client/validation


;Cas10TicketValidator < AbstractCasProtocolUrlBasedTicketValidator  < AbstractUrlBasedTicketValidator implements TicketValidator


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
          (let [assertion (validate ticket-validator t service)]   ;https://github.com/apereo/java-cas-client/blob/08038cb76772dcc70e4a85389ba4b8009a1146e2/cas-client-core/src/main/java/org/jasig/cas/client/validation/AbstractUrlBasedTicketValidator.java#L185n
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


(defn is-logged-in? [req]
   (get-in req [:session const-cas-assertion]))

(defn- logs-out
  "Modifies a response map so as to end the user session.  Note that this does NOT end the CAS session, so users visiting your application will be redirected to CAS (per authentication-filter), and back to your application, only now authorized.  use with gateway parameter only


see ring.middleware.session/bare-session-response if curious how ring sessions work.   https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/middleware/session.clj
  "
  [resp]
  (assoc resp :session nil))

(defn logout-resp
  "Produces a response map that logs user out of the application (by ending the session) and CAS (by redirecting to the CAS logout endpoint).  Optionally takes a redirect URL which CAS uses to redirect the user (again!) after logout.  Redirect URL should be the *full* URL, including \"https:\""
  ([]
   (logs-out
    (redirect "https://cas.byu.edu/cas/logout")))
  ([redirect-url]
   (-> (logout-resp)
       (update-in [:headers "Location"] (str "?service=" redirect-url)))))

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

        ((fn [handler]
            (fn [req]
              (println "req:" )
              (pprint/pprint req)
              (let [resp (handler req)]
                (println "resp:")
                (pprint/pprint resp)
                resp))))
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
  e.g. (-> handler (wrap-cas \"mysite.com\"))
  Requires ring.middleware.session/wrap-session, which should be called on the \"outside\" of this, in the sense that when middleware are thread-chained, i.e.

  (-> handler
    (wrap-cookies)
    (wrap-fonts)
    (wrap-css))

  Then requests start at the bottom/outside and move up/inward, while responses start at the top/inside and move down/outward."
  ([handler service-string] (wrap-cas handler service-string BYU-CAS-server))
  ([handler service-string server]
   (cas handler service-string :server server)))
