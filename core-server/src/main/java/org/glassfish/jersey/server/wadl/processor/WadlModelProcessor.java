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

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.xml.bind.Marshaller;

import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.message.internal.MediaTypes;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceModel;
import org.glassfish.jersey.server.model.RuntimeResource;
import org.glassfish.jersey.server.model.internal.ModelProcessorUtil;
import org.glassfish.jersey.server.wadl.WadlApplicationContext;
import org.glassfish.jersey.server.wadl.internal.WadlResource;
import org.glassfish.jersey.server.wadl.internal.WadlUtils;

import com.sun.research.ws.wadl.Application;

/**
 * WADL {@link ModelProcessor model processor} which enhance resource model by WADL related resources (like "/application.wadl").
 * The provider should be registered using
 * {@link org.glassfish.jersey.server.wadl.internal.WadlAutoDiscoverable} or by
 * {@link org.glassfish.jersey.server.wadl.WadlFeature} if auto-discovery is disabled.
 *
 * @author Miroslav Fuksa
 *
 */
@Priority(10000)
public class WadlModelProcessor implements ModelProcessor {


    private final List<ModelProcessorUtil.Method> methodList;

    /**
     * Create new WADL model processor instance.
     */
    public WadlModelProcessor() {
        methodList = new ArrayList<>();
        methodList.add(new ModelProcessorUtil.Method(HttpMethod.OPTIONS, MediaType.WILDCARD_TYPE, MediaTypes.WADL_TYPE,
                OptionsHandler.class));
    }


    @Override
    public ResourceModel processResourceModel(final ResourceModel resourceModel, final Configuration configuration) {
        final boolean disabled = PropertiesHelper.isProperty(configuration.getProperty(ServerProperties.WADL_FEATURE_DISABLE));
        if (disabled) {
            return resourceModel;
        }

        final ResourceModel.Builder builder = ModelProcessorUtil.enhanceResourceModel(resourceModel, false, methodList, true);

        // Do not add WadlResource if already present in the classes (i.e. added during scanning).
        if (!configuration.getClasses().contains(WadlResource.class)) {
            final Resource wadlResource = Resource.builder(WadlResource.class).build();
            builder.addResource(wadlResource);
        }

        return builder.build();

    }

    /**
     * OPTIONS resource method handler that serves resource WADL.
     */
    public static class OptionsHandler implements Inflector<ContainerRequestContext, Response> {
        private final String lastModified =
                new SimpleDateFormat(WadlResource.HTTPDATEFORMAT).format(new Date());

        @Inject
        private Provider<ExtendedUriInfo> extendedUriInfo;

        @Context
        private WadlApplicationContext wadlApplicationContext;

        @Override
        public Response apply(ContainerRequestContext containerRequestContext) {

            final RuntimeResource resource = extendedUriInfo.get().getMatchedRuntimeResources().get(0);
            // TODO: support multiple resources, see ignored tests in WadlResourceTest.Wadl8Test
            final UriInfo uriInfo = containerRequestContext.getUriInfo();

            final Application wadlApplication = wadlApplicationContext.getApplication(uriInfo,
                    resource.getResources().get(0), WadlUtils.isDetailedWadlRequested(uriInfo));

            if (wadlApplication == null) {
                // wadlApplication can be null if limited WADL is requested and all content
                // of wadlApplication is invisible in limited WADL
                return Response.status(Response.Status.NOT_FOUND).build();

            }

            byte[] bytes;
            try {
                final Marshaller marshaller = wadlApplicationContext.getJAXBContext().createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                marshaller.marshal(wadlApplication, os);
                bytes = os.toByteArray();
                os.close();
            } catch (Exception e) {
                throw new ProcessingException("Could not marshal the wadl Application.", e);
            }

            return Response.ok()
                    .type(MediaTypes.WADL_TYPE)
                    .allow(ModelProcessorUtil.getAllowedMethods(resource))
                    .header("Last-modified", lastModified)
                    .entity(bytes)
                    .build();
        }
    }

    @Override
    public ResourceModel processSubResource(ResourceModel resourceModel, Configuration configuration) {
        final boolean disabled = PropertiesHelper.isProperty(configuration.getProperty(ServerProperties.WADL_FEATURE_DISABLE));
        if (disabled) {
            return resourceModel;
        }
        return ModelProcessorUtil.enhanceResourceModel(resourceModel, true, methodList, true).build();
    }
}
