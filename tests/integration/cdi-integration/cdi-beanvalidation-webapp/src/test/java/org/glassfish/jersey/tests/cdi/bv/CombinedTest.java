/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.tests.cdi.bv;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainerProvider;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.inject.hk2.Hk2InjectionManagerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.TestProperties;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;

import org.hamcrest.CoreMatchers;
import org.jboss.weld.environment.se.Weld;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Test both Jersey apps running simultaneously within a single Grizzly HTTP server
 * to make sure injection managers do not interfere. The test is not executed
 * if other than the default (Grizzly) test container has been set.
 * For Servlet based container testing, the other two tests, {@link RawCdiTest} and {@link RawHk2Test},
 * do the same job, because the WAR application contains both Jersey apps already.
 *
 * @author Jakub Podlesak
 */
public class CombinedTest {

    public static final String CDI_URI = "/cdi";
    public static final String HK2_URI = "/hk2";

    public static final String PORT_NUMBER = getSystemProperty(TestProperties.CONTAINER_PORT,
                                                    Integer.toString(TestProperties.DEFAULT_CONTAINER_PORT));

    private static final URI BASE_HK2_URI = URI.create("http://localhost:" + PORT_NUMBER + HK2_URI);
    private static final URI BASE_CDI_URI = URI.create("http://localhost:" + PORT_NUMBER + CDI_URI);

    private static final boolean isDefaultTestContainerFactorySet = isDefaultTestContainerFactorySet();

    Weld weld;
    HttpServer cdiServer;

    Client client;
    WebTarget cdiTarget, hk2Target;

    @Before
    public void before() throws IOException {
        if (isDefaultTestContainerFactorySet && Hk2InjectionManagerFactory.isImmediateStrategy()) {
            initializeWeld();
            startGrizzlyContainer();
            initializeClient();
        }
    }

    @Before
    public void beforeIsImmediate() {
        Assume.assumeTrue(Hk2InjectionManagerFactory.isImmediateStrategy());
    }

    @After
    public void after() {
        if (isDefaultTestContainerFactorySet && Hk2InjectionManagerFactory.isImmediateStrategy()) {
            cdiServer.shutdownNow();
            weld.shutdown();
            client.close();
        }
    }

    @Test
    public void testParamValidatedResourceNoParam() throws Exception {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testParamValidatedResourceNoParam(cdiTarget);
        BaseValidationTest._testParamValidatedResourceNoParam(hk2Target);
    }

    @Test
    public void testParamValidatedResourceParamProvided() throws Exception {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testParamValidatedResourceParamProvided(cdiTarget);
        BaseValidationTest._testParamValidatedResourceParamProvided(hk2Target);
    }

    @Test
    public void testFieldValidatedResourceNoParam() throws Exception {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testFieldValidatedResourceNoParam(cdiTarget);
        BaseValidationTest._testFieldValidatedResourceNoParam(hk2Target);
    }

    @Test
    public void testFieldValidatedResourceParamProvided() throws Exception {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testFieldValidatedResourceParamProvided(cdiTarget);
        BaseValidationTest._testFieldValidatedResourceParamProvided(hk2Target);
    }

    @Test
    public void testPropertyValidatedResourceNoParam() throws Exception {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testPropertyValidatedResourceNoParam(cdiTarget);
        BaseValidationTest._testPropertyValidatedResourceNoParam(hk2Target);
    }

    @Test
    public void testPropertyValidatedResourceParamProvided() throws Exception {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testPropertyValidatedResourceParamProvided(cdiTarget);
        BaseValidationTest._testPropertyValidatedResourceParamProvided(hk2Target);
    }

    @Test
    public void testOldFashionedResourceNoParam() {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testOldFashionedResourceNoParam(cdiTarget);
        BaseValidationTest._testOldFashionedResourceNoParam(hk2Target);
    }

    @Test
    public void testOldFashionedResourceParamProvided() throws Exception {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testOldFashionedResourceParamProvided(cdiTarget);
        BaseValidationTest._testOldFashionedResourceParamProvided(hk2Target);
    }

    @Test
    public void testNonJaxRsValidationFieldValidatedResourceNoParam() {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testNonJaxRsValidationFieldValidatedResourceNoParam(cdiTarget);
    }

    @Test
    public void testNonJaxRsValidationFieldValidatedResourceParamProvided() {
        Assume.assumeThat(isDefaultTestContainerFactorySet, CoreMatchers.is(true));
        BaseValidationTest._testNonJaxRsValidationFieldValidatedResourceParamProvided(cdiTarget);
    }

    private void initializeWeld() {
        weld = new Weld();
        weld.initialize();
    }

    private void startGrizzlyContainer() throws IOException {
        final ResourceConfig cdiConfig = ResourceConfig.forApplicationClass(CdiApplication.class);
        final ResourceConfig hk2Config = ResourceConfig.forApplicationClass(Hk2Application.class);

        cdiServer = GrizzlyHttpServerFactory.createHttpServer(BASE_CDI_URI, cdiConfig, false);
        final HttpHandler hk2Handler = createGrizzlyContainer(hk2Config);
        cdiServer.getServerConfiguration().addHttpHandler(hk2Handler, HK2_URI);
        cdiServer.start();
    }

    private void initializeClient() {
        client = ClientBuilder.newClient();
        cdiTarget = client.target(BASE_CDI_URI);
        hk2Target = client.target(BASE_HK2_URI);
    }

    private GrizzlyHttpContainer createGrizzlyContainer(ResourceConfig resourceConfig) {
        return (new GrizzlyHttpContainerProvider()).createContainer(GrizzlyHttpContainer.class, resourceConfig);
    }

    private static boolean isDefaultTestContainerFactorySet() {
        final String testContainerFactory = getSystemProperty(TestProperties.CONTAINER_FACTORY, null);
        return testContainerFactory == null || TestProperties.DEFAULT_CONTAINER_FACTORY.equals(testContainerFactory);
    }

    private static String getSystemProperty(final String propertyName, final String defaultValue) {
        final Properties systemProperties = System.getProperties();
        return systemProperties.getProperty(propertyName, defaultValue);
    }
}
