/*
 * Copyright (c) 2012, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2019 Payara Foundation and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.server.validation.internal;

import java.security.AccessController;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Providers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Configuration;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidationProviderResolver;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.spi.ValidationProvider;

import org.glassfish.jersey.internal.ServiceFinder;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.internal.util.ReflectionHelper;
import org.glassfish.jersey.model.internal.RankedComparator;
import org.glassfish.jersey.model.internal.RankedProvider;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.internal.inject.ConfiguredValidator;
import org.glassfish.jersey.server.spi.ValidationInterceptor;
import org.glassfish.jersey.server.validation.ValidationConfig;

/**
 * Bean Validation provider injection binder.
 *
 * @author Michal Gajdos
 */
public final class ValidationBinder extends AbstractBinder {

    private static final Logger LOGGER = Logger.getLogger(ValidationBinder.class.getName());

    @Override
    protected void configure() {
        bindFactory(DefaultConfigurationProvider.class, Singleton.class).to(Configuration.class).in(Singleton.class);

        bindFactory(DefaultValidatorFactoryProvider.class, Singleton.class).to(ValidatorFactory.class).in(Singleton.class);
        bindFactory(DefaultValidatorProvider.class, Singleton.class).to(Validator.class).in(Singleton.class);

        bindFactory(ConfiguredValidatorProvider.class, Singleton.class).to(ConfiguredValidator.class);

        // Custom Exception Mapper and Writer - registering in binder to make possible for users register their own providers.
        bind(ValidationExceptionMapper.class).to(ExceptionMapper.class).in(Singleton.class);
        bind(ValidationErrorMessageBodyWriter.class).to(MessageBodyWriter.class).in(Singleton.class);
    }

    /**
     * Factory providing default {@link jakarta.validation.Configuration} instance.
     */
    private static class DefaultConfigurationProvider implements Supplier<Configuration> {

        private final boolean inOsgi;

        public DefaultConfigurationProvider() {
            this.inOsgi = ReflectionHelper.getOsgiRegistryInstance() != null;
        }

        @Override
        public Configuration get() {
            try {
                if (!inOsgi) {
                    return Validation.byDefaultProvider().configure();
                } else {
                    return Validation
                            .byDefaultProvider()
                            .providerResolver(new ValidationProviderResolver() {
                                @Override
                                public List<ValidationProvider<?>> getValidationProviders() {
                                    final List<ValidationProvider<?>> validationProviders = new ArrayList<>();

                                    for (final ValidationProvider validationProvider : ServiceFinder
                                            .find(ValidationProvider.class)) {
                                        validationProviders.add(validationProvider);
                                    }

                                    return validationProviders;
                                }
                            })
                            .configure();
                }
            } catch (final ValidationException e) {
                // log and re-trow
                LOGGER.log(Level.FINE, LocalizationMessages.VALIDATION_EXCEPTION_PROVIDER(), e);
                throw e;
            }
        }
    }

    /**
     * Factory providing default (un-configured) {@link ValidatorFactory} instance.
     */
    private static class DefaultValidatorFactoryProvider implements Supplier<ValidatorFactory> {

        @Inject
        private Configuration config;

        @Override
        public ValidatorFactory get() {
            return config.buildValidatorFactory();
        }
    }

    /**
     * Factory providing default (un-configured) {@link Validator} instance.
     */
    private static class DefaultValidatorProvider implements Supplier<Validator> {

        @Inject
        private ValidatorFactory factory;

        @Override
        public Validator get() {
            return factory.getValidator();
        }
    }

    /**
     * Factory providing configured {@link Validator} instance.
     */
    private static class ConfiguredValidatorProvider implements Supplier<ConfiguredValidator> {

        @Inject
        private InjectionManager injectionManager;

        @Inject
        private Configuration validationConfig;
        @Inject
        private ValidatorFactory factory;

        @Context
        private jakarta.ws.rs.core.Configuration jaxRsConfig;
        @Context
        private Providers providers;
        @Context
        private ResourceContext resourceContext;

        private ConfiguredValidator defaultValidator;

        private final WeakHashMap<ContextResolver<ValidationConfig>, ConfiguredValidator> validatorCache =
                new WeakHashMap<>();

