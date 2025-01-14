/*
 * Copyright (c) 2010, 2022 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.osgi.test.util;

import java.security.AccessController;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.glassfish.jersey.internal.util.JdkVersion;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.test.TestProperties;

import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

/**
 * Helper class to be used by individual tests.
 *
 * @author Jakub Podlesak
 * @author Michal Gajdos
 */
public class Helper {

    /**
     * Jersey HTTP port.
     */
    private static final int port = getEnvVariable(TestProperties.CONTAINER_PORT, 8080);

    /**
     * JAX-RS delegate property.
     */
    private static final String JAXRS_RUNTIME_DELEGATE_PROPERTY = "jakarta.ws.rs.ext.RuntimeDelegate";

    /**
     * JAX-RS Client Builder property
     */
    private static final String JAXRS_CLIENT_BUILDER = "jakarta.ws.rs.client.ClientBuilder";

    /**
     * Returns an integer value of given system property, or a default value
     * as defined by the other method parameter, if the system property can
     * not be used.
     *
     * @param varName      name of the system variable.
     * @param defaultValue the default value to return if the system variable is missing or can not be parsed as an integer.
     * @return an integer value taken either from the system property or the default value as defined by the defaultValue parameter.
     */
    public static int getEnvVariable(final String varName, int defaultValue) {
        if (null == varName) {
            return defaultValue;
        }
        String varValue = AccessController.doPrivileged(PropertiesHelper.getSystemProperty(varName));
        if (null != varValue) {
            try {
                return Integer.parseInt(varValue);
            } catch (NumberFormatException e) {
                // will return default value below
            }
        }
        return defaultValue;
    }

    /**
     * Returns a value of {@value TestProperties#CONTAINER_PORT} property which should be used as port number for test container.
     *
     * @return port number.
     */
    public static int getPort() {
        return port;
    }

    /**
     * Adds a system property for Maven local repository location to the PaxExam OSGi runtime if a "localRepository" property
     * is present in the map of the system properties.
     *
     * @param options list of options to add the local repository property to.
     * @return list of options enhanced by the local repository property if this property is set or the given list if the
     *         previous condition is not met.
     */
    public static List<Option> addPaxExamMavenLocalRepositoryProperty(List<Option> options) {
        final String localRepository = AccessController.doPrivileged(PropertiesHelper.getSystemProperty("localRepository"));

        if (localRepository != null) {
            options.addAll(expandedList(systemProperty("org.ops4j.pax.url.mvn.localRepository").value(localRepository)));
        }

        return options;
    }

    /**
     * Convert list of OSGi options to an array.
     *
     * @param options list of OSGi options.
     * @return array of OSGi options.
     */
    public static Option[] asArray(final List<Option> options) {
        return options.toArray(new Option[options.size()]);
    }

    /**
     * Create new list of common OSGi integration test options.
     *
     * @return list of common OSGi integration test options.
     */
    public static List<Option> getCommonOsgiOptions() {
        return getCommonOsgiOptions(true);
    }

    /**
     * Create new list of common OSGi integration test options.
     *
     * @param includeJerseyJaxRsLibs indicates whether JaxRs and Jersey bundles should be added into the resulting list of
     * options.
     * @return list of common OSGi integration test options.
     */
    public static List<Option> getCommonOsgiOptions(final boolean includeJerseyJaxRsLibs) {
        final List<Option> options = new LinkedList<Option>(expandedList(
                // systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("FINEST"),
                systemProperty("org.osgi.service.http.port").value(String.valueOf(port)),
                systemProperty(TestProperties.CONTAINER_PORT).value(String.valueOf(port)),
                systemProperty("org.osgi.framework.system.packages.extra").value("jakarta.annotation"),
                systemProperty(JAXRS_RUNTIME_DELEGATE_PROPERTY).value("org.glassfish.jersey.internal.RuntimeDelegateImpl"),
                systemProperty(JAXRS_CLIENT_BUILDER).value("org.glassfish.jersey.client.JerseyClientBuilder"),

                // jakarta.annotation has to go first!
                mavenBundle().groupId("jakarta.annotation").artifactId("jakarta.annotation-api").versionAsInProject(),
                mavenBundle().groupId("jakarta.activation").artifactId("jakarta.activation-api").versionAsInProject(),
                mavenBundle().groupId("jakarta.inject").artifactId("jakarta.inject-api").versionAsInProject(),
                mavenBundle().groupId("jakarta.xml.bind").artifactId("jakarta.xml.bind-api").versionAsInProject(),
                junitBundles(),

                // HK2
                mavenBundle().groupId("org.glassfish.hk2").artifactId("hk2-api").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2").artifactId("osgi-resource-locator").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2").artifactId("hk2-locator").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2").artifactId("hk2-utils").versionAsInProject(),
                //  mavenBundle().groupId("org.glassfish.hk2.external").artifactId("jakarta.inject").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.hk2.external").artifactId("aopalliance-repackaged").versionAsInProject(),
                mavenBundle().groupId("org.javassist").artifactId("javassist").versionAsInProject(),

                // Grizzly
                systemPackage("sun.misc"),
                mavenBundle().groupId("org.glassfish.grizzly").artifactId("grizzly-framework").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.grizzly").artifactId("grizzly-http").versionAsInProject(),
                mavenBundle().groupId("org.glassfish.grizzly").artifactId("grizzly-http-server").versionAsInProject(),

                // jakarta.validation
                mavenBundle().groupId("jakarta.validation").artifactId("jakarta.validation-api").versionAsInProject(),

                // Jersey Grizzly
                mavenBundle().groupId("org.glassfish.jersey.containers").artifactId("jersey-container-grizzly2-http")
                        .versionAsInProject()
        ));

        if (includeJerseyJaxRsLibs) {
            options.addAll(expandedList(
                    // JAX-RS API
                    mavenBundle().groupId("jakarta.ws.rs").artifactId("jakarta.ws.rs-api").versionAsInProject(),

                    // Jersey bundles
                    mavenBundle().groupId("org.glassfish.jersey.core").artifactId("jersey-common").versionAsInProject(),
                    mavenBundle().groupId("org.glassfish.jersey.media").artifactId("jersey-media-jaxb").versionAsInProject(),
                    mavenBundle().groupId("org.glassfish.jersey.core").artifactId("jersey-server").versionAsInProject(),
                    mavenBundle().groupId("org.glassfish.jersey.core").artifactId("jersey-client").versionAsInProject(),

                    // Jersey Injection provider
                    mavenBundle().groupId("org.glassfish.jersey.inject").artifactId("jersey-hk2").versionAsInProject()
//                     Jaxb - api
//                    getActivationBundle()
            ));
        }

        return addPaxExamMavenLocalRepositoryProperty(options);
    }

    private static Option getActivationBundle() {
        return JdkVersion.getJdkVersion().getMajor() > 8
                ? mavenBundle().groupId("com.sun.activation").artifactId("jakarta.activation").versionAsInProject()
                : null;
    }

    /**
     * Create expanded options list from the supplied options.
     *
     * @param options options to be expanded into the option list.
     * @return expanded options list.
     */
    public static List<Option> expandedList(Option... options) {
        return Arrays.asList(options(options));
    }
}
