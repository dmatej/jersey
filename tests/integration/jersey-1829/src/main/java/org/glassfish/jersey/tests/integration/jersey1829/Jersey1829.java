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

package org.glassfish.jersey.tests.integration.jersey1829;

import java.util.Collections;
import java.util.Set;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

/**
 * Application class with test resource that returns custom status reason phrase.
 *
 * @author Miroslav Fuksa
 */
public class Jersey1829 extends Application {

    public static final String REASON_PHRASE = "my-phrase";

    @SuppressWarnings("unchecked")
    @Override
    public Set<Class<?>> getClasses() {
        return Collections.<Class<?>>singleton(TestResource.class);
    }

    @Path("resource")
    public static class TestResource {

        @GET
        @Path("428")
        public Response get() {
            return Response.status(new Custom428Type()).build();
        }

        @GET
        @Path("428-entity")
        public Response getWithEntity() {
            return Response.status(new Custom428Type()).entity("entity").build();
        }
    }

    public static class Custom428Type implements Response.StatusType {

        @Override
        public int getStatusCode() {
            return 428;
        }

        @Override
        public String getReasonPhrase() {
            return REASON_PHRASE;
        }

        @Override
        public Response.Status.Family getFamily() {
            return Response.Status.Family.CLIENT_ERROR;
        }
    }

}
