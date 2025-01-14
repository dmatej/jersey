/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.cdi.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import jakarta.annotation.ManagedBean;
import jakarta.annotation.PostConstruct;

/**
 * Shows injection of path and query parameter into a managed bean.
 *
 * @author Roberto Chinnici
 * @author Jakub Podlesak
 */
@ManagedBean
@Path("echofield/{b}")
public class EchoParamFieldResource {

    @PathParam("b") String bInjected;

    String b;

    /**
     * Ensure we got path parameter value injected.
     */
    @PostConstruct
    @SuppressWarnings("unused")
    private void postConstruct() {
        if (bInjected == null) {
            throw new IllegalStateException("Field b has not been injected!");
        }
        b = bInjected;
    }

    /**
     * Return a string containing injected values.
     *
     * @param a value of a query parameter a.
     * @return message containing injected values.
     */
    @GET
    @Produces("text/plain")
    public String get(@QueryParam("a") String a) {
        return String.format("ECHO %s %s", a, b);
    }
}
