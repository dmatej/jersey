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

package org.glassfish.jersey.model.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

import jakarta.annotation.Priority;

import org.glassfish.jersey.ExtendedConfig;
import org.glassfish.jersey.internal.LocalizationMessages;
import org.glassfish.jersey.internal.ServiceFinder;
import org.glassfish.jersey.internal.inject.Binder;
import org.glassfish.jersey.internal.inject.CompositeBinder;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.ProviderBinder;
import org.glassfish.jersey.internal.spi.AutoDiscoverable;
import org.glassfish.jersey.internal.spi.ForcedAutoDiscoverable;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.model.ContractProvider;
import org.glassfish.jersey.process.Inflector;

/**
 * Common immutable {@link jakarta.ws.rs.core.Configuration} implementation for
 * server and client.
 *
 * @author Michal Gajdos
 * @author Marek Potociar
 */
public class CommonConfig implements FeatureContext, ExtendedConfig {

    private static final Logger LOGGER = Logger.getLogger(CommonConfig.class.getName());
    private static final Function<Object, Binder> CAST_TO_BINDER = Binder.class::cast;

    /**
     * Configuration runtime type.
     */
    private final RuntimeType type;
    /**
     * Configuration properties collection and it's immutable views.
     */
    private final Map<String, Object> properties;
    private final Map<String, Object> immutablePropertiesView;
    private final Collection<String> immutablePropertyNames;
    /**
     * Configured providers, does not include features and binders.
     */
    private final ComponentBag componentBag;
    /**
     * Collection of unprocessed feature registrations.
     */
    private final List<FeatureRegistration> newFeatureRegistrations;
    /**
     * Collection of enabled feature classes.
     */
    private final Set<Class<? extends Feature>> enabledFeatureClasses;
    /**
     * Collection of enabled feature instances.
     */
    private final Set<Feature> enabledFeatures;

    /**
     * Flag determining whether the configuration of meta-providers (excl. binders) should be disabled.
     */
    private boolean disableMetaProviderConfiguration;

    /**
     * A single feature registration record.
     */
    private static final class FeatureRegistration {

        private final Class<? extends Feature> featureClass;
        private final Feature feature;
        private final RuntimeType runtimeType;
        private final int priority;

        private FeatureRegistration(final Class<? extends Feature> featureClass, int priority) {
            this.featureClass = featureClass;
            this.feature = null;
            final ConstrainedTo runtimeTypeConstraint = featureClass.getAnnotation(ConstrainedTo.class);
            this.runtimeType = runtimeTypeConstraint == null ? null : runtimeTypeConstraint.value();
            this.priority = priority(featureClass, priority);
        }

        private FeatureRegistration(final Feature feature, int priority) {
            this.featureClass = feature.getClass();
            this.feature = feature;
            final ConstrainedTo runtimeTypeConstraint = featureClass.getAnnotation(ConstrainedTo.class);
            this.runtimeType = runtimeTypeConstraint == null ? null : runtimeTypeConstraint.value();
            this.priority = priority(featureClass, priority);
        }

        private static int priority(Class<? extends Feature> featureClass, int priority) {
            if (priority != ContractProvider.NO_PRIORITY) {
                return priority;
            }
            final Priority priorityAnnotation = featureClass.getAnnotation(Priority.class);
            if (priorityAnnotation != null) {
                return priorityAnnotation.value();
            } else {
                return Priorities.USER;
            }
        }

        /**
         * Get the registered feature class.
         *
         * @return registered feature class.
         */
        private Class<? extends Feature> getFeatureClass() {
            return featureClass;
        }

        /**
         * Get the registered feature instance or {@code null} if this is a
         * class based feature registration.
         *
         * @return the registered feature instance or {@code null} if this is a
         *         class based feature registration.
         */
        private Feature getFeature() {
            return feature;
        }

        /**
         * Get the {@code RuntimeType} constraint given by {@code ConstrainedTo} annotated
         * the Feature or {@code null} if not annotated.
         *
         * @return the {@code RuntimeType} constraint given by {@code ConstrainedTo} annotated
         *         the Feature or {@code null} if not annotated.
         */
        private RuntimeType getFeatureRuntimeType() {
            return runtimeType;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FeatureRegistration)) {
                return false;
            }
            final FeatureRegistration other = (FeatureRegistration) obj;

