/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.microprofile.restclient;

import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Filters out all interfaces annotated with {@link RegisterRestClient}
 * and creates new Producer from each of these selected interfaces.
 *
 * Also adds support for injection of rest client instances to fields
 * without {@link RestClient} annotation.
 *
 * @author David Kral
 */
public class RestClientExtension implements Extension {

    private Set<Class<?>> interfaces = new HashSet<>();

    /**
     * Filters out all interfaces annotated with {@link RegisterRestClient} annotation and
     * adds them to the collection for further processing.
     *
     * @param processAnnotatedType filtered annotated types
     */
    public void collectClientRegistrations(@Observes
                                           @WithAnnotations({RegisterRestClient.class})
                                                   ProcessAnnotatedType<?> processAnnotatedType) {
        Class<?> typeDef = processAnnotatedType.getAnnotatedType().getJavaClass();
        if (typeDef.isInterface()) {
            interfaces.add(typeDef);
        } else {
            throw new DeploymentException("RegisterRestClient annotation has to be on interface! " + typeDef + " is not "
                    + "interface.");
        }
    }

    /**
     * Creates new producers based on collected interfaces.
     *
     * @param abd after bean discovery instance
     * @param bm bean manager instance
     */
    public void restClientRegistration(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        interfaces.forEach(type -> abd.addBean(new RestClientProducer(type, bm)));
    }

}
