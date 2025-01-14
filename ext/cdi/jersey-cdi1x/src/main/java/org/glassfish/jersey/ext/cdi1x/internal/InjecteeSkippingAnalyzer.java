/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.ext.cdi1x.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.glassfish.jersey.internal.util.collection.Views;

import org.glassfish.hk2.api.ClassAnalyzer;
import org.glassfish.hk2.api.MultiException;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

/**
 * Class analyzer that ignores given injection points.
 * Used for CDI integration, where we need to avoid HK2 replacing CDI injected entities.
 *
 * @author Jakub Podlesak
 */
public final class InjecteeSkippingAnalyzer implements ClassAnalyzer {

    private final ClassAnalyzer defaultAnalyzer;
    private final Map<Class<?>, Set<Method>> methodsToSkip;
    private final Map<Class<?>, Set<Field>> fieldsToSkip;
    private final BeanManager beanManager;
    private final CdiComponentProvider cdiComponentProvider;

    public InjecteeSkippingAnalyzer(ClassAnalyzer defaultAnalyzer,
                                    Map<Class<?>, Set<Method>> methodsToSkip,
                                    Map<Class<?>, Set<Field>> fieldsToSkip,
                                    BeanManager beanManager) {
        this.defaultAnalyzer = defaultAnalyzer;
        this.methodsToSkip = methodsToSkip;
        this.fieldsToSkip = fieldsToSkip;
        this.beanManager = beanManager;
        this.cdiComponentProvider = beanManager.getExtension(CdiComponentProvider.class);
    }

    @Override
    public <T> Constructor<T> getConstructor(Class<T> type) throws MultiException, NoSuchMethodException {
        throw new IllegalStateException(LocalizationMessages.CDI_CLASS_ANALYZER_MISUSED());
    }

    @Override
    public <T> Set<Method> getInitializerMethods(Class<T> type) throws MultiException {
        final Set<Method> originalMethods = defaultAnalyzer.getInitializerMethods(type);
        final Set<Method> skippedMethods = getMembersToSkip(type, methodsToSkip);
        return Views.setDiffView(originalMethods, skippedMethods);
    }

    @Override
    public <T> Set<Field> getFields(Class<T> type) throws MultiException {
        final Set<Field> originalFields = defaultAnalyzer.getFields(type);
        final Set<Field> skippedFields = getMembersToSkip(type, fieldsToSkip);
        addCdiInjectedFieldsToSkip(skippedFields, originalFields);
        return Views.setDiffView(originalFields, skippedFields);
    }

    @Override
    public <T> Method getPostConstructMethod(Class<T> type) throws MultiException {
        throw new IllegalStateException(LocalizationMessages.CDI_CLASS_ANALYZER_MISUSED());
    }

    @Override
    public <T> Method getPreDestroyMethod(Class<T> type) throws MultiException {
        throw new IllegalStateException(LocalizationMessages.CDI_CLASS_ANALYZER_MISUSED());
    }

    private <M extends Member> Set<M> getMembersToSkip(final Class<?> type, final Map<Class<?>, Set<M>> skippedMembers) {

        final Set<M> directResult = skippedMembers.get(type);

        if (directResult != null) {
            return directResult;
        }

        // fallback for GLASSFISH-20255
        final Set<M> compositeResult = new HashSet<>();
        for (Entry<Class<?>, Set<M>> type2Method : skippedMembers.entrySet()) {

            if (type2Method.getKey().isAssignableFrom(type)) {
                compositeResult.addAll(type2Method.getValue());
            }
        }

        return compositeResult;
    }

    private void addCdiInjectedFieldsToSkip(Set<Field> skippedFields, Set<Field> originalFields) {
        for (Field field : originalFields) {
            if (field.getAnnotation(Inject.class) != null && !cdiComponentProvider.isHk2ProvidedType(field.getType())) {
                skippedFields.add(field);
            }
        }
    }
}
