/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.helloworld;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.InvocationCallback;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.util.runner.ConcurrentRunner;
import org.glassfish.jersey.test.util.runner.RunSeparately;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(ConcurrentRunner.class)
public class HelloWorldTest extends JerseyTest {

    @Override
    protected ResourceConfig configure() {
        // mvn test -Djersey.config.test.container.factory=org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
        // mvn test -Djersey.config.test.container.factory=org.glassfish.jersey.test.grizzly.GrizzlyTestContainerFactory
        // mvn test -Djersey.config.test.container.factory=org.glassfish.jersey.test.jdkhttp.JdkHttpServerTestContainerFactory
        // mvn test -Djersey.config.test.container.factory=org.glassfish.jersey.test.simple.SimpleTestContainerFactory
        enable(TestProperties.LOG_TRAFFIC);
        // enable(TestProperties.DUMP_ENTITY);
        return new ResourceConfig(HelloWorldResource.class);
    }

//    Uncomment to use Grizzly async client
//    @Override
//    protected void configureClient(ClientConfig clientConfig) {
//        clientConfig.connector(new GrizzlyConnector(clientConfig));
//    }

    @Test
    @Ignore("not compatible with test framework (doesn't use client())")
    public void testHelloWorld() throws Exception {
        URL getUrl = UriBuilder.fromUri(getBaseUri()).path(App.ROOT_PATH).build().toURL();
        HttpURLConnection connection = (HttpURLConnection) getUrl.openConnection();
        try {
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "text/plain");
            assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());
        } finally {
            connection.disconnect();
        }
    }

    @Test
    public void testConnection() {
        Response response = target().path(App.ROOT_PATH).request("text/plain").get();
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testClientStringResponse() {
        String s = target().path(App.ROOT_PATH).request().get(String.class);
        assertEquals(HelloWorldResource.CLICHED_MESSAGE, s);
    }

    @Test
    public void testAsyncClientRequests() throws InterruptedException {
        final int REQUESTS = 10;
        final CountDownLatch latch = new CountDownLatch(REQUESTS);
        final long tic = System.currentTimeMillis();
        for (int i = 0; i < REQUESTS; i++) {
            final int id = i;
            target().path(App.ROOT_PATH).request().async().get(new InvocationCallback<Response>() {
                @Override
                public void completed(Response response) {
                    try {
                        final String result = response.readEntity(String.class);
                        assertEquals(HelloWorldResource.CLICHED_MESSAGE, result);
                    } finally {
                        latch.countDown();
                    }
                }

                @Override
                public void failed(Throwable error) {
                    error.printStackTrace();
                    latch.countDown();
                }
            });
        }
        latch.await(10 * getAsyncTimeoutMultiplier(), TimeUnit.SECONDS);
        final long toc = System.currentTimeMillis();
        Logger.getLogger(HelloWorldTest.class.getName()).info("Executed in: " + (toc - tic));
    }

    @Test
    public void testHead() {
        Response response = target().path(App.ROOT_PATH).request().head();
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
    }

    @Test
    public void testFooBarOptions() {
        Response response = target().path(App.ROOT_PATH).request().header("Accept", "foo/bar").options();
        assertEquals(200, response.getStatus());
        final String allowHeader = response.getHeaderString("Allow");
        _checkAllowContent(allowHeader);
        assertEquals("foo/bar", response.getMediaType().toString());
        assertEquals(0, response.getLength());
    }

    @Test
    public void testTextPlainOptions() {
        Response response = target().path(App.ROOT_PATH).request().header("Accept", MediaType.TEXT_PLAIN).options();
        assertEquals(200, response.getStatus());
        final String allowHeader = response.getHeaderString("Allow");
        _checkAllowContent(allowHeader);
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
        final String responseBody = response.readEntity(String.class);
        _checkAllowContent(responseBody);
    }

    private void _checkAllowContent(final String content) {
        assertTrue(content.contains("GET"));
        assertTrue(content.contains("HEAD"));
        assertTrue(content.contains("OPTIONS"));
    }

    @Test
    public void testMissingResourceNotFound() {
        Response response;

        response = target().path(App.ROOT_PATH + "arbitrary").request().get();
        assertEquals(404, response.getStatus());

        response = target().path(App.ROOT_PATH).path("arbitrary").request().get();
        assertEquals(404, response.getStatus());
    }

    @Test
    @RunSeparately
    public void testLoggingFilterClientClass() {
        Client client = client();
        client.register(CustomLoggingFilter.class).property("foo", "bar");
        CustomLoggingFilter.preFilterCalled = CustomLoggingFilter.postFilterCalled = 0;
        String s = target().path(App.ROOT_PATH).request().get(String.class);
        assertEquals(HelloWorldResource.CLICHED_MESSAGE, s);
        assertEquals(1, CustomLoggingFilter.preFilterCalled);
        assertEquals(1, CustomLoggingFilter.postFilterCalled);
    }

    @Test
    @RunSeparately
    public void testLoggingFilterClientInstance() {
        Client client = client();
        client.register(new CustomLoggingFilter()).property("foo", "bar");
        CustomLoggingFilter.preFilterCalled = CustomLoggingFilter.postFilterCalled = 0;
        String s = target().path(App.ROOT_PATH).request().get(String.class);
        assertEquals(HelloWorldResource.CLICHED_MESSAGE, s);
        assertEquals(1, CustomLoggingFilter.preFilterCalled);
        assertEquals(1, CustomLoggingFilter.postFilterCalled);
    }

    @Test
    @RunSeparately
    public void testLoggingFilterTargetClass() {
        WebTarget target = target().path(App.ROOT_PATH);
        target.register(CustomLoggingFilter.class).property("foo", "bar");
        CustomLoggingFilter.preFilterCalled = CustomLoggingFilter.postFilterCalled = 0;
        String s = target.request().get(String.class);
        assertEquals(HelloWorldResource.CLICHED_MESSAGE, s);
        assertEquals(1, CustomLoggingFilter.preFilterCalled);
        assertEquals(1, CustomLoggingFilter.postFilterCalled);
    }

    @Test
    @RunSeparately
    public void testLoggingFilterTargetInstance() {
        WebTarget target = target().path(App.ROOT_PATH);
        target.register(new CustomLoggingFilter()).property("foo", "bar");
        CustomLoggingFilter.preFilterCalled = CustomLoggingFilter.postFilterCalled = 0;
        String s = target.request().get(String.class);
        assertEquals(HelloWorldResource.CLICHED_MESSAGE, s);
        assertEquals(1, CustomLoggingFilter.preFilterCalled);
        assertEquals(1, CustomLoggingFilter.postFilterCalled);
    }

    @Test
    @RunSeparately
    public void testConfigurationUpdate() {
        Client client1 = client();
        client1.register(CustomLoggingFilter.class).property("foo", "bar");

        Client client = ClientBuilder.newClient(client1.getConfiguration());
        CustomLoggingFilter.preFilterCalled = CustomLoggingFilter.postFilterCalled = 0;
        String s = target().path(App.ROOT_PATH).request().get(String.class);
        assertEquals(HelloWorldResource.CLICHED_MESSAGE, s);
        assertEquals(1, CustomLoggingFilter.preFilterCalled);
        assertEquals(1, CustomLoggingFilter.postFilterCalled);
    }
}
