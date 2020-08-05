# byu-cas

A Clojure library to simplify connection to BYU CAS for authentication.

(link to auto-generated docs: https://byu-odh.github.io/byu-cas/)

## Installation 
[byu-odh/byu-cas "4"]


## Usage

### Most common case
Wrap your app in `wrap-cas`, set the timeout to 120 minutes (BYU guideline), then wrap that in `wrap-session` and `wrap-params`, which `wrap-cas` needs to function correctly.

 ```
 (defn app []
  (-> handler
      (wrap-cas {:timeout 120})
      (wrap-session)
	  (wrap-params)))
 ```
Note that the user's NetID will be available under `:username` in the request map.

## Logging Out
The first thing you need to understand is that any user who logs in with your application is logged into *two* systems---both your app and "BYU in general," i.e. the **C**entral **A**uthentication **S**erver.  

In the majority of cases, you would rather not think about this and would like "log out of our app" to be functionally equivalent to "log out of BYU," so we'll treat that case first.

### What logging out is
To log a user out of your app and out of CAS, you need to 
 - end the session, either by having them delete their session cookie, or by removing the session store on your app's server.  
 - redirect them to the logout endpoint of BYU's CAS system, https://cas.byu.edu/cas/logout"
 
`byu-cas` provides a function, `logout-resp`, which does both---though you likely won't need to call it.

### Easy Logout Setup

#### Timeout

BYU's CAS policies, which can be found [here](https://it.byu.edu/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Deac4e9f90a0a3c0e4b937e7cc6b49811), specify a two-hour timeout period (achieved by adding `:timeout 120` to the `options` map of `wrap-cas`).  If `:timeout` is set, `byu-cas` logs the time when a user is authenticated, and after the specified time period, logs them out by destroying their session and redirecting their next request to CAS's logout endpoint.


#### URL
Simply add `logout=true` as a query parameter, i.e. http://myapp.byu.edu/somepage?logout=true, and `wrap-cas` will detect it and redirect the user to the main CAS endpoint (WITHOUT processing the request).


## Proxies
The CAS system relies on redirects (if you check the URL when at the CAS login page, you'll notice a "service" query param that corresponds to your application).  To do this it needs to know what URL to redirect your user to.

If there's no proxy in your setup, don't worry about this.  `byu-cas` will use infer the correct URL from the request.

If you *are* behind a proxy and want to keep the user on the outward-facing domain, you can pass in the `:service` key, which takes either a host-uri string (A host-uri string is just a URL with the query parameters removed, e.g. "http://byu.edu/proxyfront"), or a function of the form `host-uri-string -> host-uri-string`. 


## How CAS works
CAS can be a little hairy; if you need to do anything out of the ordinatry, the following links should be useful.


 - [Webflow diagram](https://apereo.github.io/cas/development/protocol/CAS-Protocol.html)
 - [Protocol Specification](https://apereo.github.io/cas/6.2.x/protocol/CAS-Protocol-Specification.html)
 - [On Logging Out](https://apereo.github.io/cas/6.2.x/installation/Logout-Single-Signout.html)


## License

Copyright © 2020 BYU Office of Digital Humanities

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
