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

package org.glassfish.jersey.media.sse;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.ClientExecutor;
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder;
import org.glassfish.jersey.internal.util.ExtendedLogger;
import org.glassfish.jersey.media.sse.internal.EventProcessor;

/**
 * Client for reading and processing {@link InboundEvent incoming Server-Sent Events}.
 * <p>
 * Instances of this class are thread safe. To build a new instance, you can use one of the
 * available public {@code EventSource} constructors that produce pre-configured event
 * source instances. Alternatively, you can create a new {@link EventSource.Builder} instance
 * using {@link #target(jakarta.ws.rs.client.WebTarget) EventSource.target(endpoint)} factory method.
 * Compared to {@code EventSource} constructors, an event source builder provides greater flexibility
 * when custom-configuring a new event source builder.
 * </p>
 * <p>
 * Once an {@link EventSource} is created, it {@link #open opens a connection} to the associated {@link WebTarget web target}
 * and starts processing any incoming inbound events.
 * Whenever a new event is received, an {@link EventSource#onEvent(InboundEvent)} method is called as well as any
 * registered {@link EventListener event listeners} are notified (see {@link EventSource#register(EventListener)}
 * and {@link EventSource#register(EventListener, String, String...)}.
 * </p>
 * <h3>Reconnect support</h3>
 * <p>
 * The {@code EventSource} supports automated recuperation from a connection loss, including
 * negotiation of delivery of any missed events based on the last received  SSE event {@code id} field value, provided
 * this field is set by the server and the negotiation facility is supported by the server. In case of a connection loss,
 * the last received SSE event {@code id} field value is send in the <tt>{@value SseFeature#LAST_EVENT_ID_HEADER}</tt> HTTP
 * request header as part of a new connection request sent to the SSE endpoint. Upon a receipt of such reconnect request, the SSE
 * endpoint that supports this negotiation facility is expected to replay all missed events. Note however, that this is a
 * best-effort mechanism which does not provide any guaranty that all events would be delivered without a loss. You should
 * therefore not rely on receiving every single event and design your client application code accordingly.
 * </p>
 * <p>
 * By default, when a connection the the SSE endpoint is lost, the event source will wait <tt>{@value #RECONNECT_DEFAULT}</tt> ms
 * before attempting to reconnect to the SSE endpoint. The SSE endpoint can however control the client-side retry delay
 * by including a special {@code retry} field value in the any send event. Jersey {@code EventSource} implementation
 * tracks any received SSE event {@code retry} field values set by the endpoint and adjusts the reconnect delay accordingly,
 * using the last received {@code retry} field value as the reconnect delay.
 * </p>
 * <p>
 * In addition to handling the standard connection losses, Jersey {@code EventSource} automatically deals with any
 * {@code HTTP 503 Service Unavailable} responses from SSE endpoint, that contain a
 * <tt>{@value jakarta.ws.rs.core.HttpHeaders#RETRY_AFTER}</tt> HTTP header with a valid value. The
 * <tt>HTTP 503 + {@value jakarta.ws.rs.core.HttpHeaders#RETRY_AFTER}</tt> technique is often used by HTTP endpoints
 * as a means of connection and traffic throttling. In case a
 * <tt>HTTP 503 + {@value jakarta.ws.rs.core.HttpHeaders#RETRY_AFTER}</tt> response is received in return to a connection
 * request, Jersey {@code EventSource} will automatically schedule a new reconnect attempt and use the received
 * <tt>{@value jakarta.ws.rs.core.HttpHeaders#RETRY_AFTER}</tt> HTTP header value as a one-time override of the reconnect delay.
 * </p>
 * <h3>Using HTTP persistent connections</h3>
 * <p>
 * The experience has shown that persistent HTTP connection management in the {@link java.net.HttpURLConnection},
 * that is used as a default Jersey client {@link org.glassfish.jersey.client.spi.Connector connector}, is fragile.
 * It is unfortunately quite possible, that under heavy load the client and server connections may get out of sync,
 * causing Jersey {@code EventSource} hang on a connection that has already been closed by a server, but has not been
 * properly cleaned up by the {@link java.net.HttpURLConnection} management code, and has been reused for a re-connect
 * request instead. To avoid this issue, Jersey {@code EventSource} implementation by default disables
 * <a href="http://en.wikipedia.org/wiki/HTTP_persistent_connection">persistent HTTP connections</a> when connecting
 * (or reconnecting) to the SSE endpoint.
 * </p>
 * <p>
 * In case you are using Jersey event source with a Jersey client
 * {@link org.glassfish.jersey.client.ClientConfig#connectorProvider(org.glassfish.jersey.client.spi.ConnectorProvider)}
 * connector provider configured to use some other client {@code ConnectorProvider} implementation able to reliably
 * manage persistent HTTP connections (such as {@code org.glassfish.jersey.grizzly.connector.GrizzlyConnectorProvider} or
 * {@code org.glassfish.jersey.apache.connector.ApacheConnectorProvider}), or in case you simply need to use persistent
 * HTTP connections, you may do so by invoking the {@link Builder#usePersistentConnections() usePersistentConnections()} method
 * on an event source builder prior to creating a new event source instance.
 * </p>
 *
 * @author Pavel Bucek
 * @author Marek Potociar
 */
