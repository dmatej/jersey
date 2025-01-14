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

package org.glassfish.jersey.test.util.server;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;

import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.RuntimeDelegate;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.message.MessageBodyWorkers;
import org.glassfish.jersey.message.internal.HeaderUtils;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;

/**
 * Used by unit tests / benchmarks to create mock {@link org.glassfish.jersey.server.ContainerRequest} instances.
 *
 * @author Michal Gajdos
 * @author Martin Matula
 * @since 2.17
 */
public final class ContainerRequestBuilder {

    /**
     * Create new Jersey container request context builder. The builder and built request context are supposed to be used only
     * for testing purposes.
     *
     * @param requestUri    request URI.
     * @param method        request HTTP method name.
     * @param configuration container request configuration
     * @return new builder instance.
     */
    public static ContainerRequestBuilder from(final String requestUri,
                                               final String method,
                                               final Configuration configuration) {
        return from(null, requestUri, method, configuration);
    }

    /**
     * Create new Jersey container request context builder. The builder and built request context are supposed to be used only
     * for testing purposes.
     *
     * @param baseUri       base application URI.
     * @param requestUri    request URI.
     * @param method        request HTTP method name.
     * @param configuration container request configuration
     * @return new builder instance.
     */
    public static ContainerRequestBuilder from(final String baseUri,
                                               final String requestUri,
                                               final String method,
                                               final Configuration configuration) {
        return from(baseUri, requestUri, method, null, null, configuration);
    }

    /**
     * Create new Jersey container request context builder. The builder and built request context are supposed to be used only
     * for testing purposes.
     *
     * @param baseUri            base application URI.
     * @param requestUri         request URI.
     * @param method             request HTTP method name.
     * @param securityContext    security context of the current request. May be {@code null}.
     *                           The {@link SecurityContext#getUserPrincipal()} must return {@code null} if the current request
     *                           has not been authenticated by the container.
     * @param propertiesDelegate custom {@link PropertiesDelegate properties delegate} to be used by the context, may be
     *                           {@code null}.
     * @param configuration      container request configuration
     * @return new builder instance.
     */
    public static ContainerRequestBuilder from(final String baseUri,
                                               final String requestUri,
                                               final String method,
                                               final SecurityContext securityContext,
                                               final PropertiesDelegate propertiesDelegate,
                                               final Configuration configuration) {
        return new ContainerRequestBuilder(baseUri, requestUri, method, securityContext, propertiesDelegate, configuration);
    }

    /**
     * Create new Jersey container request context builder. The builder and built request context are supposed to be used only
     * for testing purposes.
     *
     * @param requestUri    request URI.
     * @param method        request HTTP method name.
     * @param configuration container request configuration
     * @return new builder instance.
     */
    public static ContainerRequestBuilder from(final URI requestUri,
                                               final String method,
                                               final Configuration configuration) {
        return from(null, requestUri, method, configuration);
    }

    /**
     * Create new Jersey container request context builder. The builder and built request context are supposed to be used only
     * for testing purposes.
     *
     * @param baseUri       base application URI.
     * @param requestUri    request URI.
     * @param method        request HTTP method name.
     * @param configuration container request configuration
     * @return new builder instance.
     */
    public static ContainerRequestBuilder from(final URI baseUri,
                                               final URI requestUri,
                                               final String method,
                                               final Configuration configuration) {
        return from(baseUri, requestUri, method, null, null, configuration);
    }

    /**
     * Create new Jersey container request context builder. The builder and built request context are supposed to be used only
     * for testing purposes.
     *
     * @param baseUri            base application URI.
     * @param requestUri         request URI.
     * @param method             request HTTP method name.
     * @param securityContext    security context of the current request. May be {@code null}.
     *                           The {@link SecurityContext#getUserPrincipal()} must return {@code null} if the current request
     *                           has not been authenticated by the container.
     * @param propertiesDelegate custom {@link PropertiesDelegate properties delegate} to be used by the context, may be
     *                           {@code null}.
     * @param configuration      container request configuration
     * @return new builder instance.
     */
    public static ContainerRequestBuilder from(final URI baseUri,
                                               final URI requestUri,
                                               final String method,
                                               final SecurityContext securityContext,
                                               final PropertiesDelegate propertiesDelegate,
                                               final Configuration configuration) {
        return new ContainerRequestBuilder(baseUri, requestUri, method, securityContext, propertiesDelegate, configuration);
    }

