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

package org.glassfish.jersey.server.internal;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.container.AsyncResponse;

import org.glassfish.jersey.server.spi.ContainerResponseWriter;
import org.glassfish.jersey.server.spi.ContainerResponseWriter.TimeoutHandler;

/**
 * Common {@link ContainerResponseWriter#suspend(long, TimeUnit, ContainerResponseWriter.TimeoutHandler)}
 * and {@link ContainerResponseWriter#setSuspendTimeout(long, TimeUnit)} handler that can be used in
 * {@link ContainerResponseWriter} implementations instead of the underlying infrastructure.
 *
 * @author Michal Gajdos
 * @author Marek Potociar
 */
public class JerseyRequestTimeoutHandler {

    private static final Logger LOGGER = Logger.getLogger(JerseyRequestTimeoutHandler.class.getName());

    private ScheduledFuture<?> timeoutTask = null; // guarded by runtimeLock
    private ContainerResponseWriter.TimeoutHandler timeoutHandler = null; // guarded by runtimeLock
    private boolean suspended = false; // guarded by runtimeLock
    private final Object runtimeLock = new Object();

    private final ContainerResponseWriter containerResponseWriter;
    private final ScheduledExecutorService executor;

    /**
     * Create request timeout handler for the giver {@link ContainerResponseWriter response writer}.
     *
     * @param containerResponseWriter response writer to create request timeout handler for.
     * @param timeoutTaskExecutor     Jersey runtime executor used for background execution of timeout
     *                                handling tasks.
     */
    public JerseyRequestTimeoutHandler(final ContainerResponseWriter containerResponseWriter,
                                       final ScheduledExecutorService timeoutTaskExecutor) {
        this.containerResponseWriter = containerResponseWriter;
        this.executor = timeoutTaskExecutor;
    }

    /**
     * Suspend the request/response processing.
     *
     * @param timeOut time-out value. Value less or equal to 0, indicates that
     *                the processing is suspended indefinitely.
     * @param unit    time-out time unit.
     * @param handler time-out handler to process a time-out event if it occurs.
     * @return {@code true} if the suspend operation completed successfully, {@code false} otherwise.
     * @see ContainerResponseWriter#suspend(long, TimeUnit, ContainerResponseWriter.TimeoutHandler)
     */
    public boolean suspend(final long timeOut, final TimeUnit unit, final TimeoutHandler handler) {
        synchronized (runtimeLock) {
            if (suspended) {
                return false;
            }

            suspended = true;
            timeoutHandler = handler;

            containerResponseWriter.setSuspendTimeout(timeOut, unit);
            return true;
        }
    }

    /**
     * Set the suspend timeout.
     *
     * @param timeOut time-out value. Value less or equal to 0, indicates that
     *                the processing is suspended indefinitely.
     * @param unit    time-out time unit.
     * @throws IllegalStateException in case the response writer has not been suspended yet.
     * @see ContainerResponseWriter#setSuspendTimeout(long, TimeUnit)
     */
    public void setSuspendTimeout(final long timeOut, final TimeUnit unit) throws IllegalStateException {
        synchronized (runtimeLock) {
            if (!suspended) {
                throw new IllegalStateException(LocalizationMessages.SUSPEND_NOT_SUSPENDED());
            }

            close(true);

            if (timeOut <= AsyncResponse.NO_TIMEOUT) {
                return;
            }

            try {
                timeoutTask = executor.schedule(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            synchronized (runtimeLock) {
                                timeoutHandler.onTimeout(containerResponseWriter);
                            }
                        } catch (final Throwable throwable) {
                            LOGGER.log(Level.WARNING, LocalizationMessages.SUSPEND_HANDLER_EXECUTION_FAILED(), throwable);
                        }
                    }
                }, timeOut, unit);
            } catch (final IllegalStateException ex) {
                LOGGER.log(Level.WARNING, LocalizationMessages.SUSPEND_SCHEDULING_ERROR(), ex);
            }
        }
    }

    /**
     * Cancel the suspended task.
     */
    public void close() {
        close(false);
    }

    private synchronized void close(final boolean interruptIfRunning) {
        if (timeoutTask != null) {
            timeoutTask.cancel(interruptIfRunning);
            timeoutTask = null;
        }
    }
}
