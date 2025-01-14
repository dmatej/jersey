/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.inject.cdi.se;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.Provider;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;
import jakarta.enterprise.inject.spi.WithAnnotations;

import org.glassfish.jersey.inject.cdi.se.bean.BeanHelper;
import org.glassfish.jersey.inject.cdi.se.injector.JerseyInjectionTarget;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.Binding;
import org.glassfish.jersey.internal.inject.ClassBinding;
import org.glassfish.jersey.internal.inject.InjectionResolver;
import org.glassfish.jersey.internal.inject.InjectionResolverBinding;
import org.glassfish.jersey.internal.inject.InstanceBinding;
import org.glassfish.jersey.internal.inject.SupplierClassBinding;
import org.glassfish.jersey.internal.inject.SupplierInstanceBinding;

import org.jboss.weld.injection.producer.BasicInjectionTarget;

/**
 * CDI extension that handles CDI bootstrap events and registers Jersey's internally used components and components registered
 * using {@link Application}.
 *
 * @author Petr Bouda
 */
class SeBeanRegisterExtension implements Extension {

    private final AbstractBinder bindings;

    private final List<JerseyInjectionTarget> jerseyInjectionTargets = new ArrayList<>();

    /**
     * Creates a new extension that registers Jersey's internally used components and components registered using
     * {@link Application}.
     * <p>
     * TODO: Probably will be changed during the migration from CDI SE to JAVA EE environment.
     *
     * @param bindings all register beans using Jersey.
     */
    SeBeanRegisterExtension(AbstractBinder bindings) {
        this.bindings = bindings;
    }

    /**
     * Ignores the classes which are manually added using bindings (through {@link Application} class) and scanned by CDI.
     * The manual adding is privileged and the beans scanned using CDI are ignored.
     * <p>
     * TODO: The method counts with the condition that the all bindings are known before the CDI scanning has been started,
     * can be changed during the migration from CDI SE to JAVA EE environment.
     *
     * @param pat processed type.
     * @param <T> type of the scanned bean.
     */
    public <T> void ignoreManuallyRegisteredComponents(
            @Observes @WithAnnotations({ Path.class, Provider.class }) ProcessAnnotatedType<T> pat) {
        for (Binding binding : bindings.getBindings()) {
            if (ClassBinding.class.isAssignableFrom(binding.getClass())) {
                ClassBinding<?> classBinding = (ClassBinding<?>) binding;
                if (pat.getAnnotatedType().getJavaClass() == classBinding.getService()) {
                    pat.veto();
                    return;
                }
            } else if (InstanceBinding.class.isAssignableFrom(binding.getClass())) {
                InstanceBinding<?> instanceBinding = (InstanceBinding<?>) binding;
                if (pat.getAnnotatedType().getJavaClass() == instanceBinding.getService().getClass()) {
                    pat.veto();
                    return;
                }
            }
        }
    }

    /**
     * Wraps all JAR-RS components by Jersey-specific injection target.
     *
     * @param pit process injection target.
     * @param <T> type of the processed injection target.
     */
    public <T> void observeInjectionTarget(@Observes ProcessInjectionTarget<T> pit) {
        BasicInjectionTarget<T> it = (BasicInjectionTarget<T>) pit.getInjectionTarget();
        JerseyInjectionTarget<T> jerseyInjectionTarget =
                new JerseyInjectionTarget<>(it, pit.getAnnotatedType().getJavaClass());
        jerseyInjectionTargets.add(jerseyInjectionTarget);
        pit.setInjectionTarget(jerseyInjectionTarget);
    }

    /**
     * Takes all registered bindings and registers them in {@link BeanManager}.
     * <p>
     * Method should register only Jersey internal components and class/instances registered using {@link Application}. Registered
     * classes/instances have priority therefore CDI scanning should veto these classes/instances during {
     *
     * @param abd         {@code AfterBeanDiscovery} event.
     * @param beanManager current {@code BeanManager}.
     * @link ProcessAnnotatedType} bootstrap phase.
     */
    public void registerBeans(@Observes AfterBeanDiscovery abd, BeanManager beanManager) {
        Collection<Binding> bindings = this.bindings.getBindings();

        /*
         * Filters out only registered InjectionResolvers.
         */
        List<InjectionResolver> injectionResolvers = bindings.stream()
                .filter(binding -> InjectionResolverBinding.class.isAssignableFrom(binding.getClass()))
                .map(InjectionResolverBinding.class::cast)
                .map(InjectionResolverBinding::getResolver)
                .collect(Collectors.toList());

        /*
         * Provide registered InjectionResolvers to Jersey's components which has been discovered by CDI in
         * ProcessInjectionTarget bootstrap phase.
         */
        jerseyInjectionTargets.forEach(injectionTarget -> injectionTarget.setInjectionResolvers(injectionResolvers));

        for (Binding binding : bindings) {
            if (ClassBinding.class.isAssignableFrom(binding.getClass())) {
                BeanHelper.registerBean((ClassBinding<?>) binding, abd, injectionResolvers, beanManager);

            } else if (InstanceBinding.class.isAssignableFrom(binding.getClass())) {
                BeanHelper.registerBean((InstanceBinding<?>) binding, abd, injectionResolvers);

            } else if (SupplierClassBinding.class.isAssignableFrom(binding.getClass())) {
                BeanHelper.registerSupplier((SupplierClassBinding<?>) binding, abd, injectionResolvers, beanManager);

            } else if (SupplierInstanceBinding.class.isAssignableFrom(binding.getClass())) {
                BeanHelper.registerSupplier((SupplierInstanceBinding<?>) binding, abd, beanManager);
            }
        }

        abd.addBean(new RequestScopeBean(beanManager));
    }
}
