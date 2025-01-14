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

package org.glassfish.jersey.server.internal.monitoring;

import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.ExceptionMapper;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;

/**
 * {@link RequestEvent Request event} implementation. Instances are immutable.
 *
 * @author Miroslav Fuksa
 */
public class RequestEventImpl implements RequestEvent {


    /**
     * Builder of {@link RequestEventImpl}.
     */
    public static class Builder implements RequestEventBuilder {
        private ContainerRequest containerRequest;
        private ContainerResponse containerResponse;
        private Throwable throwable;
        private ExtendedUriInfo extendedUriInfo;
        private Iterable<ContainerResponseFilter> containerResponseFilters;
        private Iterable<ContainerRequestFilter> containerRequestFilters;
        private ExceptionMapper<?> exceptionMapper;
        private boolean success;
        private boolean responseWritten;
        private boolean responseSuccessfullyMapped;
        private ExceptionCause exceptionCause;

        @Override
        public Builder setExceptionMapper(ExceptionMapper<?> exceptionMapper) {
            this.exceptionMapper = exceptionMapper;
            return this;
        }

        @Override
        public Builder setContainerRequest(ContainerRequest containerRequest) {
            this.containerRequest = containerRequest;
            return this;
        }

        @Override
        public Builder setContainerResponse(ContainerResponse containerResponse) {
            this.containerResponse = containerResponse;
            return this;
        }

        @Override
        public Builder setResponseWritten(boolean responseWritten) {
            this.responseWritten = responseWritten;
            return this;
        }

        @Override
        public Builder setSuccess(boolean success) {
            this.success = success;
            return this;
        }

        @Override
        public Builder setException(Throwable throwable, ExceptionCause exceptionCause) {
            this.throwable = throwable;
            this.exceptionCause = exceptionCause;
            return this;
        }


        @Override
        public Builder setExtendedUriInfo(ExtendedUriInfo extendedUriInfo) {
            this.extendedUriInfo = extendedUriInfo;
            return this;
        }

        @Override
        public Builder setContainerResponseFilters(Iterable<ContainerResponseFilter> containerResponseFilters) {
            this.containerResponseFilters = containerResponseFilters;
            return this;
        }

        @Override
        public Builder setContainerRequestFilters(Iterable<ContainerRequestFilter> containerRequestFilters) {
            this.containerRequestFilters = containerRequestFilters;
            return this;
        }

        @Override
        public Builder setResponseSuccessfullyMapped(boolean responseSuccessfullyMapped) {
            this.responseSuccessfullyMapped = responseSuccessfullyMapped;
            return this;
        }

        @Override
        public RequestEventImpl build(Type type) {
            return new RequestEventImpl(type, containerRequest, containerResponse, throwable,
                    extendedUriInfo, containerResponseFilters, containerRequestFilters, exceptionMapper, success,
                    responseSuccessfullyMapped, exceptionCause, responseWritten);
        }
    }


    private RequestEventImpl(Type type, ContainerRequest containerRequest, ContainerResponse containerResponse,
                             Throwable throwable,
                             ExtendedUriInfo extendedUriInfo, Iterable<ContainerResponseFilter> containerResponseFilters,
                             Iterable<ContainerRequestFilter> containerRequestFilters,
                             ExceptionMapper<?> exceptionMapper,
                             boolean success,
                             boolean responseSuccessfullyMapped, ExceptionCause exceptionCause, boolean responseWritten) {
        this.type = type;
        this.containerRequest = containerRequest;
        this.containerResponse = containerResponse;
        this.throwable = throwable;
        this.extendedUriInfo = extendedUriInfo;
        this.containerResponseFilters = containerResponseFilters;
        this.containerRequestFilters = containerRequestFilters;
        this.exceptionMapper = exceptionMapper;
        this.success = success;
        this.responseSuccessfullyMapped = responseSuccessfullyMapped;
        this.exceptionCause = exceptionCause;
        this.responseWritten = responseWritten;
    }


    private final Type type;
    private final ContainerRequest containerRequest;
    private final ContainerResponse containerResponse;
    private final Throwable throwable;
    private final ExtendedUriInfo extendedUriInfo;
    private final Iterable<ContainerResponseFilter> containerResponseFilters;
    private final Iterable<ContainerRequestFilter> containerRequestFilters;
    private final ExceptionMapper<?> exceptionMapper;
    private final boolean success;
    private final boolean responseSuccessfullyMapped;
    private final ExceptionCause exceptionCause;
    private final boolean responseWritten;


    @Override
    public ContainerRequest getContainerRequest() {
        return containerRequest;
    }

    @Override
    public ContainerResponse getContainerResponse() {
        return containerResponse;
    }

    @Override
    public Throwable getException() {
        return throwable;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public ExtendedUriInfo getUriInfo() {
        return extendedUriInfo;
    }

    @Override
    public ExceptionMapper<?> getExceptionMapper() {
        return exceptionMapper;
    }

    @Override
    public Iterable<ContainerRequestFilter> getContainerRequestFilters() {
        return containerRequestFilters;
    }

    @Override
    public Iterable<ContainerResponseFilter> getContainerResponseFilters() {
        return containerResponseFilters;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public boolean isResponseSuccessfullyMapped() {
        return responseSuccessfullyMapped;
    }

    @Override
    public ExceptionCause getExceptionCause() {
        return exceptionCause;
    }

    @Override
    public boolean isResponseWritten() {
        return responseWritten;
    }
}