    /**
     * Prepend a leading slash to given URI.
     *
     * @param uri URI to be modified.
     * @return URI with a leading slash prepended, if not absolute.
     */
    private static URI slash(final URI uri) {
        final String strUri = uri.toString();
        return (uri.isAbsolute() || strUri.charAt(0) == '/') ? uri : URI.create('/' + strUri);
    }

    private final TestContainerRequest request;
    private final Configuration configuration;

    /**
     * Create new Jersey container request context builder.
     * @param baseUri            base application URI.
     * @param requestUri         request URI.
     * @param method             request HTTP method name.
     * @param securityContext    security context of the current request. May be {@code null}.
     *                           The {@link SecurityContext#getUserPrincipal()} must return {@code null} if the current request
     *                           has not been authenticated by the container.
     * @param propertiesDelegate custom {@link PropertiesDelegate properties delegate} to be used by the context, may be
     *                           {@code null}.
     * @param configuration      container request configuration
     */
    private ContainerRequestBuilder(final String baseUri,
                                    final String requestUri,
                                    final String method,
                                    final SecurityContext securityContext,
                                    final PropertiesDelegate propertiesDelegate,
                                    final Configuration configuration) {
        this(baseUri == null || baseUri.isEmpty() ? null : URI.create(baseUri),
                URI.create(requestUri),
                method,
                securityContext,
                propertiesDelegate,
                configuration);
    }

    /**
     * Create new Jersey container request context builder.
     *
     * @param baseUri            base application URI.
     * @param requestUri         request URI.
     * @param method             request HTTP method name.
     * @param securityContext    security context of the current request. May be {@code null}.
     *                           The {@link SecurityContext#getUserPrincipal()} must return {@code null} if the current request
     *                           has not been authenticated by the container.
     * @param propertiesDelegate custom {@link PropertiesDelegate properties delegate} to be used by the context, may be
     *                           {@code null}.
     * @param configuration      container request configuration
     */
    private ContainerRequestBuilder(final URI baseUri,
                                    final URI requestUri,
                                    final String method,
                                    final SecurityContext securityContext,
                                    final PropertiesDelegate propertiesDelegate,
                                    final Configuration configuration) {
        this.configuration = configuration;
        request = new TestContainerRequest(baseUri,
                slash(requestUri),
                method,
                securityContext,
                propertiesDelegate == null ? new MapPropertiesDelegate() : propertiesDelegate,
                configuration);
    }

    /**
     * Build a Jersey container request context. The build container request can be used in
     * {@link org.glassfish.jersey.server.ApplicationHandler#apply(org.glassfish.jersey.server.ContainerRequest)} method to obtain
     * response from Jersey.
     *
     * @return testing container request context.
     */
    public ContainerRequest build() {
        return request;
    }

    /**
     * Add the accepted response media types.
     *
     * @param mediaTypes accepted response media types. If {@code cookie} is {@code null} then all current headers of the same
     *                   name will be removed.
     * @return the updated builder.
     */
    public ContainerRequestBuilder accept(final String... mediaTypes) {
        putHeaders(HttpHeaders.ACCEPT, mediaTypes);
        return this;
    }

    /**
     * Add the accepted response media types.
     *
     * @param mediaTypes accepted response media types. If {@code cookie} is {@code null} then all current headers of the same
     *                   name will be removed.
     * @return the updated builder.
     */
    public ContainerRequestBuilder accept(final MediaType... mediaTypes) {
        putHeaders(HttpHeaders.ACCEPT, (Object[]) mediaTypes);
        return this;
    }

    /**
     * Set the request entity input stream. Entity {@code null} values are ignored.
     *
     * @param stream request entity input stream.
     * @return the updated builder.
     */
    public ContainerRequestBuilder entity(final InputStream stream) {
        if (stream != null) {
            request.setEntity(stream);
        }
        return this;
    }

