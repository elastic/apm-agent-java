# Spring WebFlux plugin

This plugin provides instrumentation for [Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html) framework.

## Features

Server-side transactions instrumentation with support for:
- Annotated controllers
- Functional routes

Supports both Servlet deployments and using other embedded servers.

## TODO

- cleanup existing TODOs in codebase
- test & document sample application
    - run with agent CLI -> should instrument and cover all requests
    - run with agent attach -> should have similar behavior as above.
    - add benchmark mode (might not be required yet)

Short term:

- [ ] transaction activation during request processing
    - testing for general context propagation with reactor hooks ?
    - what about limit coverage and use hooks for that ?

- [ ] Performance benchmarks & optimization
- [ ] Context propagation for Flux/Mono (reactor)
- [ ] Context propagation: capture upstream transaction HTTP headers (if any)
- [ ] Webflux client instrumentation >> delegate to another PR
    - [ ] create spans to wrap HTTP request execution
    - [ ] send current transaction/span IDs to HTTP headers
    - instrument all sub-classes of `ClientHttpConnector` seems a good start

## Implementation notes

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
