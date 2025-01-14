/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.inject.weld.internal.bean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.ws.rs.RuntimeType;

import org.glassfish.jersey.inject.weld.internal.injector.JerseyInjectionTarget;
import org.glassfish.jersey.internal.inject.ClassBinding;

/**
 * Creates an implementation of {@link jakarta.enterprise.inject.spi.Bean} interface using Jersey's {@link ClassBinding}. Binding
 * provides the information about the bean also called {@link jakarta.enterprise.inject.spi.BeanAttributes} information and
 * {@link JerseyInjectionTarget} provides the contextual part of the bean because implements
 * {@link jakarta.enterprise.context.spi.Contextual} with Jersey injection extension (is able to inject into JAX-RS/Jersey specified
 * annotation).
 * <p>
 * Inject example:
 * <pre>
 * AbstractBinder {
 *     &#64;Override
 *     protected void configure() {
 *         bind(MyBean.class)
 *              .to(MyBean.class)
 *              .in(Singleton.class)&#59;
 *     }
 * }
 * </pre>
 * Register example:
 * <pre>
 *  &#64;Path("/")
 *  public class MyResource {
 *    &#64;Inject
 *    private MyBean myBean&#59;
 *  }
 * </pre>
 *
 * @author Petr Bouda
 */
class ClassBean<T> extends JerseyBean<T> {

    private final ClassBinding<T> binding;
    private InjectionTarget<T> injectionTarget;

    /**
     * Creates a new Jersey-specific {@link jakarta.enterprise.inject.spi.Bean} instance.
     * @param runtimeType {@link RuntimeType} type information of the bean source.
     * @param binding the binding information.
     */
    ClassBean(RuntimeType runtimeType, ClassBinding<T> binding) {
        super(runtimeType, binding);
        this.binding = binding;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        /*
         * Resource class without the Scope annotation should registered as a RequestScoped.
         */
        if (binding.getScope() == null && BeanHelper.isResourceClass(binding.getService())) {
            return RequestScoped.class;
        }

        return binding.getScope() == null ? Dependent.class : transformScope(binding.getScope());
    }

    @Override
    @SuppressWarnings("unchecked")
    public T create(CreationalContext<T> context) {
        T instance = injectionTarget.produce(context);
        injectionTarget.inject(instance, context);
        injectionTarget.postConstruct(instance);
        return instance;
    }

    @Override
    public void destroy(T instance, CreationalContext<T> context) {
        injectionTarget.preDestroy(instance);
        injectionTarget.dispose(instance);
        context.release();
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> contracts = super.getTypes();
        contracts.addAll(Arrays.asList(binding.getService().getInterfaces()));
        return contracts;
    }

    @Override
    public Class<?> getBeanClass() {
        return binding.getService();
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return injectionTarget.getInjectionPoints();
    }

    /**
     * Lazy set of an injection target because to create fully functional injection target needs already created bean.
     *
     * @param injectionTarget {@link jakarta.enterprise.context.spi.Contextual} information belonging to this bean.
     */
    void setInjectionTarget(InjectionTarget<T> injectionTarget) {
        this.injectionTarget = injectionTarget;
    }

    @Override
    public String toString() {
        return "ClassBean{" + getBeanClass() + "(" + getRutimeType() + ")}";
    }

    public InjectionTarget<T> getInjectionTarget() {
        return injectionTarget;
    }
}
