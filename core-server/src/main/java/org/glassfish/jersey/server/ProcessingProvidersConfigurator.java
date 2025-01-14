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

package org.glassfish.jersey.server;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ws.rs.NameBinding;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.WriterInterceptor;

import org.glassfish.jersey.internal.BootstrapBag;
import org.glassfish.jersey.internal.BootstrapConfigurator;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.internal.util.ReflectionHelper;
import org.glassfish.jersey.model.ContractProvider;
import org.glassfish.jersey.model.internal.ComponentBag;
import org.glassfish.jersey.model.internal.RankedProvider;
import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.glassfish.jersey.server.internal.ProcessingProviders;

/**
 * Configurator which initializes and register {@link ProcessingProviders} instance into {@link BootstrapBag}.
 * Instances of these interfaces are processed, configured and provided using this configurator:
 * <ul>
 * <li>{@link ContainerRequestFilter}</li>
 * <li>{@link ContainerResponseFilter}</li>
 * <li>{@link ReaderInterceptor}</li>
 * <li>{@link WriterInterceptor}</li>
 * <li>{@link DynamicFeature}</li>
 * </ul>
 *
 * @author Petr Bouda
 */
class ProcessingProvidersConfigurator implements BootstrapConfigurator {

    private static final Logger LOGGER = Logger.getLogger(ProcessingProvidersConfigurator.class.getName());

    @Override
    public void init(InjectionManager injectionManager, BootstrapBag bootstrapBag) {
    }

    @Override
    public void postInit(InjectionManager injectionManager, BootstrapBag bootstrapBag) {
        ServerBootstrapBag serverBag = (ServerBootstrapBag) bootstrapBag;

        ComponentBag componentBag = serverBag.getRuntimeConfig().getComponentBag();
        // scan for NameBinding annotations attached to the application class
        Collection<Class<? extends Annotation>> applicationNameBindings = ReflectionHelper.getAnnotationTypes(
                ResourceConfig.unwrapApplication(serverBag.getRuntimeConfig()).getClass(), NameBinding.class);

        MultivaluedMap<RankedProvider<ContainerResponseFilter>, Class<? extends Annotation>> nameBoundRespFiltersInverse =
                new MultivaluedHashMap<>();
        MultivaluedMap<RankedProvider<ContainerRequestFilter>, Class<? extends Annotation>> nameBoundReqFiltersInverse =
                new MultivaluedHashMap<>();
        MultivaluedMap<RankedProvider<ReaderInterceptor>, Class<? extends Annotation>> nameBoundReaderInterceptorsInverse =
                new MultivaluedHashMap<>();
        MultivaluedMap<RankedProvider<WriterInterceptor>, Class<? extends Annotation>> nameBoundWriterInterceptorsInverse =
                new MultivaluedHashMap<>();

        Iterable<RankedProvider<ContainerResponseFilter>> responseFilters =
                Providers.getAllRankedProviders(injectionManager, ContainerResponseFilter.class);

        MultivaluedMap<Class<? extends Annotation>, RankedProvider<ContainerResponseFilter>> nameBoundResponseFilters =
                filterNameBound(responseFilters, null, componentBag, applicationNameBindings, nameBoundRespFiltersInverse);

        Iterable<RankedProvider<ContainerRequestFilter>> requestFilters =
                Providers.getAllRankedProviders(injectionManager, ContainerRequestFilter.class);

        List<RankedProvider<ContainerRequestFilter>> preMatchFilters = new ArrayList<>();

        MultivaluedMap<Class<? extends Annotation>, RankedProvider<ContainerRequestFilter>> nameBoundReqFilters =
                filterNameBound(requestFilters, preMatchFilters, componentBag, applicationNameBindings,
                        nameBoundReqFiltersInverse);

        Iterable<RankedProvider<ReaderInterceptor>> readerInterceptors =
                Providers.getAllRankedProviders(injectionManager, ReaderInterceptor.class);

        MultivaluedMap<Class<? extends Annotation>, RankedProvider<ReaderInterceptor>> nameBoundReaderInterceptors =
                filterNameBound(readerInterceptors, null, componentBag, applicationNameBindings,
                        nameBoundReaderInterceptorsInverse);

        Iterable<RankedProvider<WriterInterceptor>> writerInterceptors =
                Providers.getAllRankedProviders(injectionManager, WriterInterceptor.class);

        MultivaluedMap<Class<? extends Annotation>, RankedProvider<WriterInterceptor>> nameBoundWriterInterceptors =
                filterNameBound(writerInterceptors, null, componentBag, applicationNameBindings,
                        nameBoundWriterInterceptorsInverse);

        Iterable<DynamicFeature> dynamicFeatures = Providers.getAllProviders(injectionManager, DynamicFeature.class);

        ProcessingProviders processingProviders = new ProcessingProviders(nameBoundReqFilters, nameBoundReqFiltersInverse,
                nameBoundResponseFilters, nameBoundRespFiltersInverse, nameBoundReaderInterceptors,
                nameBoundReaderInterceptorsInverse, nameBoundWriterInterceptors, nameBoundWriterInterceptorsInverse,
                requestFilters, preMatchFilters, responseFilters, readerInterceptors, writerInterceptors, dynamicFeatures);

        serverBag.setProcessingProviders(processingProviders);
    }