    /**
     * Set the request entity and entity input stream. Entity {@code null} values are ignored.
     * <p/>
     * {@link org.glassfish.jersey.message.MessageBodyWorkers} are used to transform the object into entity input stream required
     * to process the request.
     * <p/>
     * NOTE: Entity transformation into entity input stream doesn't have any impact on benchmarks.
     *
     * @param entity  request entity instance.
     * @param workers message body workers to transform entity into entity input stream.
     * @return the updated builder.
     */
    public ContainerRequestBuilder entity(final Object entity, final MessageBodyWorkers workers) {
        if (entity != null) {
            if (entity instanceof InputStream) {
                return entity((InputStream) entity);
            } else {
                request.setEntity(entity, workers);
            }
        }
        return this;
    }

    /**
     * Set the request entity and entity input stream. Entity {@code null} values are ignored.
     * <p/>
     * {@link org.glassfish.jersey.server.ApplicationHandler} is required to obtain
     * {@link org.glassfish.jersey.message.MessageBodyWorkers} to transform the object into entity input stream used for
     * processing the request.
     * <p/>
     * NOTE: Entity transformation into entity input stream doesn't have any impact on benchmarks.
     *
     * @param entity  request entity instance.
     * @param handler application handler to obtain message body workers from.
     * @return the updated builder.
     */
    public ContainerRequestBuilder entity(final Object entity, final ApplicationHandler handler) {
        return entity(entity, handler.getInjectionManager().getInstance(MessageBodyWorkers.class));
    }

    /**
     * Add content type of the entity.
     *
     * @param contentType content media type.
     * @return the updated builder.
     */
    public ContainerRequestBuilder type(final String contentType) {
        request.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, contentType);
        return this;

    }

    /**
     * Add content type of the entity.
     *
     * @param contentType content media type.
     * @return the updated builder.
     */
    public ContainerRequestBuilder type(final MediaType contentType) {
        request.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, HeaderUtils.asString(contentType, configuration));
        return this;
    }

    /**
     * Add an arbitrary header.
     *
     * @param name  the name of the header
     * @param value the value of the header, the header will be serialized
     *              using a {@link jakarta.ws.rs.ext.RuntimeDelegate.HeaderDelegate} if
     *              one is available via {@link jakarta.ws.rs.ext.RuntimeDelegate#createHeaderDelegate(java.lang.Class)}
     *              for the class of {@code value} or using its {@code toString} method
     *              if a header delegate is not available. If {@code value} is {@code null}
     *              then all current headers of the same name will be removed.
     * @return the updated builder.
     */
    public ContainerRequestBuilder header(final String name, final Object value) {
        putHeader(name, value);
        return this;
    }

    /**
     * Add a cookie to be set.
     *
     * @param cookie to be set. If {@code cookie} is {@code null} then all current headers of the same name will be removed.
     * @return the updated builder.
     */
    public ContainerRequestBuilder cookie(final Cookie cookie) {
        putHeader(HttpHeaders.COOKIE, cookie);
        return this;
    }

    /**
     * Add cookies to be set.
     *
     * @param cookies to be set. If {@code cookies} is {@code null} then all current headers of the same name will be removed.
     * @return the updated builder.
     */
    public ContainerRequestBuilder cookies(final Cookie... cookies) {
        putHeaders(HttpHeaders.COOKIE, (Object[]) cookies);
        return this;
    }

    private void putHeader(final String name, final Object value) {
        if (value == null) {
            request.getHeaders().remove(name);
            return;
        }
        request.header(name, HeaderUtils.asString(value, configuration));
    }

    private void putHeaders(final String name, final Object... values) {
        if (values == null) {
            request.getHeaders().remove(name);
            return;
        }
        request.getHeaders().addAll(name, HeaderUtils.asStringList(Arrays.asList(values), configuration));
    }

    private void putHeaders(final String name, final String... values) {
        if (values == null) {
            request.getHeaders().remove(name);
            return;
        }
        request.getHeaders().addAll(name, values);
    }
}