        @Override
        public ConfiguredValidator get() {

            // Custom Configuration.
            final ContextResolver<ValidationConfig> contextResolver =
                    providers.getContextResolver(ValidationConfig.class, MediaType.WILDCARD_TYPE);

            if (contextResolver == null) {
                return getDefaultValidator();
            } else {
                if (!validatorCache.containsKey(contextResolver)) {
                    final ValidateOnExecutionHandler validateOnExecutionHandler =
                            new ValidateOnExecutionHandler(validationConfig, !isValidateOnExecutableOverrideCheckDisabled());

                    final ValidatorContext context = getDefaultValidatorContext(validateOnExecutionHandler);
                    final ValidationConfig config = contextResolver.getContext(ValidationConfig.class);

                    if (config != null) {
                        // MessageInterpolator
                        if (config.getMessageInterpolator() != null) {
                            context.messageInterpolator(config.getMessageInterpolator());
                        }

                        // TraversableResolver
                        if (config.getTraversableResolver() != null) {
                            context.traversableResolver(
                                    getTraversableResolver(config.getTraversableResolver(), validateOnExecutionHandler));
                        }

                        // ConstraintValidatorFactory
                        if (config.getConstraintValidatorFactory() != null) {
                            context.constraintValidatorFactory(config.getConstraintValidatorFactory());
                        }

                        // ParameterNameProvider
                        if (config.getParameterNameProvider() != null) {
                            context.parameterNameProvider(config.getParameterNameProvider());
                        }
                    }

                    validatorCache.put(contextResolver,
                            new DefaultConfiguredValidator(context.getValidator(), this.validationConfig,
                                    validateOnExecutionHandler, getValidationInterceptors()));
                }

                return validatorCache.get(contextResolver);
            }
        }

        private Iterable<ValidationInterceptor> getValidationInterceptors() {
            final Iterable<RankedProvider<ValidationInterceptor>> validationInterceptorIterable =
                    org.glassfish.jersey.internal.inject.Providers
                            .getAllRankedProviders(injectionManager, ValidationInterceptor.class);
            return org.glassfish.jersey.internal.inject.Providers.sortRankedProviders(
                    new RankedComparator<ValidationInterceptor>(), validationInterceptorIterable);
        }

        /**
         * Return default validator.
         *
         * @return default validator.
         */
        private ConfiguredValidator getDefaultValidator() {
            if (defaultValidator == null) {
                final ValidateOnExecutionHandler validateOnExecutionHandler =
                        new ValidateOnExecutionHandler(validationConfig, !isValidateOnExecutableOverrideCheckDisabled());
                final Validator validator = getDefaultValidatorContext(validateOnExecutionHandler).getValidator();

                defaultValidator = new DefaultConfiguredValidator(validator, validationConfig,
                        validateOnExecutionHandler, getValidationInterceptors());
            }
            return defaultValidator;
        }

        /**
         * Return default {@link ValidatorContext validator context} able to inject JAX-RS resources/providers.
         *
         * @param handler handler to create traversable resolver for.
         * @return default validator context.
         */
        private ValidatorContext getDefaultValidatorContext(final ValidateOnExecutionHandler handler) {
            final ValidatorContext context = factory.usingContext();

            // if CDI is available use composite factory
            if (isCDIAvailable()) {
                // Composite Configuration - due to PAYARA-2491
                // https://github.com/payara/Payara/issues/2245
                context.constraintValidatorFactory(resourceContext.getResource(
                        CompositeInjectingConstraintValidatorFactory.class));
            } else {
                // Default Configuration.
                context.constraintValidatorFactory(resourceContext.getResource(InjectingConstraintValidatorFactory.class));
            }

            // Traversable Resolver.
            context.traversableResolver(getTraversableResolver(factory.getTraversableResolver(), handler));

            return context;
        }

        private boolean isCDIAvailable() {
            // Both CDI & Jersey CDI modules must be available
            return AccessController.doPrivileged(
                        ReflectionHelper.classForNamePA("jakarta.enterprise.inject.spi.BeanManager")) != null
                   &&
                   AccessController.doPrivileged(
                        ReflectionHelper.classForNamePA("org.glassfish.jersey.ext.cdi1x.internal.CdiUtil")) != null;
        }

        /**
         * Create traversable resolver able to process {@link jakarta.validation.executable.ValidateOnExecution} annotation on
         * beans.
         *
         * @param delegate resolver to be wrapped into the custom traversable resolver.
         * @param handler  handler to create traversable resolver for.
         * @return custom traversable resolver.
         */
        private ValidateOnExecutionTraversableResolver getTraversableResolver(TraversableResolver delegate,
                                                                              final ValidateOnExecutionHandler handler) {
            if (delegate == null) {
                delegate = validationConfig.getDefaultTraversableResolver();
            }

            final boolean validationEnabled = validationConfig.getBootstrapConfiguration().isExecutableValidationEnabled();
            final ValidateOnExecutionTraversableResolver traversableResolver = new
                    ValidateOnExecutionTraversableResolver(delegate, handler, validationEnabled);

            return resourceContext.initResource(traversableResolver);
        }

        private boolean isValidateOnExecutableOverrideCheckDisabled() {
            return PropertiesHelper.isProperty(
                    jaxRsConfig.getProperty(ServerProperties.BV_DISABLE_VALIDATE_ON_EXECUTABLE_OVERRIDE_CHECK));
        }
    }
}
