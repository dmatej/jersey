/*
 * Copyright (c) 2014, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.tests.performance.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.ext.ContextResolver;

import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.moxy.json.MoxyJsonConfig;
import org.glassfish.jersey.server.ResourceConfig;

import org.glassfish.grizzly.http.server.HttpServer;

/**
 * Application for generating custom testing data files for Jersey performance tests.
 *
 * <p>Creates set of files containing plain text, json and xml in various predefined sizes:
 * 1kB, 5kB, 10kB, 1MB and optionally 1GB. The 1GB file has to be enabled by changing the constant
 * {@code GENERATE_ALSO_GIGABYTE_DATASETS} to true.</p>
 *
 * <p>The sizes are the MINIMAL sizes, the generation stops after reaching the given size,
 * but does not truncate the most recently
 * generated entity. For simple testing beans the size difference from the predefined treshold is minimal,
 * whereas for very complex testing beans the difference can be significant.</p>
 *
 * <p>MOXy is used for creating XML and JSON from the testing beans.</p>
 *
 * <p>Run the generation by invoking {@code mvn clean compile} and {@code mvn exec:java} commands in the module root folder.</p>
 *
 * @author Adam Lindenthal
 */
public class TestDataGeneratorApp {

    /** change the value to true to generate also 1GB files; can be time consuming and takes additional 3GB of disk
     * space (1GB json, 1GB xml and 1GB text) */
    private static final boolean GENERATE_ALSO_GIGABYTE_DATASETS = false;

    /** path where the generated files should be stored, including the final slash */
    private static final String FILE_PATH = "";

    /** specifies how the outputs generated from one bean should be separated from each other in the output files */
    public static final String ENTITY_SEPARATOR = "\n\n";

    private static final Logger LOG = Logger.getLogger(TestDataGeneratorApp.class.getName());
    private static final URI BASE_URI = URI.create("http://localhost:8080/");
    private static URI baseUri;

    public static void main(final String[] args) throws Exception {
        baseUri = args.length > 0 ? URI.create(args[0]) : BASE_URI;
        final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, createApp());

        LOG.info("Jersey performance test data generation - application started.");

        try {
            // generate text files - 1kb, 5kb, 10kb, 1MB and optionally 1GB
            generateFile("simple/text", 1024, FILE_PATH + "custom-1kb.text");
            generateFile("simple/text", 5 * 1024, FILE_PATH + "custom-5kb.text");
            generateFile("simple/text", 10 * 1024, FILE_PATH + "custom-10kb.text");
            generateFile("simple/text", 1024 * 1024, FILE_PATH + "custom-1MB.text");
            if (GENERATE_ALSO_GIGABYTE_DATASETS) {
                generateFile("text", 1024 * 1024 * 1024, FILE_PATH + "custom-1GB.text");
            }

            // generate json files - 1kb, 5kb, 10kb, 1MB and optionally 1GB
            generateFile("simple/json", 1024, FILE_PATH + "custom-1kb.json");
            generateFile("simple/json", 5 * 1024, FILE_PATH + "custom-5kb.json");
            generateFile("simple/json", 10 * 1024, FILE_PATH + "custom-10kb.json");
            generateFile("simple/json", 1024 * 1024, FILE_PATH + "custom-1MB.json");
            if (GENERATE_ALSO_GIGABYTE_DATASETS) {
                generateFile("simple/json", 1024 * 1024 * 1024, FILE_PATH + "custom-1GB.json");
            }

            // generate xml files - 1kb, 5kb, 10kb, 1MB and optionally 1GB
            generateFile("simple/xml", 1024, FILE_PATH + "custom-1kb.xml");
            generateFile("simple/xml", 5 * 1024, FILE_PATH + "custom-5kb.xml");
            generateFile("simple/xml", 10 * 1024, FILE_PATH + "custom-10kb.xml");
            generateFile("simple/xml", 1024 * 1024, FILE_PATH + "custom-1MB.xml");
            if (GENERATE_ALSO_GIGABYTE_DATASETS) {
                generateFile("simple/xml", 1024 * 1024 * 1024, FILE_PATH + "custom-1GB.json");
            }
        } catch (final IOException e) {
            LOG.log(Level.SEVERE, "An error occurred during test data generation. ", e);
        }
        server.shutdown();
    }

    public static ResourceConfig createApp() {
        return new ResourceConfig()
                .packages("org.glassfish.jersey.tests.performance.tools")
                .register(createMoxyJsonResolver());
    }

    public static ContextResolver<MoxyJsonConfig> createMoxyJsonResolver() {
        final MoxyJsonConfig moxyJsonConfig = new MoxyJsonConfig();
        final Map<String, String> namespacePrefixMapper = new HashMap<>(1);
        namespacePrefixMapper.put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
        moxyJsonConfig.setNamespacePrefixMapper(namespacePrefixMapper).setNamespaceSeparator(':');
        return moxyJsonConfig.resolver();
    }

    public static void generateFile(final String resourceRelativeUrl, final int minimalSize, final String fileName)
            throws IOException {
        LOG.info("Generating file " + fileName);
        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(baseUri).path("generate").path(resourceRelativeUrl);

        final File file = new File(fileName);
        final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), Charset.forName("UTF-8")));

        int actualSize = 0;
        while (actualSize < minimalSize) {
            final String response = target.request().get(String.class);
            writer.write(response + ENTITY_SEPARATOR);
            actualSize += response.length();
        }

        writer.flush();
        writer.close();
    }

}