            return (featureClass == other.featureClass)
                    || (feature != null && (feature == other.feature || feature.equals(other.feature)));
        }

        @Override
        public int hashCode() {
            int hash = 47;
            hash = 13 * hash + (feature != null ? feature.hashCode() : 0);
            hash = 13 * hash + (featureClass != null ? featureClass.hashCode() : 0);
            return hash;
        }
    }

    /**
     * Create a new {@code RuntimeConfig} instance.
     * <p>
     * The constructor provides a way for defining a {@link ContractProvider contract
     * provider model} registration strategy. Once a registration model is built
     * for a newly registered contract, the provided registration strategy filter is
     * consulted whether the model should be registered or not.
     * </p>
     * <p>
     * Clients can use the method to cancel any contract provider model registration
     * that does not meet the criteria of a given configuration context, such as a model
     * that does not have any recognized contracts associated with it.
     * </p>
     *
     * @param type                 configuration runtime type.
     * @param registrationStrategy function driving the decision (based on the introspected
     *                             {@link ContractProvider contract provider model}) whether
     *                             or not should the component class registration continue
     *                             towards a successful completion.
     */
    public CommonConfig(final RuntimeType type, final Predicate<ContractProvider> registrationStrategy) {
        this.type = type;

        this.properties = new HashMap<>();
        this.immutablePropertiesView = Collections.unmodifiableMap(properties);
        this.immutablePropertyNames = Collections.unmodifiableCollection(properties.keySet());

        this.componentBag = ComponentBag.newInstance(registrationStrategy);

        this.newFeatureRegistrations = new LinkedList<>();

        this.enabledFeatureClasses = Collections.newSetFromMap(new IdentityHashMap<>());
        this.enabledFeatures = new HashSet<>();

        this.disableMetaProviderConfiguration = false;
    }

    /**
     * Copy constructor.
     *
     * @param config configurable to copy class properties from.
     */
    public CommonConfig(final CommonConfig config) {
        this.type = config.type;

        this.properties = new HashMap<>(config.properties.size());
        this.immutablePropertiesView = Collections.unmodifiableMap(this.properties);
        this.immutablePropertyNames = Collections.unmodifiableCollection(this.properties.keySet());

        this.componentBag = config.componentBag.copy();

        this.newFeatureRegistrations = new LinkedList<>();
        this.enabledFeatureClasses = Collections.newSetFromMap(new IdentityHashMap<>());
        this.enabledFeatures = new HashSet<>();

        copy(config, false);
    }

    /**
     * Copy config properties, providers from given {@code config} to this instance.
     *
     * @param config configurable to copy class properties from.
     * @param loadComponentBag {@code true} if the component bag from config should be copied as well, {@code false} otherwise.
     */
    private void copy(final CommonConfig config, final boolean loadComponentBag) {
        this.properties.clear();
        this.properties.putAll(config.properties);

        this.newFeatureRegistrations.clear();
        this.newFeatureRegistrations.addAll(config.newFeatureRegistrations);

        this.enabledFeatureClasses.clear();
        this.enabledFeatureClasses.addAll(config.enabledFeatureClasses);

        this.enabledFeatures.clear();
        this.enabledFeatures.addAll(config.enabledFeatures);

        this.disableMetaProviderConfiguration = config.disableMetaProviderConfiguration;

        if (loadComponentBag) {
            this.componentBag.loadFrom(config.componentBag);
        }
    }

    @Override
    public ExtendedConfig getConfiguration() {
        return this;
    }

    @Override
    public RuntimeType getRuntimeType() {
        return type;
    }

    @Override
    public Map<String, Object> getProperties() {
        return immutablePropertiesView;
    }

    @Override
    public Object getProperty(final String name) {
        return properties.get(name);
    }

    @Override
    public boolean isProperty(final String name) {
        return PropertiesHelper.isProperty(getProperty(name));
    }

    @Override
    public Collection<String> getPropertyNames() {
        return immutablePropertyNames;
    }

    @Override
    public boolean isEnabled(final Class<? extends Feature> featureClass) {
        return enabledFeatureClasses.contains(featureClass);
    }

    @Override
    public boolean isEnabled(final Feature feature) {
        return enabledFeatures.contains(feature);
    }

    @Override
    public boolean isRegistered(final Object component) {
        return componentBag.getInstances().contains(component);
    }

    @Override
    public boolean isRegistered(final Class<?> componentClass) {
        return componentBag.getRegistrations().contains(componentClass);
    }

    @Override
    public Map<Class<?>, Integer> getContracts(final Class<?> componentClass) {
        final ContractProvider model = componentBag.getModel(componentClass);
        return (model == null) ? Collections.emptyMap() : model.getContractMap();
    }

    @Override
    public Set<Class<?>> getClasses() {
        return componentBag.getClasses();
    }

    @Override
    public Set<Object> getInstances() {
        return componentBag.getInstances();
    }

    /**
     * Returns a {@link ComponentBag} instance associated with the configuration.
     *
     * @return a non-null component bag instance.
     */
    public final ComponentBag getComponentBag() {
        return componentBag;
    }

    /**
     * An extension point that provides a way how to define a custom enhancement/update
     * operation of a contract provider model registration being produced for a given
     * component class.
     * Default implementation return an enhancer just builds the model.
     * <p>
     * Derived implementations may use this method to e.g. filter out all contracts not
     * applicable in the given configuration context or change the model scope. The returned
     * set of filtered contracts is then used for the actual provider registration.
     * </p>
     *
     * @param componentClass class of the component being registered.
     * @return filter for the contracts that being registered for a given component class.
     */
    protected Inflector<ContractProvider.Builder, ContractProvider> getModelEnhancer(final Class<?> componentClass) {
        return ComponentBag.AS_IS;
    }

    /**
     * Set the configured properties to the provided map of properties.
     *
     * @param properties new map of properties to be set.
     * @return updated configuration instance.
     */
    public CommonConfig setProperties(final Map<String, ?> properties) {
        this.properties.clear();

        if (properties != null) {
            this.properties.putAll(properties);
        }
        return this;
    }

    /**
     * Add properties to {@code ResourceConfig}.
     *
     * If any of the added properties exists already, he values of the existing
     * properties will be replaced with new values.
     *
     * @param properties properties to add.
     * @return updated configuration instance.
     */
    public CommonConfig addProperties(final Map<String, ?> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
        return this;
    }

    @Override
    public CommonConfig property(final String name, final Object value) {
        if (value == null) {
            properties.remove(name);
        } else {
            properties.put(name, value);
        }
        return this;
    }

    @Override
    public CommonConfig register(final Class<?> componentClass) {
        checkComponentClassNotNull(componentClass);
        if (componentBag.register(componentClass, getModelEnhancer(componentClass))) {
            processFeatureRegistration(null, componentClass, ContractProvider.NO_PRIORITY);
        }

        return this;
    }

    @Override
    public CommonConfig register(final Class<?> componentClass, final int bindingPriority) {
        checkComponentClassNotNull(componentClass);
        if (componentBag.register(componentClass, bindingPriority, getModelEnhancer(componentClass))) {
            processFeatureRegistration(null, componentClass, bindingPriority);
        }

        return this;
    }

    @Override
    public CommonConfig register(final Class<?> componentClass, final Class<?>... contracts) {
        checkComponentClassNotNull(componentClass);
        if (contracts == null || contracts.length == 0) {
            LOGGER.warning(LocalizationMessages.COMPONENT_CONTRACTS_EMPTY_OR_NULL(componentClass));
            return this;
        }
        if (componentBag.register(componentClass, asNewIdentitySet(contracts), getModelEnhancer(componentClass))) {
            processFeatureRegistration(null, componentClass, ContractProvider.NO_PRIORITY);
        }

        return this;
    }

    @Override
    public CommonConfig register(final Class<?> componentClass, final Map<Class<?>, Integer> contracts) {
        checkComponentClassNotNull(componentClass);
        if (componentBag.register(componentClass, contracts, getModelEnhancer(componentClass))) {
            processFeatureRegistration(null, componentClass, ContractProvider.NO_PRIORITY);
        }

        return this;
    }

    @Override
    public CommonConfig register(final Object component) {
        checkProviderNotNull(component);

        final Class<?> componentClass = component.getClass();
        if (componentBag.register(component, getModelEnhancer(componentClass))) {
            processFeatureRegistration(component, componentClass, ContractProvider.NO_PRIORITY);
        }

        return this;
    }

    @Override
    public CommonConfig register(final Object component, final int bindingPriority) {
        checkProviderNotNull(component);
        final Class<?> componentClass = component.getClass();
        if (componentBag.register(component, bindingPriority, getModelEnhancer(componentClass))) {
            processFeatureRegistration(component, componentClass, bindingPriority);
        }

        return this;
    }

    @Override
    public CommonConfig register(final Object component, final Class<?>... contracts) {
        checkProviderNotNull(component);
        final Class<?> componentClass = component.getClass();
        if (contracts == null || contracts.length == 0) {
            LOGGER.warning(LocalizationMessages.COMPONENT_CONTRACTS_EMPTY_OR_NULL(componentClass));
            return this;
        }
        if (componentBag.register(component, asNewIdentitySet(contracts), getModelEnhancer(componentClass))) {
            processFeatureRegistration(component, componentClass, ContractProvider.NO_PRIORITY);
        }

        return this;
    }

    @Override
    public CommonConfig register(final Object component, final Map<Class<?>, Integer> contracts) {
        checkProviderNotNull(component);
        final Class<?> componentClass = component.getClass();
        if (componentBag.register(component, contracts, getModelEnhancer(componentClass))) {
            processFeatureRegistration(component, componentClass, ContractProvider.NO_PRIORITY);
        }

        return this;
    }

    private void processFeatureRegistration(final Object component, final Class<?> componentClass, int priority) {
        final ContractProvider model = componentBag.getModel(componentClass);
        if (model.getContracts().contains(Feature.class)) {
            @SuppressWarnings("unchecked")
            final FeatureRegistration registration = (component != null)
                    ? new FeatureRegistration((Feature) component, priority)
                    : new FeatureRegistration((Class<? extends Feature>) componentClass, priority);
            newFeatureRegistrations.add(registration);
        }
    }

    /**
     * Load the internal configuration state from an externally provided configuration state.
     * <p/>
     * Calling this method effectively replaces existing configuration state of the instance with the state represented by the
     * externally provided configuration. If the features, auto-discoverables of given config has been already configured then
     * this method will make sure to not configure them for the second time.
     *
     * @param config external configuration state to replace the configuration of this configurable instance.
     * @return the updated common configuration instance.
     */
    public CommonConfig loadFrom(final Configuration config) {
        if (config instanceof CommonConfig) {
            // If loading from CommonConfig then simply copy properties and check whether given config has been initialized.
            final CommonConfig commonConfig = (CommonConfig) config;

            copy(commonConfig, true);
            this.disableMetaProviderConfiguration = !commonConfig.enabledFeatureClasses.isEmpty();
        } else {
            setProperties(config.getProperties());

            this.enabledFeatures.clear();
            this.enabledFeatureClasses.clear();

            componentBag.clear();
            resetFeatureRegistrations();

            for (final Class<?> clazz : config.getClasses()) {
                if (Feature.class.isAssignableFrom(clazz) && config.isEnabled((Class<? extends Feature>) clazz)) {
                    this.disableMetaProviderConfiguration = true;
                }

                register(clazz, config.getContracts(clazz));
            }

            for (final Object instance : config.getInstances()) {
                if (instance instanceof Feature && config.isEnabled((Feature) instance)) {
                    this.disableMetaProviderConfiguration = true;
                }

                register(instance, config.getContracts(instance.getClass()));
            }
        }

        return this;
    }

    private Set<Class<?>> asNewIdentitySet(final Class<?>... contracts) {
        final Set<Class<?>> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(Arrays.asList(contracts));
        return result;
    }

    private void checkProviderNotNull(final Object provider) {
        if (provider == null) {
            throw new IllegalArgumentException(LocalizationMessages.COMPONENT_CANNOT_BE_NULL());
        }
    }

    private void checkComponentClassNotNull(final Class<?> componentClass) {
        if (componentClass == null) {
            throw new IllegalArgumentException(LocalizationMessages.COMPONENT_CLASS_CANNOT_BE_NULL());
        }
    }

    /**
     * Configure {@link AutoDiscoverable auto-discoverables} in the injection manager.
     *
     * @param injectionManager  injection manager in which the auto-discoverables should be configured.
     * @param autoDiscoverables list of registered auto discoverable components.
     * @param forcedOnly        defines whether all or only forced auto-discoverables should be configured.
     */
    public void configureAutoDiscoverableProviders(final InjectionManager injectionManager,
                                                   final Collection<AutoDiscoverable> autoDiscoverables,
                                                   final boolean forcedOnly) {
        // Check whether meta providers have been initialized for a config this config has been loaded from.
        if (!disableMetaProviderConfiguration) {
            final Set<AutoDiscoverable> providers = new TreeSet<>((o1, o2) -> {
                final int p1 = o1.getClass().isAnnotationPresent(Priority.class)
                        ? o1.getClass().getAnnotation(Priority.class).value() : Priorities.USER;
                final int p2 = o2.getClass().isAnnotationPresent(Priority.class)
                        ? o2.getClass().getAnnotation(Priority.class).value() : Priorities.USER;

                return (p1 < p2 || p1 == p2) ? -1 : 1;
            });

            // Forced (always invoked).
            final List<ForcedAutoDiscoverable> forcedAutoDiscroverables = new LinkedList<>();
            for (Class<ForcedAutoDiscoverable> forcedADType : ServiceFinder.find(ForcedAutoDiscoverable.class, true)
                    .toClassArray()) {
                forcedAutoDiscroverables.add(injectionManager.createAndInitialize(forcedADType));
            }
            providers.addAll(forcedAutoDiscroverables);

            // Regular.
            if (!forcedOnly) {
                providers.addAll(autoDiscoverables);
            }

            for (final AutoDiscoverable autoDiscoverable : providers) {
                final ConstrainedTo constrainedTo = autoDiscoverable.getClass().getAnnotation(ConstrainedTo.class);

                if (constrainedTo == null || type.equals(constrainedTo.value())) {
                    try {
                        autoDiscoverable.configure(this);
                    } catch (final Exception e) {
                        LOGGER.log(Level.FINE,
                                LocalizationMessages.AUTODISCOVERABLE_CONFIGURATION_FAILED(autoDiscoverable.getClass()), e);
                    }
                }
            }
        }
    }

    /**
     * Configure binders in the injection manager and enable JAX-RS features.
     *
     * @param injectionManager injection manager in which the binders and features should be configured.
     */
    public void configureMetaProviders(InjectionManager injectionManager, ManagedObjectsFinalizer finalizer) {
        final Set<Object> configuredExternals = Collections.newSetFromMap(new IdentityHashMap<>());

        // First, configure existing binders
        final Set<Binder> configuredBinders = configureBinders(injectionManager, Collections.emptySet());

        // Check whether meta providers have been initialized for a config this config has been loaded from.
        if (!disableMetaProviderConfiguration) {
            // Next, register external meta objects
            configureExternalObjects(injectionManager, configuredExternals);
            // Configure all features
            configureFeatures(injectionManager, new HashSet<>(), resetFeatureRegistrations(), finalizer);
            // Next, register external meta objects registered by features
            configureExternalObjects(injectionManager, configuredExternals);
            // At last, configure any new binders added by features
            configureBinders(injectionManager, configuredBinders);
        }
    }

    private Set<Binder> configureBinders(InjectionManager injectionManager, Set<Binder> configured) {
        Set<Binder> allConfigured = Collections.newSetFromMap(new IdentityHashMap<>());
        allConfigured.addAll(configured);

        Collection<Binder> binders = getBinder(configured);
        if (!binders.isEmpty()) {
            injectionManager.register(CompositeBinder.wrap(binders));
            allConfigured.addAll(binders);
        }

        return allConfigured;
    }

    private Collection<Binder> getBinder(Set<Binder> configured) {
        return componentBag.getInstances(ComponentBag.BINDERS_ONLY)
                .stream()
                .map(CAST_TO_BINDER)
                .filter(binder -> !configured.contains(binder))
                .collect(Collectors.toList());
    }

    private void configureExternalObjects(InjectionManager injectionManager, Set<Object> externalObjects) {
        Consumer<Object> registerOnce = o -> {
            if (!externalObjects.contains(o)) {
                injectionManager.register(o);
                externalObjects.add(o);
            }
        };
        componentBag.getInstances(model -> ComponentBag.EXTERNAL_ONLY.test(model, injectionManager))
                .forEach(registerOnce);
        componentBag.getClasses(model -> ComponentBag.EXTERNAL_ONLY.test(model, injectionManager))
                .forEach(registerOnce);
    }

    private void configureFeatures(InjectionManager injectionManager,
                                   Set<FeatureRegistration> processed,
                                   List<FeatureRegistration> unprocessed,
                                   ManagedObjectsFinalizer managedObjectsFinalizer) {
        FeatureContextWrapper featureContextWrapper = null;
        for (final FeatureRegistration registration : unprocessed) {
            if (processed.contains(registration)) {
                LOGGER.config(LocalizationMessages.FEATURE_HAS_ALREADY_BEEN_PROCESSED(registration.getFeatureClass()));
                continue;
            }

            final RuntimeType runtimeTypeConstraint = registration.getFeatureRuntimeType();
            if (runtimeTypeConstraint != null && !type.equals(runtimeTypeConstraint)) {
                LOGGER.config(LocalizationMessages.FEATURE_CONSTRAINED_TO_IGNORED(
                        registration.getFeatureClass(), registration.runtimeType, type));
                continue;
            }

            Feature feature = registration.getFeature();
            if (feature == null) {
                feature = injectionManager.createAndInitialize(registration.getFeatureClass());
                managedObjectsFinalizer.registerForPreDestroyCall(feature);
            } else {
                // Disable injection of Feature instances on the client-side. Instances may be registered into multiple
                // web-targets which means that injecting anything into these instances is not safe.
                if (!RuntimeType.CLIENT.equals(type)) {
                    injectionManager.inject(feature);
                }
            }

            if (enabledFeatures.contains(feature)) {
                LOGGER.config(LocalizationMessages.FEATURE_HAS_ALREADY_BEEN_PROCESSED(feature));
                continue;
            }

            if (featureContextWrapper == null) {
                // init lazily
                featureContextWrapper = new FeatureContextWrapper(this, injectionManager);
            }
            final boolean success = feature.configure(featureContextWrapper);

            if (success) {
                processed.add(registration);
                final ContractProvider providerModel = componentBag.getModel(feature.getClass());
                if (providerModel != null) {
                    ProviderBinder.bindProvider(feature, providerModel, injectionManager);
                }
                configureFeatures(injectionManager, processed, resetFeatureRegistrations(), managedObjectsFinalizer);
                enabledFeatureClasses.add(registration.getFeatureClass());
                enabledFeatures.add(feature);
            }
        }
    }

    private List<FeatureRegistration> resetFeatureRegistrations() {
        final List<FeatureRegistration> result = new ArrayList<>(newFeatureRegistrations);
        newFeatureRegistrations.clear();
        Collections.sort(result, (o1, o2) -> o1.priority < o2.priority ? -1 : 1);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CommonConfig)) {
            return false;
        }

        final CommonConfig that = (CommonConfig) o;

        if (type != that.type) {
            return false;
        }
        if (!properties.equals(that.properties)) {
            return false;
        }
        if (!componentBag.equals(that.componentBag)) {
            return false;
        }
        if (!enabledFeatureClasses.equals(that.enabledFeatureClasses)) {
            return false;
        }
        if (!enabledFeatures.equals(that.enabledFeatures)) {
            return false;
        }
        if (!newFeatureRegistrations.equals(that.newFeatureRegistrations)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + properties.hashCode();
        result = 31 * result + componentBag.hashCode();
        result = 31 * result + newFeatureRegistrations.hashCode();
        result = 31 * result + enabledFeatures.hashCode();
        result = 31 * result + enabledFeatureClasses.hashCode();
        return result;
    }
}
