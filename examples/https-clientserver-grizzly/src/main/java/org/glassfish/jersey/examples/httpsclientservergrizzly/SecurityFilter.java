/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.httpsclientservergrizzly;

import java.io.IOException;
import java.security.Principal;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import jakarta.inject.Inject;

import java.nio.charset.Charset;
import jakarta.xml.bind.DatatypeConverter;

/**
 * Simple authentication filter.
 *
 * Returns response with http status 401 when proper authentication is not provided in incoming request.
 *
 * @author Pavel Bucek
 * @see ContainerRequestFilter
 */
@Provider
@PreMatching
public class SecurityFilter implements ContainerRequestFilter {

    @Inject
    jakarta.inject.Provider<UriInfo> uriInfo;
    private static final String REALM = "HTTPS Example authentication";

    @Override
    public void filter(ContainerRequestContext filterContext) throws IOException {
        User user = authenticate(filterContext);
        filterContext.setSecurityContext(new Authorizer(user));
    }

    private User authenticate(ContainerRequestContext filterContext) {
        // Extract authentication credentials
        String authentication = filterContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authentication == null) {
            throw new AuthenticationException("Authentication credentials are required", REALM);
        }
        if (!authentication.startsWith("Basic ")) {
            return null;
            // additional checks should be done here
            // "Only HTTP Basic authentication is supported"
        }
        authentication = authentication.substring("Basic ".length());
        String[] values = new String(DatatypeConverter.parseBase64Binary(authentication), Charset.forName("ASCII")).split(":");
        if (values.length < 2) {
            throw new WebApplicationException(400);
            // "Invalid syntax for username and password"
        }
        String username = values[0];
        String password = values[1];
        if ((username == null) || (password == null)) {
            throw new WebApplicationException(400);
            // "Missing username or password"
        }

        // Validate the extracted credentials
        User user;

        if (username.equals("user") && password.equals("password")) {
            user = new User("user", "user");
            System.out.println("USER AUTHENTICATED");
            //        } else if (username.equals("admin") && password.equals("adminadmin")) {
            //            user = new User("admin", "admin");
            //            System.out.println("ADMIN AUTHENTICATED");
        } else {
            System.out.println("USER NOT AUTHENTICATED");
            throw new AuthenticationException("Invalid username or password\r\n", REALM);
        }
        return user;
    }

    public class Authorizer implements SecurityContext {

        private User user;
        private Principal principal;

        public Authorizer(final User user) {
            this.user = user;
            this.principal = new Principal() {

                public String getName() {
                    return user.username;
                }
            };
        }

        public Principal getUserPrincipal() {
            return this.principal;
        }

        public boolean isUserInRole(String role) {
            return (role.equals(user.role));
        }

        public boolean isSecure() {
            return "https".equals(uriInfo.get().getRequestUri().getScheme());
        }

        public String getAuthenticationScheme() {
            return SecurityContext.BASIC_AUTH;
        }
    }

    public class User {

        public String username;
        public String role;

        public User(String username, String role) {
            this.username = username;
            this.role = role;
        }
    }
}
