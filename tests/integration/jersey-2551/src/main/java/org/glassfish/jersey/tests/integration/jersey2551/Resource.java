/*
 * Copyright (c) 2014, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.tests.integration.jersey2551;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import jakarta.inject.Inject;

import org.glassfish.hk2.api.ServiceLocator;

/**
 * @author Michal Gajdos
 */
@Path("/")
public class Resource {

    @Inject
    private ServiceLocator locator;

    @GET
    public Response get() {
        return locator instanceof ServiceLocatorGenerator.CustomServiceLocator
            ? Response.ok().build() : Response.serverError().build();
    }
}
