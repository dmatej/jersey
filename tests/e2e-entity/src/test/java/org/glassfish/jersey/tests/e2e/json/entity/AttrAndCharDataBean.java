/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.jersey.tests.e2e.json.entity;

import java.util.Formatter;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

/**
 * @author Jakub Podlesak
 */
@SuppressWarnings({"StringEquality", "RedundantIfStatement"})
@XmlRootElement(name = "parent")
public class AttrAndCharDataBean {

    @XmlAttribute
    public String attr;
    @XmlValue
    public String value;

    public static Object createTestInstance() {
        AttrAndCharDataBean instance = new AttrAndCharDataBean();
        instance.attr = "aval";
        instance.value = "pval";
        return instance;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AttrAndCharDataBean other = (AttrAndCharDataBean) obj;
        if (this.attr != other.attr && (this.attr == null || !this.attr.equals(other.attr))) {
            return false;
        }
        if (this.value != other.value && (this.value == null || !this.value.equals(other.value))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + (this.attr != null ? this.attr.hashCode() : 0);
        hash = 79 * hash + (this.value != null ? this.value.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return (new Formatter()).format("ACD(a=%s, cd=%s)", attr, value).toString();
    }

}
