/*
 * Copyright (c) 2011, 2021 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.internal;

import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.core.Application;

import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.glassfish.jersey.message.internal.MessagingBinders;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of JAX-RS {@link jakarta.ws.rs.ext.RuntimeDelegate}.
 * The {@link jakarta.ws.rs.ext.RuntimeDelegate} class looks for the implementations registered
 * in META-INF/services. Server injection binder should override this (using META-INF/services)
 * to provide an implementation that supports {@link #createEndpoint(jakarta.ws.rs.core.Application, java.lang.Class)}
 * method.
 *
 * @author Jakub Podlesak
 * @author Marek Potociar
 * @author Martin Matula
 */
public class RuntimeDelegateImpl extends AbstractRuntimeDelegate {

    public RuntimeDelegateImpl() {
        super(new MessagingBinders.HeaderDelegateProviders().getHeaderDelegateProviders());
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType)
            throws IllegalArgumentException, UnsupportedOperationException {

        final RuntimeDelegate runtimeDelegate = findServerDelegate();
        if (runtimeDelegate != null) {
            return runtimeDelegate.createEndpoint(application, endpointType);
        }
        throw new UnsupportedOperationException(LocalizationMessages.NO_CONTAINER_AVAILABLE());
    }

    @Override
    public SeBootstrap.Configuration.Builder createConfigurationBuilder() {
        final RuntimeDelegate runtimeDelegate = findServerDelegate();
        if (runtimeDelegate != null) {
            return runtimeDelegate.createConfigurationBuilder();
        }
        throw new UnsupportedOperationException(LocalizationMessages.NO_CONTAINER_AVAILABLE());
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Application application, SeBootstrap.Configuration configuration) {
        final RuntimeDelegate runtimeDelegate = findServerDelegate();
        if (runtimeDelegate != null) {
            return runtimeDelegate.bootstrap(application, configuration);
        }
        throw new UnsupportedOperationException(LocalizationMessages.NO_CONTAINER_AVAILABLE());
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Class<? extends Application> applicationClass,
                                                           SeBootstrap.Configuration configuration) {
        final RuntimeDelegate runtimeDelegate = findServerDelegate();
        if (runtimeDelegate != null) {
            return runtimeDelegate.bootstrap(applicationClass, configuration);
        }
        throw new UnsupportedOperationException(LocalizationMessages.NO_CONTAINER_AVAILABLE());
    }

    // TODO : Do we need multiple RuntimeDelegates?
    private RuntimeDelegate findServerDelegate() {
        for (RuntimeDelegate delegate : ServiceFinder.find(RuntimeDelegate.class)) {
            // try to find runtime delegate from core-server
            if (delegate.getClass() != RuntimeDelegateImpl.class) {
                return delegate;
            }
        }
        return null;
    }
}
