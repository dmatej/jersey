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

package org.glassfish.jersey.server.wadl.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceModel;
import org.glassfish.jersey.server.model.internal.ModelProcessorUtil;

/**
 * {@link ModelProcessor Model processor} enhancing {@link ResourceModel resource model} and {@link Resource sub resources}
 * by default OPTIONS methods defined by JAX-RS specification.
 *
 * @author Miroslav Fuksa
 */
@Priority(Integer.MAX_VALUE)
public class OptionsMethodProcessor implements ModelProcessor {

    private final List<ModelProcessorUtil.Method> methodList;

    /**
     * Creates new instance.
     */
    public OptionsMethodProcessor() {
        methodList = new ArrayList<>();
        methodList.add(new ModelProcessorUtil.Method(HttpMethod.OPTIONS, MediaType.WILDCARD_TYPE, MediaType.TEXT_PLAIN_TYPE,
                PlainTextOptionsInflector.class));

        methodList.add(new ModelProcessorUtil.Method(HttpMethod.OPTIONS, MediaType.WILDCARD_TYPE, MediaType.WILDCARD_TYPE,
                GenericOptionsInflector.class));
    }


    private static class PlainTextOptionsInflector implements Inflector<ContainerRequestContext, Response> {

        @Inject
        private Provider<ExtendedUriInfo> extendedUriInfo;

        @Override
        public Response apply(ContainerRequestContext containerRequestContext) {
            Set<String> allowedMethods = ModelProcessorUtil.getAllowedMethods(extendedUriInfo.get()
                    .getMatchedRuntimeResources().get(0));

            final String allowedList = allowedMethods.toString();
            final String optionsBody = allowedList.substring(1, allowedList.length() - 1);

            return Response.ok(optionsBody, MediaType.TEXT_PLAIN_TYPE)
                    .allow(allowedMethods)
                    .build();
        }
    }

    private static class GenericOptionsInflector implements Inflector<ContainerRequestContext, Response> {
        @Inject
        private Provider<ExtendedUriInfo> extendedUriInfo;

        @Override
        public Response apply(ContainerRequestContext containerRequestContext) {
            final Set<String> allowedMethods = ModelProcessorUtil.getAllowedMethods(
                    (extendedUriInfo.get().getMatchedRuntimeResources().get(0)));
            return Response.ok()
                    .allow(allowedMethods)
                    .header(HttpHeaders.CONTENT_LENGTH, "0")
                    .type(containerRequestContext.getAcceptableMediaTypes().get(0))
                    .build();
        }
    }

    @Override
    public ResourceModel processResourceModel(ResourceModel resourceModel, Configuration configuration) {
        return ModelProcessorUtil.enhanceResourceModel(resourceModel, false, methodList, true).build();
    }

    @Override
    public ResourceModel processSubResource(ResourceModel subResourceModel, Configuration configuration) {
        return ModelProcessorUtil.enhanceResourceModel(subResourceModel, true, methodList, true).build();
    }
}
