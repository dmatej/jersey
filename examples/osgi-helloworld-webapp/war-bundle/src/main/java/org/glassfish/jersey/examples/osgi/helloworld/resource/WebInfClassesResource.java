/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.osgi.helloworld.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 * This resource is physically located in WEB-INF/classes of a OSGI bundle.
 *
 * @author Stepan Vavra
 */
@Path("/webinf")
public class WebInfClassesResource {

    @GET
    @Produces("text/plain")
    public String getWebInfMessage() {
        return "WebInfClassesResource";
    }

}
