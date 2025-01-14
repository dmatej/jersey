/*
 * Copyright (c) 2012, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.server.internal;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.container.ResourceContext;

import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import org.glassfish.jersey.internal.inject.Binding;
import org.glassfish.jersey.internal.inject.Bindings;
import org.glassfish.jersey.internal.inject.ClassBinding;
import org.glassfish.jersey.internal.inject.CustomAnnotationLiteral;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.internal.util.ReflectionHelper;
import org.glassfish.jersey.model.ContractProvider;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ExtendedResourceContext;
import org.glassfish.jersey.server.model.ResourceModel;

/**
 * Jersey implementation of JAX-RS {@link ResourceContext resource context}.
 *
 * @author Marek Potociar
 */
public class JerseyResourceContext implements ExtendedResourceContext {

    private final Function<Class<?>, ?> getOrCreateInstance;
    private final Consumer<Object> injectInstance;
    private final Consumer<Binding> registerBinding;

    private final Set<Class<?>> bindingCache;
    private final Object bindingCacheLock;

    private volatile ResourceModel resourceModel;

    /**
     * Creates a new JerseyResourceContext.
     *
     * @param getOrCreateInstance function to create or get existing instance.
     * @param injectInstance      consumer to inject instances into an unmanaged instance.
     * @param registerBinding     consumer to register a new binding into injection manager.
     */
    public JerseyResourceContext(
            Function<Class<?>, ?> getOrCreateInstance,
            Consumer<Object> injectInstance,
            Consumer<Binding> registerBinding) {
        this.getOrCreateInstance = getOrCreateInstance;
        this.injectInstance = injectInstance;
        this.registerBinding = registerBinding;
        this.bindingCache = Collections.newSetFromMap(new IdentityHashMap<>());
        this.bindingCacheLock = new Object();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getResource(Class<T> resourceClass) {
        try {
            return (T) getOrCreateInstance.apply(resourceClass);
        } catch (Exception ex) {
            Logger.getLogger(JerseyResourceContext.class.getName()).log(Level.WARNING,
                    LocalizationMessages.RESOURCE_LOOKUP_FAILED(resourceClass), ex);
        }
        return null;
    }

    @Override
    public <T> T initResource(T resource) {
        injectInstance.accept(resource);
        return resource;
    }

    /**
     * Binds {@code resourceClass} into HK2 context as singleton.
     *
     * The bound class is then cached internally so that any sub-sequent attempt to bind that class
     * as a singleton is silently ignored.
     *
     * @param <T>           type of the resource class.
     * @param resourceClass resource class that should be bound. If the class is not
     *                      annotated with {@link jakarta.inject.Singleton Singleton annotation} it
     *                      will be ignored by this method.
     */
    public <T> void bindResource(Class<T> resourceClass) {
        if (bindingCache.contains(resourceClass)) {
            return;
        }

        synchronized (bindingCacheLock) {
            if (bindingCache.contains(resourceClass)) {
                return;
            }
            unsafeBindResource(resourceClass, null);
        }
    }

    /**
     * Binds {@code resourceClass} into HK2 context as singleton.
     *
     * The bound class is then cached internally so that any sub-sequent attempt to bind that class
     * as a singleton is silently ignored.
     *
     * @param resource singleton resource instance that should be bound as singleton. If the class is not
     *                 annotated with {@link jakarta.inject.Singleton Singleton annotation} it
     *                 will be ignored by this method.
     */
    @SuppressWarnings("unchecked")
    public <T> void bindResourceIfSingleton(T resource) {
        final Class<?> resourceClass = resource.getClass();
        if (bindingCache.contains(resourceClass)) {
            return;
        }

        synchronized (bindingCacheLock) {
            if (bindingCache.contains(resourceClass)) {
                return;
            }
            if (getScope(resourceClass) == Singleton.class) {
                registerBinding.accept(Bindings.service(resource).to((Class<? super T>) resourceClass));
            }

            bindingCache.add(resourceClass);
        }
    }

    /**
     * Bind a resource instance in a InjectionManager.
     *
     * The bound resource instance is internally cached to make sure any sub-sequent attempts to service the
     * class are silently ignored.
     * <p>
     * WARNING: This version of method is not synchronized as well as the cache is not checked for existing
     * bindings before the resource is bound and cached.
     * </p>
     *
     * @param resource resource instance to be bound.
     * @param providerModel provider model for the resource class. If not {@code null}, the class
     *                      wil be bound as a contract provider too.
     */
    public void unsafeBindResource(Object resource, ContractProvider providerModel) {
        Binding binding;
        Class<?> resourceClass = resource.getClass();
        if (providerModel != null) {
            Class<? extends Annotation> scope = providerModel.getScope();
            binding = Bindings.service(resource).to(resourceClass);

            for (Class contract : Providers.getProviderContracts(resourceClass)) {
                binding.addAlias(contract)
                        .in(scope.getName())
                        .qualifiedBy(CustomAnnotationLiteral.INSTANCE);
            }
        } else {
            binding = Bindings.serviceAsContract(resourceClass);
        }
        registerBinding.accept(binding);
        bindingCache.add(resourceClass);
    }

    private static Class<? extends Annotation> getScope(Class<?> resourceClass) {
        final Collection<Class<? extends Annotation>> scopes =
                ReflectionHelper.getAnnotationTypes(resourceClass, Scope.class);

        return scopes.isEmpty() ? RequestScoped.class : scopes.iterator().next();
    }

    /**
     * Bind a resource class in a HK2 context.
     *
     * The bound resource class is internally cached to make sure any sub-sequent attempts to bind the
     * class are silently ignored.
     * <p>
     * WARNING: This version of method is not synchronized as well as the cache is not checked for existing
     * bindings before the resource is bound and cached.
     * </p>
     *
     * @param <T>           resource class type.
     * @param resourceClass resource class to be bound.
     * @param providerModel provider model for the class. If not {@code null}, the class
     *                      wil be bound as a contract provider too.
     */
    public <T> void unsafeBindResource(Class<T> resourceClass, ContractProvider providerModel) {
        ClassBinding<T> descriptor;
        if (providerModel != null) {
            Class<? extends Annotation> scope = providerModel.getScope();
            descriptor = Bindings.serviceAsContract(resourceClass).in(scope);

            for (Class contract : providerModel.getContracts()) {
                descriptor.addAlias(contract)
                        .in(scope.getName())
                        .ranked(providerModel.getPriority(contract))
                        .qualifiedBy(CustomAnnotationLiteral.INSTANCE);
            }
        } else {
            descriptor = Bindings.serviceAsContract(resourceClass).in(getScope(resourceClass));
        }
        registerBinding.accept(descriptor);
        bindingCache.add(resourceClass);
    }

    @Override
    public ResourceModel getResourceModel() {
        return this.resourceModel;
    }

    /**
     * Set the {@link ResourceModel resource mode} of the application associated with this context.
     * @param resourceModel Resource model on which the {@link org.glassfish.jersey.server.ApplicationHandler application}
     *                      is based.
     */
    public void setResourceModel(ResourceModel resourceModel) {
        this.resourceModel = resourceModel;
    }
}
