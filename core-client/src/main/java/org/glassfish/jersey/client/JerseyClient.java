/*
 * Copyright (c) 2011, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.UriBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.client.internal.LocalizationMessages;
import org.glassfish.jersey.client.spi.DefaultSslContextProvider;
import org.glassfish.jersey.internal.ServiceFinder;
import org.glassfish.jersey.internal.util.collection.UnsafeValue;
import org.glassfish.jersey.internal.util.collection.Values;

import static org.glassfish.jersey.internal.guava.Preconditions.checkNotNull;
import static org.glassfish.jersey.internal.guava.Preconditions.checkState;

/**
 * Jersey implementation of {@link jakarta.ws.rs.client.Client JAX-RS Client}
 * contract.
 *
 * @author Marek Potociar
 */
public class JerseyClient implements jakarta.ws.rs.client.Client, Initializable<JerseyClient> {
    private static final Logger LOG = Logger.getLogger(JerseyClient.class.getName());

    private static final DefaultSslContextProvider DEFAULT_SSL_CONTEXT_PROVIDER = new DefaultSslContextProvider() {
        @Override
        public SSLContext getDefaultSslContext() {
            return SslConfigurator.getDefaultContext();
        }
    };

    private final AtomicBoolean closedFlag = new AtomicBoolean(false);
    private final boolean isDefaultSslContext;
    private final ClientConfig config;
    private final HostnameVerifier hostnameVerifier;
    private final UnsafeValue<SSLContext, IllegalStateException> sslContext;
    private final LinkedBlockingDeque<WeakReference<JerseyClient.ShutdownHook>> shutdownHooks =
                                        new LinkedBlockingDeque<WeakReference<JerseyClient.ShutdownHook>>();
    private final ReferenceQueue<JerseyClient.ShutdownHook> shReferenceQueue = new ReferenceQueue<JerseyClient.ShutdownHook>();

    /**
     * Client instance shutdown hook.
     */
    interface ShutdownHook {
        /**
         * Invoked when the client instance is closed.
         */
        public void onShutdown();
    }

    /**
     * Create a new Jersey client instance using a default configuration.
     */
    protected JerseyClient() {
        this(null, (UnsafeValue<SSLContext, IllegalStateException>) null, null, null);
    }

    /**
     * Create a new Jersey client instance.
     *
     * @param config     jersey client configuration.
     * @param sslContext jersey client SSL context.
     * @param verifier   jersey client host name verifier.
     */
    protected JerseyClient(final Configuration config,
                           final SSLContext sslContext,
                           final HostnameVerifier verifier) {

        this(config, sslContext, verifier, null);
    }

    /**
     * Create a new Jersey client instance.
     *
     * @param config                    jersey client configuration.
     * @param sslContext                jersey client SSL context.
     * @param verifier                  jersey client host name verifier.
     * @param defaultSslContextProvider default SSL context provider.
     */
    protected JerseyClient(final Configuration config,
                           final SSLContext sslContext,
                           final HostnameVerifier verifier,
                           final DefaultSslContextProvider defaultSslContextProvider) {
        this(config, sslContext == null ? null : Values.unsafe(sslContext), verifier,
             defaultSslContextProvider);
    }

    /**
     * Create a new Jersey client instance.
     *
     * @param config             jersey client configuration.
     * @param sslContextProvider jersey client SSL context provider.
     * @param verifier           jersey client host name verifier.
     */
    protected JerseyClient(final Configuration config,
                           final UnsafeValue<SSLContext, IllegalStateException> sslContextProvider,
                           final HostnameVerifier verifier) {
        this(config, sslContextProvider, verifier, null);
    }

    /**
     * Create a new Jersey client instance.
     *
     * @param config                    jersey client configuration.
     * @param sslContextProvider        jersey client SSL context provider. Non {@code null} provider is expected to
     *                                  return non-default value.
     * @param verifier                  jersey client host name verifier.
     * @param defaultSslContextProvider default SSL context provider.
     */
    protected JerseyClient(final Configuration config,
                           final UnsafeValue<SSLContext, IllegalStateException> sslContextProvider,
                           final HostnameVerifier verifier,
                           final DefaultSslContextProvider defaultSslContextProvider) {
        this.config = config == null ? new ClientConfig(this) : new ClientConfig(this, config);

        if (sslContextProvider == null) {
            this.isDefaultSslContext = true;

            if (defaultSslContextProvider != null) {
                this.sslContext = createLazySslContext(defaultSslContextProvider);
            } else {
                final DefaultSslContextProvider lookedUpSslContextProvider;

                final Iterator<DefaultSslContextProvider> iterator =
                        ServiceFinder.find(DefaultSslContextProvider.class).iterator();

                if (iterator.hasNext()) {
                    lookedUpSslContextProvider = iterator.next();
                } else {
                    lookedUpSslContextProvider = DEFAULT_SSL_CONTEXT_PROVIDER;
                }

                this.sslContext = createLazySslContext(lookedUpSslContextProvider);
            }
        } else {
            this.isDefaultSslContext = false;
            this.sslContext = Values.lazy(sslContextProvider);
        }

        this.hostnameVerifier = verifier;
    }

    @Override
    public void close() {
        if (closedFlag.compareAndSet(false, true)) {
            release();
        }
    }

    private void release() {
        Reference<ShutdownHook> listenerRef;
        while ((listenerRef = shutdownHooks.pollFirst()) != null) {
            JerseyClient.ShutdownHook listener = listenerRef.get();
            if (listener != null) {
                try {
                    listener.onShutdown();
                } catch (Throwable t) {
                    LOG.log(Level.WARNING, LocalizationMessages.ERROR_SHUTDOWNHOOK_CLOSE(listenerRef.getClass().getName()), t);
                }
            }
        }
    }

