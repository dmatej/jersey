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

package org.glassfish.jersey.jetty.connector;

import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;

import org.junit.Test;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Martin Matula
 * @author Arul Dhesiaseelan (aruld at acm.org)
 */
public class TimeoutTest extends JerseyTest {
    private static final Logger LOGGER = Logger.getLogger(TimeoutTest.class.getName());

    @Path("/test")
    public static class TimeoutResource {
        @GET
        public String get() {
            return "GET";
        }

        @GET
        @Path("timeout")
        public String getTimeout() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "GET";
        }
    }

    @Override
    protected Application configure() {
        ResourceConfig config = new ResourceConfig(TimeoutResource.class);
        config.register(new LoggingFeature(LOGGER, LoggingFeature.Verbosity.PAYLOAD_ANY));
        return config;
    }

    @Override
    protected void configureClient(ClientConfig config) {
        config.connectorProvider(new JettyConnectorProvider());
    }

    @Test
    public void testFast() {
        Response r = target("test").request().get();
        assertEquals(200, r.getStatus());
        assertEquals("GET", r.readEntity(String.class));
    }

    @Test
    public void testSlow() {
        final URI u = target().getUri();
        ClientConfig config = new ClientConfig().property(ClientProperties.READ_TIMEOUT, 1_000);
        config.connectorProvider(new JettyConnectorProvider());
        Client c = ClientBuilder.newClient(config);
        WebTarget t = c.target(u);
        try {
            t.path("test/timeout").request().get();
            fail("Timeout expected.");
        } catch (ProcessingException e) {
            assertThat("Unexpected processing exception cause",
                    e.getCause(), instanceOf(TimeoutException.class));
        } finally {
            c.close();
        }
    }

    @Test
    public void testTimeoutInRequest() {
        final URI u = target().getUri();
        ClientConfig config = new ClientConfig();
        config.connectorProvider(new JettyConnectorProvider());
        Client c = ClientBuilder.newClient(config);
        WebTarget t = c.target(u);
        try {
            t.path("test/timeout").request().property(ClientProperties.READ_TIMEOUT, 1_000).get();
            fail("Timeout expected.");
        } catch (ProcessingException e) {
            assertThat("Unexpected processing exception cause",
                    e.getCause(), instanceOf(TimeoutException.class));
        } finally {
            c.close();
        }
    }
}
