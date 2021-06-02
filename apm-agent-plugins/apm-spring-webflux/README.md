# Spring WebFlux plugin

This plugin provides instrumentation
for [Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html) framework.

## Features

Server-side transactions instrumentation with support for:

- Annotated controllers
- Functional routes
- deployment within a Servlet container/application-server

## Test application

### Features

- The embedded server (netty or tomcat) can be set at startup
- Endpoints definition with two variants: annotated and functional routes
- Provides a client API to call itself, which relies on Webflux client
- Provides a CLI client to execute a set of sample requests

### Build and run

The `apm-spring-webflux-testapp` module provides a standalone spring boot application.

In order to run it, you can:

- run spring-boot maven plugin
    ```
    mvn spring-boot:run
    ```
- package it as an executable jar and run it
    ```
    mvn package
    java -jar ./target/apm-spring-webflux-testapp-standalone.jar
    ```

Optional parameters

- `--port 8080` set server port, use `-1` for a random port
- `--server netty` set server implementation, valid values are `netty` and `tomcat`
- `--count N` execute `N` sets of sample requests against application, defaults to `0`
- `--client` execute client sample requests against an already-started server, implies `--count 1` if not set explicitly
