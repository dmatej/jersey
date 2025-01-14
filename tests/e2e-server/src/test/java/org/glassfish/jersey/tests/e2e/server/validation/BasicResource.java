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

package org.glassfish.jersey.tests.e2e.server.validation;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * @author Michal Gajdos
 */
@Path("beanvalidation")
public class BasicResource {

    @NotNull
    @Context
    private ResourceContext resourceContext;

    private String getInputParamsResponse(final String path,
                                          final String matrix,
                                          final String query,
                                          final String header,
                                          final String cookie,
                                          final String form) {

        return path + '_' + matrix + '_' + query + '_' + header + '_' + cookie + '_' + form;
    }

    @POST
    @Path("basicParam/{path: .*}")
    @Consumes("application/x-www-form-urlencoded")
    @SuppressWarnings("UnusedParameters")
    public String validateParametersBasicConstraint(
            @NotNull @PathParam("path") final String path,
            @NotNull @MatrixParam("matrix") final String matrix,
            @NotNull @QueryParam("query") final String query,
            @NotNull @HeaderParam("header") final String header,
            @NotNull @CookieParam("cookie") final String cookie,
            @NotNull @FormParam("form") final String form,
            @NotNull @Context final Request request) {

        return getInputParamsResponse(path, matrix, query, header, cookie, form);
    }

    @POST
    @Path("basicDefaultParam/{path: .*}")
    @Consumes("application/x-www-form-urlencoded")
    @SuppressWarnings("UnusedParameters")
    public String validateParametersDefaultBasicConstraint(
            @NotNull @DefaultValue("pathParam") @PathParam("path") final String path,
            @NotNull @DefaultValue("matrixParam") @MatrixParam("matrix") final String matrix,
            @NotNull @DefaultValue("queryParam") @QueryParam("query") final String query,
            @NotNull @DefaultValue("headerParam") @HeaderParam("header") final String header,
            @NotNull @DefaultValue("cookieParam") @CookieParam("cookie") final String cookie,
            @NotNull @DefaultValue("formParam") @FormParam("form") final String form,
            @NotNull @Context final Request request) {

        return getInputParamsResponse(path, matrix, query, header, cookie, form);
    }

    @POST
    @Path("customParam/{path: .*}")
    @Consumes("application/x-www-form-urlencoded")
    public String validateParametersCustomConstraint(
            @ParamConstraint @PathParam("path") final String path,
            @ParamConstraint @MatrixParam("matrix") final String matrix,
            @ParamConstraint @QueryParam("query") final String query,
            @ParamConstraint @HeaderParam("header") final String header,
            @ParamConstraint @CookieParam("cookie") final String cookie,
            @ParamConstraint @FormParam("form") final String form) {

        return getInputParamsResponse(path, matrix, query, header, cookie, form);
    }

    @POST
    @Path("mixedParam/{path: .*}")
    @Consumes("application/x-www-form-urlencoded")
    public String validateParametersMixedConstraint(
            @Size(max = 11) @ParamConstraint @PathParam("path") final String path,
            @Size(max = 11) @ParamConstraint @MatrixParam("matrix") final String matrix,
            @Size(max = 11) @ParamConstraint @QueryParam("query") final String query,
            @Size(max = 11) @ParamConstraint @HeaderParam("header") final String header,
            @Size(max = 11) @ParamConstraint @CookieParam("cookie") final String cookie,
            @Size(max = 11) @ParamConstraint @FormParam("form") final String form) {

        return getInputParamsResponse(path, matrix, query, header, cookie, form);
    }

    @POST
    @Path("multipleParam/{path: .*}")
    @Consumes("application/x-www-form-urlencoded")
    public String validateParametersMultipleConstraint(
            @MultipleParamConstraint @PathParam("path") final String path,
            @MultipleParamConstraint @MatrixParam("matrix") final String matrix,
            @MultipleParamConstraint @QueryParam("query") final String query,
            @MultipleParamConstraint @HeaderParam("header") final String header,
            @MultipleParamConstraint @CookieParam("cookie") final String cookie,
            @MultipleParamConstraint @FormParam("form") final String form) {

        return getInputParamsResponse(path, matrix, query, header, cookie, form);
    }

    @POST
    @Path("emptyBeanParam")
    @Consumes("application/contactBean")
    @Produces("application/contactBean")
    public ContactBean validateEmptyBeanParamConstraint(@NotNull final ContactBean bean) {
        return bean;
    }

    @POST
    @Path("validBeanParam")
    @Consumes("application/contactBean")
    @Produces("application/contactBean")
    public ContactBean validateValidBeanParamConstraint(@NotNull @Valid final ContactBean bean) {
        return bean;
    }

    @POST
    @Path("customBeanParam")
    @Consumes("application/contactBean")
    @Produces("application/contactBean")
    public ContactBean validateCustomBeanParamConstraint(@OneContact final ContactBean bean) {
        return bean;
    }

    @POST
    @Path("emptyBeanResponse")
    @Consumes("application/contactBean")
    @Produces("application/contactBean")
    @NotNull
    public ContactBean validateEmptyBeanResponseConstraint(final ContactBean bean) {
        return bean;
    }

    @POST
    @Path("validBeanResponse")
    @Consumes("application/contactBean")
    @Produces("application/contactBean")
    @NotNull
    @Valid
    public ContactBean validateValidBeanResponseConstraint(final ContactBean bean) {
        return bean;
    }

    @POST
    @Path("validBeanWrappedInResponse")
    @Consumes("application/contactBean")
    @Produces("application/contactBean")
    @NotNull
    @Valid
    public Response validateValidBeanWrappedInResponseConstraint(final ContactBean bean) {
        return Response.ok(bean).type("application/contactBean").build();
    }

    @POST
    @Path("customBeanResponse")
    @Consumes("application/contactBean")
    @Produces("application/contactBean")
    @OneContact
    public ContactBean validateCustomBeanResponseConstraint(final ContactBean bean) {
        return bean;
    }

    @GET
    @Path("invalidContext")
    @SuppressWarnings("UnusedParameters")
    public Response invalidContext(@Null @Context final Request request) {
        return Response.status(500).build();
    }

    @Path("sub/validResourceContextInstance")
    public BasicSubResource getSubResourceValidResourceContextInstance() {
        return resourceContext.initResource(new BasicSubResource(resourceContext));
    }

    @Path("sub/nullResourceContextInstance")
    public BasicSubResource getSubResourceNullResourceContextInstance() {
        return resourceContext.initResource(new BasicSubResource(null));
    }

    @Path("sub/nullResourceContextClass")
    public BasicSubResource getSubResourceNullResourceContextClass() {
        return resourceContext.getResource(BasicSubResource.class);
    }

    @Path("sub/wrong")
    public BasicBadSubResource getWrongSubResource() {
        return resourceContext.getResource(BasicBadSubResource.class);
    }
}
