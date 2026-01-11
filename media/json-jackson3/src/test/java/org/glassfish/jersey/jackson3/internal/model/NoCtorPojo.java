package org.glassfish.jersey.jackson3.internal.model;

// Simple POJO that will fail deserialization without NoCtorDeserModule
public class NoCtorPojo {

    public String name;
    public String value;

    public NoCtorPojo(String name, String value) {
        this.name = name;
        this.value = value;
    }

}
