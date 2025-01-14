/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 Payara Foundation and/or its affiliates. All rights reserved.
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
package org.glassfish.jersey.client;


import org.glassfish.jersey.internal.BootstrapBag;
import org.glassfish.jersey.client.inject.ParameterUpdaterProvider;
import org.glassfish.jersey.internal.util.collection.LazyValue;
import org.glassfish.jersey.spi.ComponentProvider;

import jakarta.ws.rs.core.GenericType;
import java.util.Collection;

/**
 * {@inheritDoc}
 * <p>
 * This bootstrap bag is specialized for client part of Jersey.
 *
 * @author Gaurav Gupta (gaurav.gupta@payara.fish)
 */
public class ClientBootstrapBag extends BootstrapBag {

    private ParameterUpdaterProvider parameterUpdaterProvider;
    private LazyValue<Collection<ComponentProvider>> componentProviders;

    public ParameterUpdaterProvider getParameterUpdaterProvider() {
        requireNonNull(parameterUpdaterProvider, ParameterUpdaterProvider.class);
        return parameterUpdaterProvider;
    }

    public void setParameterUpdaterProvider(ParameterUpdaterProvider provider) {
        this.parameterUpdaterProvider = provider;
    }

    /**
     * Gets a lazy initialized collection of {@link ComponentProvider}s.
     *
     * @return A lazy initialized collection of {@code ComponentProvider}s.
     */
    LazyValue<Collection<ComponentProvider>> getComponentProviders() {
        requireNonNull(componentProviders, new GenericType<LazyValue<Collection<ComponentProvider>>>() {}.getType());
        return componentProviders;
    }

    /**
     * Sets a lazy initialized collection of {@link ComponentProvider}s.
     *
     * @param componentProviders A lazy initialized collection of {@code ComponentProvider}s.
     */
    void setComponentProviders(LazyValue<Collection<ComponentProvider>> componentProviders) {
        this.componentProviders = componentProviders;
    }
}