    /**
     * Takes collection of all filters/interceptors (either request/reader or response/writer)
     * and separates out all name-bound filters/interceptors, returns them as a separate MultivaluedMap,
     * mapping the name-bound annotation to the list of name-bound filters/interceptors. The same key values
     * are also added into the inverse map passed in {@code inverseNameBoundMap}.
     * <p/>
     * Note, the name-bound filters/interceptors are removed from the original filters/interceptors collection.
     * If non-null collection is passed in the postMatching parameter (applicable for filters only),
     * this method also removes all the global
     * postMatching filters from the original collection and adds them to the collection passed in the postMatching
     * parameter.
     *
     * @param all                     Collection of all filters to be processed.
     * @param preMatchingFilters      Collection into which pre-matching filters should be added.
     * @param componentBag            Component bag
     * @param applicationNameBindings Collection of name binding annotations attached to the JAX-RS application.
     * @param inverseNameBoundMap     Inverse name bound map into which the name bound providers should be inserted. The keys
     *                                are providers (filters, interceptor)
     * @return {@link MultivaluedMap} of all name-bound filters.
     */
    private static <T> MultivaluedMap<Class<? extends Annotation>, RankedProvider<T>> filterNameBound(
            final Iterable<RankedProvider<T>> all,
            final Collection<RankedProvider<ContainerRequestFilter>> preMatchingFilters,
            final ComponentBag componentBag,
            final Collection<Class<? extends Annotation>> applicationNameBindings,
            final MultivaluedMap<RankedProvider<T>, Class<? extends Annotation>> inverseNameBoundMap) {

        final MultivaluedMap<Class<? extends Annotation>, RankedProvider<T>> result
                = new MultivaluedHashMap<>();

        for (final Iterator<RankedProvider<T>> it = all.iterator(); it.hasNext(); ) {
            final RankedProvider<T> provider = it.next();
            Class<?> providerClass = provider.getProvider().getClass();
            final Set<Type> contractTypes = provider.getContractTypes();
            if (contractTypes != null && !contractTypes.contains(providerClass)) {
                providerClass = ReflectionHelper.theMostSpecificTypeOf(contractTypes);
            }

            ContractProvider model = componentBag.getModel(providerClass);
            if (model == null) {
                // the provider was (most likely) bound in HK2 externally
                model = ComponentBag.modelFor(providerClass);
            }

            final boolean preMatching = providerClass.getAnnotation(PreMatching.class) != null;
            if (preMatching && preMatchingFilters != null) {
                it.remove();
                preMatchingFilters.add(new RankedProvider<>((ContainerRequestFilter) provider.getProvider(),
                        model.getPriority(ContainerRequestFilter.class)));
            }

            boolean nameBound = model.isNameBound();
            if (nameBound
                    && !applicationNameBindings.isEmpty()
                    && applicationNameBindings.containsAll(model.getNameBindings())) {
                // override the name-bound flag
                nameBound = false;
            }

            if (nameBound) { // not application-bound
                if (!preMatching) {
                    it.remove();
                    for (final Class<? extends Annotation> binding : model.getNameBindings()) {
                        result.add(binding, provider);
                        inverseNameBoundMap.add(provider, binding);
                    }
                } else {
                    LOGGER.warning(LocalizationMessages.PREMATCHING_ALSO_NAME_BOUND(providerClass));
                }
            }
        }

        return result;
    }
}
