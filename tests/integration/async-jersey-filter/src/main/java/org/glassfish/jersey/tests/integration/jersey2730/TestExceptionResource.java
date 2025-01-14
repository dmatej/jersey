/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.tests.integration.jersey2730;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;

import jakarta.inject.Singleton;

import org.glassfish.jersey.servlet.internal.ResponseWriter;
import org.glassfish.jersey.tests.integration.jersey2730.exception.MappedException;
import org.glassfish.jersey.tests.integration.jersey2730.exception.UnmappedException;
import org.glassfish.jersey.tests.integration.jersey2730.exception.UnmappedRuntimeException;

/**
 * @author Stepan Vavra
 */
@Path("/exception")
@Singleton
public class TestExceptionResource {

    /**
     * An instance of thread that was processing a last request to this resource.
     */
    private Thread lastProcessingThread;

    @GET
    @Path("null")
    public void get(@Suspended final AsyncResponse asyncResponse) {
        lastProcessingThread = Thread.currentThread();
        asyncResponse.resume((Throwable) null);
    }

    @GET
    @Path("mapped")
    public void getMappedException(@Suspended final AsyncResponse asyncResponse) {
        lastProcessingThread = Thread.currentThread();
        asyncResponse.resume(new MappedException());
    }

    @GET
    @Path("unmapped")
    public void getUnmappedException(@Suspended final AsyncResponse asyncResponse) {
        lastProcessingThread = Thread.currentThread();
        asyncResponse.resume(new UnmappedException());
    }

    @GET
    @Path("runtime")
    public void getUnmappedRuntimeException(@Suspended final AsyncResponse asyncResponse) {
        lastProcessingThread = Thread.currentThread();
        asyncResponse.resume(new UnmappedRuntimeException());
    }

    /**
     * Returns whether a thread that was processing a last request got stuck in {@link ResponseWriter}.
     * <p/>
     * Under normal circumstances, the last processing thread should return back to the servlet container
     * and its pool.
     * <p/>
     * May not work when executed in parallel.
     *
     * @return
     */
    @GET
    @Path("rpc/lastthreadstuck")
    public boolean lastThreadStuckRpc() {
        if (lastProcessingThread == null || Thread.currentThread() == lastProcessingThread) {
            return false;
        }

        switch (lastProcessingThread.getState()) {
            case BLOCKED:
            case TIMED_WAITING:
            case WAITING:
                for (StackTraceElement stackTraceElement : lastProcessingThread.getStackTrace()) {
                    if (ResponseWriter.class.getName().equals(stackTraceElement.getClassName())) {
                        return true;
                    }
                }
        }

        return false;
    }
}