public class EventSource implements EventListener {

    /**
     * Default SSE {@link EventSource} reconnect delay value in milliseconds.
     *
     * @since 2.3
     */
    public static final long RECONNECT_DEFAULT = 500;

    private static final Level CONNECTION_ERROR_LEVEL = Level.FINE;
    private static final ExtendedLogger LOGGER = new ExtendedLogger(Logger.getLogger(EventSource.class.getName()), Level.FINEST);

    /**
     * SSE streaming resource target.
     */
    private final WebTarget target;
    /**
     * Default reconnect delay.
     */
    private final long reconnectDelay;
    /**
     * Flag indicating if the persistent HTTP connections should be disabled.
     */
    private final boolean disableKeepAlive;
    /**
     * Incoming SSE event processing task executor.
     */
    private final CloseableClientExecutor executor;
    /**
     * Event source internal state.
     */
    private final AtomicReference<EventProcessor.State> state = new AtomicReference<>(EventProcessor.State.READY);
    /**
     * List of all listeners not bound to receive only events of a particular name.
     */
    private final List<EventListener> unboundListeners = new CopyOnWriteArrayList<>();
    /**
     * A map of listeners bound to receive only events of a particular name.
     */
    private final ConcurrentMap<String, List<EventListener>> boundListeners = new ConcurrentHashMap<>();

    /**
     * Shutdown callback.
     * <p>
     * Invoked when event processing reaches terminal stage.
     */
    private final EventProcessor.ShutdownHandler shutdownHandler = EventSource.this::shutdown;

    /**
     * Create a new {@link EventSource.Builder event source builder} that provides convenient way how to
     * configure and fine-tune various aspects of a newly prepared event source instance.
     *
     * @param endpoint SSE streaming endpoint. Must not be {@code null}.
     * @return a builder of a new event source instance pointing at the specified SSE streaming endpoint.
     * @throws NullPointerException in case the supplied web target is {@code null}.
     * @since 2.3
     */
    public static Builder target(WebTarget endpoint) {
        return new Builder(endpoint);
    }

    /**
     * Create new SSE event source and open a connection it to the supplied SSE streaming {@link WebTarget web target}.
     *
     * This constructor is performs the same series of actions as a call to:
     * <pre>EventSource.target(endpoint).open()</pre>
     * <p>
     * The created event source instance automatically {@link #open opens a connection} to the supplied SSE streaming
     * web target and starts processing incoming {@link InboundEvent events}.
     * </p>
     * <p>
     * The incoming events are processed by the event source in an asynchronous task that runs in an
     * internal single-threaded {@link ScheduledExecutorService scheduled executor service}.
     * </p>
     *
     * @param endpoint SSE streaming endpoint. Must not be {@code null}.
     * @throws NullPointerException in case the supplied web target is {@code null}.
     */
    public EventSource(final WebTarget endpoint) {
        this(endpoint, true);
    }