    private UnsafeValue<SSLContext, IllegalStateException> createLazySslContext(final DefaultSslContextProvider provider) {
        return Values.lazy(new UnsafeValue<SSLContext, IllegalStateException>() {
            @Override
            public SSLContext get() {
                return provider.getDefaultSslContext();
            }
        });
    }

    /**
     * Register a new client shutdown hook.
     *
     * @param shutdownHook client shutdown hook.
     */
    /* package */ void registerShutdownHook(final ShutdownHook shutdownHook) {
        checkNotClosed();
        shutdownHooks.push(new WeakReference<JerseyClient.ShutdownHook>(shutdownHook, shReferenceQueue));
        cleanUpShutdownHooks();
    }

    /**
     * Clean up shutdown hooks that have been garbage collected.
     */
    private void cleanUpShutdownHooks() {

        Reference<? extends ShutdownHook> reference;

        while ((reference = shReferenceQueue.poll()) != null) {

            shutdownHooks.remove(reference);

            final ShutdownHook shutdownHook = reference.get();
            if (shutdownHook != null) { // close this one off if still accessible
                shutdownHook.onShutdown();
            }
        }
    }

    private ScheduledExecutorService getDefaultScheduledExecutorService() {
        return Executors.newScheduledThreadPool(8);
    }

    /**
     * Check client state.
     *
     * @return {@code true} if current {@code JerseyClient} instance is closed, otherwise {@code false}.
     *
     * @see #close()
     */
    public boolean isClosed() {
        return closedFlag.get();
    }

    /**
     * Check that the client instance has not been closed.
     *
     * @throws IllegalStateException in case the client instance has been closed already.
     */
    void checkNotClosed() {
        checkState(!closedFlag.get(), LocalizationMessages.CLIENT_INSTANCE_CLOSED());
    }

    /**
     * Get information about used {@link SSLContext}.
     *
     * @return {@code true} when used {@code SSLContext} is acquired from {@link SslConfigurator#getDefaultContext()},
     * {@code false} otherwise.
     */
    public boolean isDefaultSslContext() {
        return isDefaultSslContext;
    }

    @Override
    public JerseyWebTarget target(final String uri) {
        checkNotClosed();
        checkNotNull(uri, LocalizationMessages.CLIENT_URI_TEMPLATE_NULL());
        return new JerseyWebTarget(uri, this);
    }

    @Override
    public JerseyWebTarget target(final URI uri) {
        checkNotClosed();
        checkNotNull(uri, LocalizationMessages.CLIENT_URI_NULL());
        return new JerseyWebTarget(uri, this);
    }

    @Override
    public JerseyWebTarget target(final UriBuilder uriBuilder) {
        checkNotClosed();
        checkNotNull(uriBuilder, LocalizationMessages.CLIENT_URI_BUILDER_NULL());
        return new JerseyWebTarget(uriBuilder, this);
    }

    @Override
    public JerseyWebTarget target(final Link link) {
        checkNotClosed();
        checkNotNull(link, LocalizationMessages.CLIENT_TARGET_LINK_NULL());
        return new JerseyWebTarget(link, this);
    }

    @Override
    public JerseyInvocation.Builder invocation(final Link link) {
        checkNotClosed();
        checkNotNull(link, LocalizationMessages.CLIENT_INVOCATION_LINK_NULL());
        final JerseyWebTarget t = new JerseyWebTarget(link, this);
        final String acceptType = link.getType();
        return (acceptType != null) ? t.request(acceptType) : t.request();
    }

    @Override
    public JerseyClient register(final Class<?> providerClass) {
        checkNotClosed();
        config.register(providerClass);
        return this;
    }

    @Override
    public JerseyClient register(final Object provider) {
        checkNotClosed();
        config.register(provider);
        return this;
    }

    @Override
    public JerseyClient register(final Class<?> providerClass, final int bindingPriority) {
        checkNotClosed();
        config.register(providerClass, bindingPriority);
        return this;
    }

    @Override
    public JerseyClient register(final Class<?> providerClass, final Class<?>... contracts) {
        checkNotClosed();
        config.register(providerClass, contracts);
        return this;
    }

    @Override
    public JerseyClient register(final Class<?> providerClass, final Map<Class<?>, Integer> contracts) {
        checkNotClosed();
        config.register(providerClass, contracts);
        return this;
    }

    @Override
    public JerseyClient register(final Object provider, final int bindingPriority) {
        checkNotClosed();
        config.register(provider, bindingPriority);
        return this;
    }

    @Override
    public JerseyClient register(final Object provider, final Class<?>... contracts) {
        checkNotClosed();
        config.register(provider, contracts);
        return this;
    }

    @Override
    public JerseyClient register(final Object provider, final Map<Class<?>, Integer> contracts) {
        checkNotClosed();
        config.register(provider, contracts);
        return this;
    }

    @Override
    public JerseyClient property(final String name, final Object value) {
        checkNotClosed();
        config.property(name, value);
        return this;
    }

    @Override
    public ClientConfig getConfiguration() {
        checkNotClosed();
        return config.getConfiguration();
    }

    @Override
    public SSLContext getSslContext() {
        return sslContext.get();
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public ExecutorService getExecutorService() {
        return config.getExecutorService();
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return config.getScheduledExecutorService();
    }

    @Override
    public JerseyClient preInitialize() {
        config.preInitialize();
        return this;
    }
}
