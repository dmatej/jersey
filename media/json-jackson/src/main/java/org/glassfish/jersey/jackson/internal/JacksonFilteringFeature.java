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

package org.glassfish.jersey.jackson.internal;

import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.GenericType;

import jakarta.inject.Singleton;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.message.filtering.spi.ObjectGraphTransformer;
import org.glassfish.jersey.message.filtering.spi.ObjectProvider;

import com.fasterxml.jackson.databind.ser.FilterProvider;

/**
 * {@link Feature} adding support for Entity Data Filtering into Jackson media module.
 *
 * @author Michal Gajdos
 */
public final class JacksonFilteringFeature implements Feature {

    @Override
    public boolean configure(final FeatureContext context) {
        final Configuration config = context.getConfiguration();

        if (!config.isRegistered(JacksonFilteringFeature.Binder.class)) {
            context.register(new Binder());
            return true;
        }
        return false;
    }

    private static final class Binder extends AbstractBinder {

        @Override
        protected void configure() {
            bindAsContract(JacksonObjectProvider.class)
                    // FilteringObjectProvider.
                    .to(new GenericType<ObjectProvider<FilterProvider>>() {})
                    // FilteringGraphTransformer.
                    .to(new GenericType<ObjectGraphTransformer<FilterProvider>>() {})
                    // Scope.
                    .in(Singleton.class);
        }
    }
}