    /**
     * Create new SSE event source pointing at a SSE streaming {@link WebTarget web target}.
     *
     * This constructor is performs the same series of actions as a call to:
     * <pre>
     * if (open) {
     *     EventSource.target(endpoint).open();
     * } else {
     *     EventSource.target(endpoint).build();
     * }</pre>
     * <p>
     * If the supplied {@code open} flag is {@code true}, the created event source instance automatically
     * {@link #open opens a connection} to the supplied SSE streaming web target and starts processing incoming
     * {@link InboundEvent events}.
     * Otherwise, if the {@code open} flag is set to {@code false}, the created event source instance
     * is not automatically connected to the web target. In this case it is expected that the user who
     * created the event source will manually invoke its {@link #open()} method.
     * </p>
     * <p>
     * Once the event source is open, the incoming events are processed by the event source in an
     * asynchronous task that runs in an internal single-threaded {@link ScheduledExecutorService
     * scheduled executor service}.
     * </p>
     *
     * @param endpoint SSE streaming endpoint. Must not be {@code null}.
     * @param open     if {@code true}, the event source will immediately connect to the SSE endpoint,
     *                 if {@code false}, the connection will not be established until {@link #open()} method is
     *                 called explicitly on the event stream.
     * @throws NullPointerException in case the supplied web target is {@code null}.
     */
    public EventSource(final WebTarget endpoint, final boolean open) {
        this(endpoint, null, RECONNECT_DEFAULT, true, open);
    }

    private EventSource(final WebTarget target,
                        final String name,
                        final long reconnectDelay,
                        final boolean disableKeepAlive,
                        final boolean open) {
        if (target == null) {
            throw new NullPointerException("Web target is 'null'.");
        }
        this.target = SseFeature.register(target);
        this.reconnectDelay = reconnectDelay;
        this.disableKeepAlive = disableKeepAlive;

        final String esName = (name == null) ? createDefaultName(target) : name;

        this.executor = new CloseableClientExecutor(Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat(esName + "-%d")
                                          .setDaemon(true)
                                          .build()));

