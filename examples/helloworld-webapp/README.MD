[//]: # " Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved. "
[//]: # " "
[//]: # " This program and the accompanying materials are made available under the "
[//]: # " terms of the Eclipse Distribution License v. 1.0, which is available at "
[//]: # " http://www.eclipse.org/org/documents/edl-v10.php. "
[//]: # " "
[//]: # " SPDX-License-Identifier: BSD-3-Clause "

Helloworld webapp Example
=========================

This example demonstrates how to create simple Jersey application,
package it into WAR file and how to run it using [Grizzly
container](https://projects.eclipse.org/projects/ee4j.grizzly).

Contents
--------

The mapping of the URI path space is presented in the following table:

URI path            | Resource class       | HTTP methods
------------------- | -------------------- | --------------
**_/helloworld_**   | HelloWorldResource   | GET

Application is configured by using web.xml, which registers
[jakarta.ws.rs.core.Application](https://jakarta.ee/specifications/restful-ws/3.0/apidocs/jakarta/ws/rs/core/Application.html)
descendant to get classes and singletons from it (see class MyApplication).

Running the Example
-------------------

Run the example as follows:

>     mvn clean compile exec:java

This deploys the example using [Grizzly](https://projects.eclipse.org/projects/ee4j.grizzly) container. You can access the application at:

- <http://localhost:8080/helloworld-webapp/helloworld>
