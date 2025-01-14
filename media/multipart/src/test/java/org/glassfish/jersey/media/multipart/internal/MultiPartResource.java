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

package org.glassfish.jersey.media.multipart.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.MultiPart;

/**
 * Resource file for {@link MultiPartReaderWriterTest}.
 *
 * @author Paul Sandoz
 * @author Michal Gajdos
 */
@Path("/multipart")
public class MultiPartResource {

    @Path("zero")
    @GET
    @Produces("text/plain")
    public String zero() {
        return "Hello, world\r\n";
    }

    @Path("one")
    @GET
    @Produces("multipart/mixed")
    public Response one() {
        MultiPart entity = new MultiPart();
        // Exercise manually adding part(s) to the bodyParts property
        BodyPart part = new BodyPart("This is the only segment", new MediaType("text", "plain"));
        entity.getBodyParts().add(part);
        return Response.ok(entity).type("multipart/mixed").build();
    }

    @Path("two")
    @GET
    @Produces("multipart/mixed")
    public Response two() {
        // Exercise builder pattern with default content type
        return Response.ok(new MultiPart()
                .bodyPart("This is the first segment", new MediaType("text", "plain"))
                .bodyPart("<outer><inner>value</inner></outer>", new MediaType("text", "xml"))).build();
    }

    @Path("three")
    @GET
    @Produces("multipart/mixed")
    public Response three() {
        // Exercise builder pattern with explicit content type
        MultiPartBean bean = new MultiPartBean("myname", "myvalue");
        return Response.ok(new MultiPart()
                .type(new MediaType("multipart", "mixed"))
                .bodyPart("This is the first segment", new MediaType("text", "plain"))
                .bodyPart(bean, new MediaType("x-application", "x-format"))).build();
    }

    @Path("four")
    @PUT
    @Produces("text/plain")
    public Response four(MultiPart multiPart) throws IOException {
        if (!(multiPart.getBodyParts().size() == 2)) {
            return Response.ok("FAILED:  Number of body parts is " + multiPart.getBodyParts().size() + " instead of 2").build();
        }

        BodyPart part0 = multiPart.getBodyParts().get(0);
        if (!(part0.getMediaType().equals(new MediaType("text", "plain")))) {
            return Response.ok("FAILED:  First media type is " + part0.getMediaType()).build();
        }

        BodyPart part1 = multiPart.getBodyParts().get(1);
        if (!(part1.getMediaType().equals(new MediaType("x-application", "x-format")))) {
            return Response.ok("FAILED:  Second media type is " + part1.getMediaType()).build();
        }

        BodyPartEntity bpe = (BodyPartEntity) part0.getEntity();
        StringBuilder sb = new StringBuilder();
        InputStream stream = bpe.getInputStream();
        InputStreamReader reader = new InputStreamReader(stream);
        char[] buffer = new char[2048];
        while (true) {
            int n = reader.read(buffer);
            if (n < 0) {
                break;
            }
            sb.append(buffer, 0, n);
        }
        if (!sb.toString().equals("This is the first segment")) {
            return Response.ok("FAILED:  First part name = " + sb.toString()).build();
        }

        MultiPartBean bean = part1.getEntityAs(MultiPartBean.class);
        if (!bean.getName().equals("myname")) {
            return Response.ok("FAILED:  Second part name = " + bean.getName()).build();
        }
        if (!bean.getValue().equals("myvalue")) {
            return Response.ok("FAILED:  Second part value = " + bean.getValue()).build();
        }
        return Response.ok("SUCCESS:  All tests passed").build();
    }

    // Note - this should never actually get reached, because the client
    // is trying to post a MultiPart with no body parts inside, and that
    // should throw a client side exception
    @Path("six")
    @POST
    @Consumes("multipart/mixed")
    @Produces("text/plain")
    public Response six(MultiPart multiPart) {
        String response = "All OK";
        if (!"multipart".equals(multiPart.getMediaType().getType())
                || !"mixed".equals(multiPart.getMediaType().getSubtype())) {
            response = "MultiPart media type is " + multiPart.getMediaType().toString();
        } else if (multiPart.getBodyParts().size() != 0) {
            response = "Got " + multiPart.getBodyParts().size() + " body parts instead of zero";
        }
        return Response.ok(response).build();
    }

    @Path("ten")
    @PUT
    @Consumes("multipart/mixed")
    @Produces("text/plain")
    public Response ten(MultiPart mp) {
        if (!(mp.getBodyParts().size() == 2)) {
            return Response.ok("FAILED:  Body part count is " + mp.getBodyParts().size() + " instead of 2").build();
        } else if (!(mp.getBodyParts().get(1).getEntity() instanceof BodyPartEntity)) {
            return Response.ok("FAILED:  Second body part is " + mp.getBodyParts().get(1).getClass().getName()
                    + " instead of BodyPartEntity").build();
        }
        BodyPartEntity bpe = (BodyPartEntity) mp.getBodyParts().get(1).getEntity();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream stream = bpe.getInputStream();
            byte[] buffer = new byte[2048];
            while (true) {
                int n = stream.read(buffer);
                if (n < 0) {
                    break;
                }
                baos.write(buffer, 0, n);
            }
            if (baos.toByteArray().length > 0) {
                return Response.ok("FAILED:  Second body part had " + baos.toByteArray().length + " bytes instead of 0").build();
            }
            return Response.ok("SUCCESS:  All tests passed").build();
        } catch (IOException e) {
            return Response.ok("FAILED:  Threw IOException").build();
        }
    }

    // Echo back a body part whose content may or may not exceed the size
    // of the buffer threshold
    @Path("eleven")
    @PUT
    @Consumes("multipart/mixed")
    @Produces("multipart/mixed")
    public Response eleven(MultiPart multiPart) throws IOException {
        BodyPartEntity bpe = (BodyPartEntity) multiPart.getBodyParts().get(0).getEntity();
        StringBuilder sb = new StringBuilder();
        InputStream stream = bpe.getInputStream();
        InputStreamReader reader = new InputStreamReader(stream);
        char[] buffer = new char[2048];
        while (true) {
            int n = reader.read(buffer);
            if (n < 0) {
                break;
            }
            sb.append(buffer, 0, n);
        }
        return Response.ok(new MultiPart().bodyPart(sb.toString(), MediaType.TEXT_PLAIN_TYPE))
                .type(new MediaType("multipart", "mixed")).build();
    }

    // Echo back the multipart that was sent
    @Path("twelve")
    @PUT
    @Consumes("multipart/mixed")
    @Produces("multipart/mixed")
    public MultiPart twelve(MultiPart multiPart) throws IOException {
        return multiPart;
    }

    // Call clean up explicitly
    @Path("thirteen")
    @PUT
    @Consumes("multipart/mixed")
    @Produces("multipart/mixed")
    public String thirteen(MultiPart multiPart) throws IOException {
        multiPart.cleanup();
        return "cleanup";
    }

    @GET
    @Path("etag")
    @Produces("multipart/mixed")
    public Response etag() {
        MultiPart entity = new MultiPart();
        // Exercise manually adding part(s) to the bodyParts property
        BodyPart part = new BodyPart("This is the only segment", new MediaType("text", "plain"));
        part.getHeaders().add("ETag", "\"value\"");
        entity.getBodyParts().add(part);
        return Response.ok(entity).type("multipart/mixed").build();
    }
}