        if (open) {
            open();
        }
    }

    private static String createDefaultName(WebTarget target) {
        return String.format("jersey-sse-event-source-[%s]", target.getUri().toASCIIString().replace("%", "%%"));
    }

    /**
     * Open the connection to the supplied SSE underlying {@link WebTarget web target} and start processing incoming
     * {@link InboundEvent events}.
     *
     * @throws IllegalStateException in case the event source has already been opened earlier.
     */
    public void open() {
        if (!state.compareAndSet(EventProcessor.State.READY, EventProcessor.State.OPEN)) {
            switch (state.get()) {
                case OPEN:
                    throw new IllegalStateException(LocalizationMessages.EVENT_SOURCE_ALREADY_CONNECTED());
                case CLOSED:
                    throw new IllegalStateException(LocalizationMessages.EVENT_SOURCE_ALREADY_CLOSED());
            }
        }

        EventProcessor.Builder builder =
                EventProcessor.builder(target, state, executor, this, shutdownHandler)
                              .boundListeners(boundListeners)
                              .unboundListeners(unboundListeners)
                              .reconnectDelay(reconnectDelay, TimeUnit.MILLISECONDS);

        if (disableKeepAlive) {
            builder.disableKeepAlive();
        }

        EventProcessor processor = builder.build();

        executor.submit(processor);

        // return only after the first request to the SSE endpoint has been made
        processor.awaitFirstContact();
    }

    /**
     * Check if this event source instance has already been {@link #open() opened}.
     *
     * @return {@code true} if this event source is open, {@code false} otherwise.
     */
    public boolean isOpen() {
        return state.get() == EventProcessor.State.OPEN;
    }

    /**
     * Register new {@link EventListener event listener} to receive all streamed {@link InboundEvent SSE events}.
     *
     * @param listener event listener to be registered with the event source.
     * @see #register(EventListener, String, String...)
     */
    public void register(final EventListener listener) {
        register(listener, null);
    }

    /**
     * Add name-bound {@link EventListener event listener} which will be called only for incoming SSE
     * {@link InboundEvent events} whose {@link InboundEvent#getName() name} is equal to the specified
     * name(s).
     *
     * @param listener   event listener to register with this event source.
     * @param eventName  inbound event name.
     * @param eventNames additional event names.
     * @see #register(EventListener)
     */
    public void register(final EventListener listener, final String eventName, final String... eventNames) {
        if (eventName == null) {
            unboundListeners.add(listener);
        } else {
            addBoundListener(eventName, listener);

            if (eventNames != null) {
                for (String name : eventNames) {
                    addBoundListener(name, listener);
                }
            }
        }
    }

    private void addBoundListener(final String name, final EventListener listener) {
        List<EventListener> listeners = boundListeners.putIfAbsent(name,
                new CopyOnWriteArrayList<>(Collections.singleton(listener)));
        if (listeners != null) {
            // alas, new listener collection registration conflict:
            // need to add the new listener to the existing listener collection
            listeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default {@code EventSource} implementation is empty, users can override this method to handle
     * incoming {@link InboundEvent}s.
     * </p>
     * <p>
     * Note that overriding this method may be necessary to make sure no {@code InboundEvent incoming events}
     * are lost in case the event source is constructed using {@link #EventSource(jakarta.ws.rs.client.WebTarget)}
     * constructor or in case a {@code true} flag is passed to the {@link #EventSource(jakarta.ws.rs.client.WebTarget, boolean)}
     * constructor, since the connection is opened as as part of the constructor call and the event processing starts
     * immediately. Therefore any {@link EventListener}s registered later after the event source has been constructed
     * may miss the notifications about the one or more events that arrive immediately after the connection to the
     * event source is established.
     * </p>
     *
     * @param inboundEvent received inbound event.
     */
    @Override
    public void onEvent(final InboundEvent inboundEvent) {
        // do nothing
    }

    /**
     * Close this event source.
     *
     * The method will wait up to 5 seconds for the internal event processing task to complete.
     */
    public void close() {
        close(5, TimeUnit.SECONDS);
    }

    /**
     * Close this event source and wait for the internal event processing task to complete
     * for up to the specified amount of wait time.
     * <p>
     * The method blocks until the event processing task has completed execution after a shutdown
     * request, or until the timeout occurs, or the current thread is interrupted, whichever happens
     * first.
     * </p>
     * <p>
     * In case the waiting for the event processing task has been interrupted, this method restores
     * the {@link Thread#interrupted() interrupt} flag on the thread before returning {@code false}.
     * </p>
     *
     * @param timeout the maximum time to wait.
     * @param unit    the time unit of the timeout argument.
     * @return {@code true} if this executor terminated and {@code false} if the timeout elapsed
     * before termination or the termination was interrupted.
     */
    public boolean close(final long timeout, final TimeUnit unit) {
        shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                LOGGER.log(CONNECTION_ERROR_LEVEL,
                        LocalizationMessages.EVENT_SOURCE_SHUTDOWN_TIMEOUT(target.getUri().toString()));
                return false;
            }
        } catch (InterruptedException e) {
            LOGGER.log(CONNECTION_ERROR_LEVEL,
                    LocalizationMessages.EVENT_SOURCE_SHUTDOWN_INTERRUPTED(target.getUri().toString()));
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    private void shutdown() {
        if (state.getAndSet(EventProcessor.State.CLOSED) != EventProcessor.State.CLOSED) {
            // shut down only if has not been shut down before
            LOGGER.debugLog("Shutting down event processing.");
            executor.close();
        }
    }

    /**
     * Closeable client executor.
     * <p>
     * Provides all {@link ClientExecutor} methods as well as {@link Closeable} and {@link #awaitTermination(long, TimeUnit)}.
     */
    private static class CloseableClientExecutor implements ClientExecutor, Closeable {

        private final ScheduledExecutorService scheduledExecutorService;

        /**
         * Create new Closeable Client executor with provided backing scheduled executor service.
         *
         * @param scheduledExecutorService backing scheduled executor service.
         */
        public CloseableClientExecutor(ScheduledExecutorService scheduledExecutorService) {
            this.scheduledExecutorService = scheduledExecutorService;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return scheduledExecutorService.submit(task);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return scheduledExecutorService.submit(task);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return scheduledExecutorService.submit(task, result);
        }

        @Override
        public <T> ScheduledFuture<T> schedule(Callable<T> callable, long delay, TimeUnit unit) {
            return scheduledExecutorService.schedule(callable, delay, unit);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return scheduledExecutorService.schedule(command, delay, unit);
        }

        @Override
        public void close() {
            scheduledExecutorService.shutdownNow();
        }

        /**
         * Blocks until all tasks have completed execution after a shutdown
         * request, or the timeout occurs, or the current thread is
         * interrupted, whichever happens first.
         *
         * @param timeout the maximum time to wait
         * @param unit the time unit of the timeout argument
         * @return {@code true} if this executor terminated and
         *         {@code false} if the timeout elapsed before termination
         * @throws InterruptedException if interrupted while waiting
         */
        boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return scheduledExecutorService.awaitTermination(timeout, unit);
        }
    }


    /**
     * Jersey {@link EventSource} builder class.
     *
     * Event source builder provides methods that let you conveniently configure and subsequently build
     * a new {@code EventSource} instance. You can obtain a new event source builder instance using
     * a static {@link EventSource#target(jakarta.ws.rs.client.WebTarget) EventSource.target(endpoint)} factory method.
     * <p>
     * For example:
     * <pre>
     * EventSource es = EventSource.target(endpoint).named("my source")
     *                             .reconnectingEvery(5, SECONDS)
     *                             .open();
     * </pre>
     * </p>
     *
     * @since 2.3
     */
    public static class Builder {

        private final WebTarget endpoint;

        private long reconnect = EventSource.RECONNECT_DEFAULT;
        private String name = null;
        private boolean disableKeepAlive = true;

        private Builder(final WebTarget endpoint) {
            this.endpoint = endpoint;
        }

        /**
         * Set a custom name for the event source.
         * <p>
         * At present, custom event source name is mainly useful to be able to distinguish different event source
         * event processing threads from one another. If not set, a default name will be generated using the
         * SSE endpoint URI.
         * </p>
         *
         * @param name custom event source name.
         * @return updated event source builder instance.
         */
        public Builder named(String name) {
            this.name = name;
            return this;
        }

        /**
         * Instruct event source to use
         * <a href="http://en.wikipedia.org/wiki/HTTP_persistent_connection">persistent HTTP connections</a> when connecting
         * (or reconnecting) to the SSE endpoint, provided the mechanism is supported by the underlying client
         * {@link org.glassfish.jersey.client.spi.Connector}.
         * <p>
         * By default, the persistent HTTP connections are disabled for the reasons discussed in the {@link EventSource}
         * javadoc.
         * </p>
         *
         * @return updated event source builder instance.
         */
        @SuppressWarnings("unused")
        public Builder usePersistentConnections() {
            disableKeepAlive = false;
            return this;
        }

        /**
         * Set the initial reconnect delay to be used by the event source.
         * <p>
         * Note that this value may be later overridden by the SSE endpoint using either a {@code retry} SSE event field
         * or <tt>HTTP 503 + {@value jakarta.ws.rs.core.HttpHeaders#RETRY_AFTER}</tt> mechanism as described
         * in the {@link EventSource} javadoc.
         * </p>
         *
         * @param delay the default time to wait before attempting to recover from a connection loss.
         * @param unit  time unit of the reconnect delay parameter.
         * @return updated event source builder instance.
         */
        @SuppressWarnings("unused")
        public Builder reconnectingEvery(final long delay, TimeUnit unit) {
            reconnect = unit.toMillis(delay);
            return this;
        }

        /**
         * Build new SSE event source pointing at a SSE streaming {@link WebTarget web target}.
         * <p>
         * The returned event source is ready, but not {@link EventSource#open() connected} to the SSE endpoint.
         * It is expected that you will manually invoke its {@link #open()} method once you are ready to start
         * receiving SSE events. In case you want to build an event source instance that is already connected
         * to the SSE endpoint, use the event source builder {@link #open()} method instead.
         * </p>
         * <p>
         * Once the event source is open, the incoming events are processed by the event source in an
         * asynchronous task that runs in an internal single-threaded {@link ScheduledExecutorService
         * scheduled executor service}.
         * </p>
         *
         * @return new event source instance, ready to be connected to the SSE endpoint.
         * @see #open()
         */
        public EventSource build() {
            return new EventSource(endpoint, name, reconnect, disableKeepAlive, false);
        }

        /**
         * Build new SSE event source pointing at a SSE streaming {@link WebTarget web target}.
         * <p>
         * The returned event source is already {@link EventSource#open() connected} to the SSE endpoint
         * and is processing any new incoming events. In case you want to build an event source instance
         * that is already ready, but not automatically connected to the SSE endpoint, use the event source
         * builder {@link #build()} method instead.
         * </p>
         * <p>
         * The incoming events are processed by the event source in an asynchronous task that runs in an
         * internal single-threaded {@link ScheduledExecutorService scheduled executor service}.
         * </p>
         *
         * @return new event source instance, already connected to the SSE endpoint.
         * @see #build()
         */
        public EventSource open() {
            // opening directly in the constructor is just plain ugly...
            final EventSource source = new EventSource(endpoint, name, reconnect, disableKeepAlive, false);
            source.open();
            return source;
        }
    }
}
