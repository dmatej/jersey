/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.jersey_ejb.resources;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import org.glassfish.jersey.examples.jersey_ejb.entities.Message;
import org.glassfish.jersey.examples.jersey_ejb.exceptions.CustomNotFoundException;

/**
 * A stateless EJB bean to handle REST requests to the messages resource.
 * Messages are stored in the injected EJB singleton instance.
 *
 * @author Pavel Bucek
 */
@Stateless
public class MessageBoardResourceBean {

    @Context
    private UriInfo ui;
    @EJB
    MessageHolderSingletonBean singleton;

    /**
     * Returns a list of all messages stored in the internal message holder.
     */
    @GET
    public List<Message> getMessages() {
        return singleton.getMessages();
    }

    @POST
    public Response addMessage(String msg) throws URISyntaxException {
        Message m = singleton.addMessage(msg);

        URI msgURI = ui.getRequestUriBuilder().path(Integer.toString(m.getUniqueId())).build();

        return Response.created(msgURI).build();
    }

    @Path("{msgNum}")
    @GET
    public Message getMessage(@PathParam("msgNum") int msgNum) {
        Message m = singleton.getMessage(msgNum);

        if (m == null) {
            // This exception will be passed through to the JAX-RS runtime
            // No other runtime exception will behave this way unless the
            // exception is annotated with jakarta.ejb.ApplicationException
            throw new NotFoundException();
        }

        return m;

    }

    @Path("{msgNum}")
    @DELETE
    public void deleteMessage(@PathParam("msgNum") int msgNum) throws CustomNotFoundException {
        boolean deleted = singleton.deleteMessage(msgNum);

        if (!deleted) {
            // This exception will be mapped to a 404 response
            throw new CustomNotFoundException();
        }
    }
}





