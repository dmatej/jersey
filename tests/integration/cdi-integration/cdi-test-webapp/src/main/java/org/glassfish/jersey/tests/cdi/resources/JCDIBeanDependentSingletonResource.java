/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.tests.cdi.resources;

import java.util.logging.Logger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

import jakarta.annotation.ManagedBean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Application scoped JAX-RS resource registered as CDI managed bean.
 *
 * @author Paul Sandoz
 * @author Jakub Podlesak
 */
@Path("/jcdibean/dependent/singleton/{p}")
@ApplicationScoped
@ManagedBean
public class JCDIBeanDependentSingletonResource {

    private static final Logger LOGGER = Logger.getLogger(JCDIBeanDependentSingletonResource.class.getName());

    @Resource(name = "injectedResource")
    private int counter = 0;

    @Context
    private UriInfo uiFieldinject;

    @Context
    private ResourceContext resourceContext;

    private UriInfo uiMethodInject;

    @Context
    public void set(UriInfo ui) {
        this.uiMethodInject = ui;
    }

    @PostConstruct
    public void postConstruct() {
        LOGGER.info(String.format("In post construct of %s", this));
        ensureInjected();
    }

    @GET
    @Produces("text/plain")
    public String getMessage(@PathParam("p") String p) {
        LOGGER.info(String.format(
                "In getMessage in %s; uiFieldInject: %s; uiMethodInject: %s", this, uiFieldinject, uiMethodInject));
        ensureInjected();

        return String.format("%s: p=%s, queryParam=%s",
                uiFieldinject.getRequestUri().toString(), p, uiMethodInject.getQueryParameters().getFirst("x"));
    }

    @Path("exception")
    public String getException() {
        throw new JDCIBeanDependentException();
    }

    @Path("counter")
    @GET
    public synchronized String getCounter() {
        return Integer.toString(counter++);
    }

    @Path("counter")
    @PUT
    public synchronized void setCounter(String counter) {
        this.counter = Integer.decode(counter);
    }

    @PreDestroy
    public void preDestroy() {
        LOGGER.info(String.format("In pre destroy of %s", this));
    }

    private void ensureInjected() throws IllegalStateException {
        if (uiFieldinject == null || uiMethodInject == null || resourceContext == null) {
            throw new IllegalStateException();
        }
    }
}
