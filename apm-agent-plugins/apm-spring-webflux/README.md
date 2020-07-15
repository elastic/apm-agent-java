# Spring WebFlux plugin

This plugin provides instrumentation for [Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html) framework.

## TODO

Short term:
- [ ] cleanup existing TODOs in codebase
- [ ] capture data from HTTP response
- [ ] test & document sample application
    - run with agent CLI -> should instrument and cover all requests
    - run with agent attach -> should have similar behavior as above.
    - add benchmark mode (might not be required yet)
- [ ] transaction activation during request processing
    - testing for general context propagation with reactor hooks ?
    - what about limit coverage and use hooks for that ?

- [ ] Integrate with Servlet transaction when deployed to a servlet container
- [ ] Performance benchmarks & optimization
- [ ] Context propagation for Flux/Mono (reactor)
- [ ] Context propagation: capture upstream transaction HTTP headers (if any)
- [ ] Webflux client instrumentation
    - [ ] create spans to wrap HTTP request execution
    - [ ] send current transaction/span IDs to HTTP headers

## Implementation notes


## Test application

The `apm-spring-webflux-testapp` module provides a standalone spring boot application.

In order to run it, you can:
- run spring-boot maven plugin
    ```
    mvn spring-boot:run
    ```
- package it as an executable jar and run it
    ```
    mvn package
    java -jar ./target/apm-spring-webflux-testapp.jar
    ```
