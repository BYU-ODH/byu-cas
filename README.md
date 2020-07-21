# byu-cas

A Clojure library to simplify connection to BYU CAS for authentication.

(link to auto-generated docs: https://byu-odh.github.io/byu-cas/)

## Installation 
[byu-odh/byu-cas "3"]


## Usage

###Most common case
Wrap your app in `wrap-cas`, set the timeout to 120 minutes (BYU guideline), then wrap that in `wrap-session` and `wrap-params`, which `wrap-cas` needs to function correctly.

 ```
 (defn app []
  (-> handler
      (wrap-cas :timeout 120)
      (wrap-session)
	  (wrap-params)))
 ```
Note that the user's NetID will be available under `:username` in the request map.

##Logging Out
The first thing you need to understand is that any user who logs in with your application is logged into *two* systems---both your app and "BYU in general," i.e. the **C**entral **A**uthentication **S**erver.  

In the majority of cases, you would rather not think about this and would like "log out of our app" to be functionally equivalent to "log out of BYU," so we'll treat that case first.

###What logging out is
To log a user out of your app and out of CAS, you need to 
 - end the session, either by having them delete their session cookie, or by removing the session store on your app's server.  
 - redirect them to the logout endpoint of BYU's CAS system, https://cas.byu.edu/cas/logout"
 
`byu-cas` provides a function, `logout-resp`, which does both---though you likely won't need to call it.

###Easy Logout Setup
####Timeout
The default for `byu-cas` is to log users out after two hours.  You can change this by specifying a time (in minutes) in the `timeout` option of `wrap-cas`.  You can also pass in `:none`, which will remove the timeout.


It's worth reading through BYU's CAS policies, which can be found [here](https://it.byu.edu/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Deac4e9f90a0a3c0e4b937e7cc6b49811).


####URL
Simply add `logout=true` as a query parameter, i.e. http://myapp.byu.edu/somepage?logout=true, and `wrap-cas` will detect it and redirect the user to the main CAS endpoint (WITHOUT processing the request).



##How CAS works
CAS can be a little hairy; if you need to do anything out of the ordinatry, the following links should be useful.


 - [Webflow diagram](https://apereo.github.io/cas/development/protocol/CAS-Protocol.html)
- [Protocol Specification](https://apereo.github.io/cas/6.2.x/protocol/CAS-Protocol-Specification.html)
 - [On Logging Out](https://apereo.github.io/cas/6.2.x/installation/Logout-Single-Signout.html)


## License

Copyright © 2020 BYU Office of Digital Humanities

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
