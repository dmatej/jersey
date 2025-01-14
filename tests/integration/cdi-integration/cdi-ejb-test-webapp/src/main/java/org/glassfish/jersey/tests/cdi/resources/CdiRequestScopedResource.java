/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved.
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

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Request scoped CDI bean injected with EJB timers.
 *
 * @author Jakub Podlesak
 */
@RequestScoped
@Path("request-scoped")
public class CdiRequestScopedResource {

    @EJB EjbSingletonTimer ejbInjectedTimer;
    @Inject EjbSingletonTimer jsr330InjectedTimer;
    @Inject CdiAppScopedTimer cdiTimer;

    @GET
    @Path("ejb-injected-timer")
    public String getEjbTime() {
        return Long.toString(ejbInjectedTimer.getMiliseconds());
    }

    @GET
    @Path("jsr330-injected-timer")
    public String getJsr330Time() {
        return Long.toString(jsr330InjectedTimer.getMiliseconds());
    }

    @GET
    public String getMyself() {
        return this.toString();
    }
}
