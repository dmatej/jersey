/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.test.microprofile.restclient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.MediaType;

/**
 * Created by David Kral.
 */
public class ApplicationResourceImpl implements ApplicationResource {
    @Override
    public List<String> getValue() {
        return Arrays.asList("This is default value!", "Test");
    }

    @Override
    public Map<String, String> getTestMap() {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("firstKey", "firstValue");
        testMap.put("secondKey", "secondValue");
        return testMap;
    }

    @Override
    public String postAppendValue(String value) {
        return null;
    }

    @Override
    public JsonValue someJsonOperation(JsonValue jsonValue) {
        return null;
    }

    @Override
    public JsonValue jsonValue() {
        return Json.createObjectBuilder().add("someKey", "Some value").build();
    }

    @Override
    public String methodContentType(MediaType contentType, String entity) {
        return null;
    }

    @Override
    public String regex(String content) {
        return content;
    }

    @Override
    public String regex0(String context0, String context1) {
        return context0 + "_" + context1;
    }
}
