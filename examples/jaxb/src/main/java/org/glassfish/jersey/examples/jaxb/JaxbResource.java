/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.jaxb;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import jakarta.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.glassfish.jersey.message.XmlHeader;

/**
 * An example resource utilizing JAXB beans.
 *
 * @author Paul Sandoz
 */
@Path("jaxb")
@Produces("application/xml")
@Consumes("application/xml")
public class JaxbResource {

    @Path("XmlRootElement")
    @GET
    public JaxbXmlRootElement getRootElement() {
        return new JaxbXmlRootElement("xml root element");
    }

    @Path("XmlRootElementWithHeader")
    @GET
    @XmlHeader("<?xml-stylesheet type='text/xsl' href='foobar.xsl' ?>")
    public JaxbXmlRootElement getRootElementWithHeader() {
        return new JaxbXmlRootElement("xml root element");
    }

    @Path("XmlRootElement")
    @POST
    public JaxbXmlRootElement postRootElement(JaxbXmlRootElement r) {
        return r;
    }

    @Path("JAXBElement")
    @GET
    public JAXBElement<JaxbXmlType> getJAXBElement() {
        return new JAXBElement<JaxbXmlType>(
                new QName("jaxbXmlRootElement"),
                JaxbXmlType.class,
                new JaxbXmlType("xml type"));
    }

    @Path("JAXBElement")
    @POST
    public JAXBElement<JaxbXmlType> postJAXBElement(JAXBElement<JaxbXmlType> e) {
        return e;
    }

    @Path("XmlType")
    @POST
    public JAXBElement<JaxbXmlType> postXmlType(JaxbXmlType r) {
        return new JAXBElement<JaxbXmlType>(
                new QName("jaxbXmlRootElement"), JaxbXmlType.class, r);
    }
}
