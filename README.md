# byu-cas

A Clojure library to simplify connection to BYU CAS for authentication.

(link to auto-generated docs: https://byu-odh.github.io/byu-cas/)

## Usage

[byu-odh/byu-cas "2"]


byu-cas is a Clojure library for dealing with BYU's CAS (Central Authentication Service) system.

It's worth it to acquaint yourself with how CAS works (good resources are 

[the webflow diagram](https://apereo.github.io/cas/development/protocol/CAS-Protocol.html) and [the protocol specification](https://apereo.github.io/cas/6.1.x/protocol/CAS-Protocol-Specification.html)), but the gist is:  when you log into CAS, it logs such, puts a cookie on your system, and then redirects you to whatever application you were trying to get to, with a query-param ("ticket", usually) in the URL.  That application (hereafter referred to as the "client application") reads the ticket, and calls CAS to confirm that this person is OK'd by CAS.  It's then the *application*'s responsbility to interpret and manage that.

`byu-cas` does so with sessions, which is why `wrap-cas`, the flagship function of `byu-cas`, is dependent on `wrap-session` being called "outside" of it, like so:


 ```
 (defn generate-app []
  (-> http-handler
      (wrap-cas "http://my-great-app.byu.edu")
      (wrap-session)))
 ```

`wrap-cas` redirects users to BYU's authentication server, `cas.byu.edu`, and CAS (after verifying netid/password) redirects them back to the original app, but with a "ticket" query param.  `wrap-cas` sees the ticket, calls the central CAS server to verify this person is authenticated, creates a session, and then redirects them to the same URL, only with the ticket param removed now that it's no longere necessary.

In addition to `wrap-cas`, the library also provides a `logout-resp` function, which produces a 302 redirect response that ends the *local* session (as in, the user's session on your particular app), and also redirects them to cas.byu.edu/cas/logout, which logs them out of CAS altogether. 



## Updates
- 2016.10.10 :: added wrap-remove-cas-code to allow refreshing of pages after code has been provided

## License

Copyright © 2016 BYU Office of Digital Humanities

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
