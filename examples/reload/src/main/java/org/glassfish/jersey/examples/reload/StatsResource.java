/*
 * Copyright (c) 2012, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jersey.examples.reload;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/**
 *
 * @author Jakub Podlesak
 */
@Path("stats")
@Produces("text/plain")
public class StatsResource {

    @GET
    public String getStats() {
        return String.format("Arrivals resource hits: %d\nDepartures resource hits: %d",
                FlightsDB.arrivalsReqCount.get(), FlightsDB.departuresReqCount.get());
    }
}
