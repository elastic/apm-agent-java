# next (1.7.0)

## Features

## Bug Fixes

# 1.6.0

## Related Announcements
 * Java APM Agent became part of the Cloud Foundry Java Buildpack as of [Release v4.19](https://github.com/cloudfoundry/java-buildpack/releases/tag/v4.19)
 
## Features
 * Support Apache HttpAsyncClient - span creation and cross-service trace context propagation
 * Added the `jvm.thread.count` metric, indicating the number of live threads in the JVM (daemon and non-daemon) 
 * Added support for WebLogic
 * Added support for Spring `@Scheduled` and EJB `@Schedule` annotations - [#569](https://github.com/elastic/apm-agent-java/pull/569)

## Bug Fixes
 * Avoid that the agent blocks server shutdown in case the APM Server is not available - [#554](https://github.com/elastic/apm-agent-java/pull/554)
 * Public API annotations improper retention prevents it from being used with Groovy - [#567](https://github.com/elastic/apm-agent-java/pull/567)
 * Eliminate side effects of class loading related to Instrumentation matching mechanism

# 1.5.0

## Potentially breaking changes
 * If you didn't explicitly set the [`service_name`](https://www.elastic.co/guide/en/apm/agent/java/master/config-core.html#config-service-name)
   previously and you are dealing with a servlet-based application (including Spring Boot),
   your `service_name` will change.
   See the documentation for [`service_name`](https://www.elastic.co/guide/en/apm/agent/java/master/config-core.html#config-service-name)
   and the corresponding section in _Features_ for more information.
   Note: this requires APM Server 7.0+. If using previous versions, nothing will change.

## Features
 * Added property "allow_path_on_hierarchy" to JAX-RS plugin, to lookup inherited usage of `@path`
 * Support for number and boolean labels in the public API (#497).
   This change also renames `tag` to `label` on the API level to be compliant with the [Elastic Common Schema (ECS)](https://github.com/elastic/ecs#-base-fields).
   The `addTag(String, String)` method is still supported but deprecated in favor of `addLabel(String, String)`.
   As of version 7.x of the stack, labels will be stored under `labels` in Elasticsearch.
   Previously, they were stored under `context.tags`.
 * Support async queries made by Elasticsearch REST client 
 * Added `setStartTimestamp(long epochMicros)` and `end(long epochMicros)` API methods to `Span` and `Transaction`,
   allowing to set custom start and end timestamps.
 * Auto-detection of the `service_name` based on the `<display-name>` element of the `web.xml` with a fallback to the servlet context path.
   If you are using a spring-based application, the agent will use the setting for `spring.application.name` for its `service_name`.
   See the documentation for [`service_name`](https://www.elastic.co/guide/en/apm/agent/java/master/config-core.html#config-service-name)
   for more information.
   Note: this requires APM Server 7.0+. If using previous versions, nothing will change.
 * Previously, enabling [`capture_body`](https://www.elastic.co/guide/en/apm/agent/java/master/config-http.html#config-capture-body) could only capture form parameters.
   Now it supports all UTF-8 encoded plain-text content types.
   The option [`capture_body_content_types`](https://www.elastic.co/guide/en/apm/agent/java/master/config-http.html#config-capture-body-content-types)
   controls which `Content-Type`s should be captured.
 * Support async calls made by OkHttp client (`Call#enqueue`)
 * Added support for providing config options on agent attach.
   * CLI example: `--config server_urls=http://localhost:8200,http://localhost:8201`
   * API example: `ElasticApmAttacher.attach(Map.of("server_urls", "http://localhost:8200,http://localhost:8201"));`

## Bug Fixes
 * Logging integration through MDC is not working properly - [#499](https://github.com/elastic/apm-agent-java/issues/499)
 * ClassCastException with adoptopenjdk/openjdk11-openj9 - [#505](https://github.com/elastic/apm-agent-java/issues/505)
 * Span count limitation is not working properly - reported [in our forum](https://discuss.elastic.co/t/kibana-apm-not-showing-spans-which-are-visible-in-discover-too-many-spans/171690)
 * Java agent causes Exceptions in Alfresco cluster environment due to failure in the instrumentation of Hazelcast `Executor`s - reported [in our forum](https://discuss.elastic.co/t/cant-run-apm-java-agent-in-alfresco-cluster-environment/172962)

# 1.4.0

## Features
 * Added support for sync calls of OkHttp client
 * Added support for context propagation for `java.util.concurrent.ExecutorService`s
 * The `trace_methods` configuration now allows to omit the method matcher.
   Example: `com.example.*` traces all classes and methods within the `com.example` package and sub-packages.
 * Added support for JSF. Tested on WildFly, WebSphere Liberty and Payara with embedded JSF implementation and on Tomcat and Jetty with
 MyFaces 2.2 and 2.3
 * Introduces a new configuration option `disable_metrics` which disables the collection of metrics via a wildcard expression.
 * Support for HttpUrlConnection
 * Adds `subtype` and `action` to spans. This replaces former typing mechanism where type, subtype and action were all set through
   the type in an hierarchical dotted-syntax. In order to support existing API usages, dotted types are parsed into subtype and action, 
   however `Span.createSpan` and `Span.setType` are deprecated starting this version. Instead, type-less spans can be created using the new 
   `Span.startSpan` API and typed spans can be created using the new `Span.startSpan(String type, String subtype, String action)` API
 * Support for JBoss EAP 6.4, 7.0, 7.1 and 7.2
 * Improved startup times
 * Support for SOAP (JAX-WS).
   SOAP client create spans and propagate context.
   Transactions are created for `@WebService` classes and `@WebMethod` methods.  

## Bug Fixes
 * Fixes a failure in BitBucket when agent deployed ([#349](https://github.com/elastic/apm-agent-java/issues/349))
 * Fixes increased CPU consumption ([#443](https://github.com/elastic/apm-agent-java/issues/443) and [#453](https://github.com/elastic/apm-agent-java/issues/453))
 * Fixed some OpenTracing bridge functionalities that were not working when auto-instrumentation is disabled
 * Fixed an error occurring when ending an OpenTracing span before deactivating
 * Sending proper `null` for metrics that have a NaN value
 * Fixes JVM crash with Java 7 ([#458](https://github.com/elastic/apm-agent-java/issues/458))
 * Fixes an application deployment failure when using EclipseLink and `trace_methods` configuration ([#474](https://github.com/elastic/apm-agent-java/issues/474))

# 1.3.0

## Features
 * The agent now collects system and JVM metrics ([#360](https://github.com/elastic/apm-agent-java/pull/360))
 * Add API methods `ElasticApm#startTransactionWithRemoteParent` and `Span#injectTraceHeaders` to allow for manual context propagation ([#396](https://github.com/elastic/apm-agent-java/pull/396)).
 * Added `trace_methods` configuration option which lets you define which methods in your project or 3rd party libraries should be traced.
   To create spans for all `public` methods of classes whose name ends in `Service` which are in a sub-package of `org.example.services` use this matcher:
   `public org.example.services.*.*Service#*` ([#398](https://github.com/elastic/apm-agent-java/pull/398))
 * Added span for `DispatcherServlet#render` ([#409](https://github.com/elastic/apm-agent-java/pull/409)).
 * Flush reporter on shutdown to make sure all recorded Spans are sent to the server before the programm exits ([#397](https://github.com/elastic/apm-agent-java/pull/397))
 * Adds Kubernetes ([#383](https://github.com/elastic/apm-agent-java/issues/383)) and Docker metadata to, enabling correlation with the Kibana Infra UI.
 * Improved error handling of the Servlet Async API ([#399](https://github.com/elastic/apm-agent-java/issues/399))

## Bug Fixes
 * Fixing a potential memory leak when there is no connection with APM server
 * Fixes NoSuchMethodError CharBuffer.flip() which occurs when using the Elasticsearch RestClient and Java 7 or 8 ([#401](https://github.com/elastic/apm-agent-java/pull/401))

 
# 1.2.0

## Features
 * Added `capture_headers` configuration option.
   Set to `false` to disable capturing request and response headers.
   This will reduce the allocation rate of the agent and can save you network bandwidth and disk space.
 * Makes the API methods `addTag`, `setName`, `setType`, `setUser` and `setResult` fluent, so that calls can be chained. 

## Bug Fixes
 * Catch all errors thrown within agent injected code
 * Enable public APIs and OpenTracing bridge to work properly in OSGi systems, fixes [this WildFly issue](https://github.com/elastic/apm-agent-java/issues/362)
 * Remove module-info.java to enable agent working on early Tomcat 8.5 versions
 * Fix [async Servlet API issue](https://github.com/elastic/apm-agent-java/issues/371)

# 1.1.0

## Features
 * Some memory allocation improvements
 * Enabling bootdelegation for agent classes in Atlassian OSGI systems

## Bug Fixes
 * Update dsl-json which fixes a memory leak.
 See [ngs-doo/dsl-json#102](https://github.com/ngs-doo/dsl-json/pull/102) for details. 
 * Avoid `VerifyError`s by non instrumenting classes compiled for Java 4 or earlier
 * Enable APM Server URL configuration with path (fixes #339)
 * Reverse `system.hostname` and `system.platform` order sent to APM server

# 1.0.1

## Bug Fixes
 * Fixes NoSuchMethodError CharBuffer.flip() which occurs when using the Elasticsearch RestClient and Java 7 or 8 (#313)

# 1.0.0

## Breaking changes
 * Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
   Until the time the APM Server 6.5.0 is officially released,
   you can test with docker by pulling the APM Server image via
   `docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT`. 

## Features
 * Adds `@CaptureTransaction` and `@CaptureSpan` annotations which let you declaratively add custom transactions and spans.
   Note that it is required to configure the `application_packages` for this to work.
   See the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-annotation) for more information.
 * The public API now supports to activate a span on the current thread.
   This makes the span available via `ElasticApm#currentSpan()`
   Refer to the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-span-activate) for more details.
 * Capturing of Elasticsearch RestClient 5.0.2+ calls.
   Currently, the `*Async` methods are not supported, only their synchronous counterparts.
 * Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction.
   More information can be found in the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-ensure-parent-id).
 * Added `Transaction.isSampled()` and `Span.isSampled()` methods to the public API
 * Added `Transaction#setResult` to the public API (#293)

## Bug Fixes
 * Fix for situations where status code is reported as `200`, even though it actually was `500` (#225)
 * Capturing the username now properly works when using Spring security (#183)

# 1.0.0.RC1

## Breaking changes
 * Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
   Until the time the APM Server 6.5.0 is officially released,
   you can test with docker by pulling the APM Server image via
   `docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT`. 

## Features
 * Adds `@CaptureTransaction` and `@CaptureSpan` annotations which let you declaratively add custom transactions and spans.
   Note that it is required to configure the `application_packages` for this to work.
   See the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-annotation) for more information.
 * The public API now supports to activate a span on the current thread.
   This makes the span available via `ElasticApm#currentSpan()`
   Refer to the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-span-activate) for more details.
 * Capturing of Elasticsearch RestClient 5.0.2+ calls.
   Currently, the `*Async` methods are not supported, only their synchronous counterparts.
 * Added API methods to enable correlating the spans created from the JavaScrip Real User Monitoring agent with the Java agent transaction.
   More information can be found in the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/public-api.html#api-ensure-parent-id).
 * Microsecond accurate timestamps (#261)

## Bug Fixes
 * Fix for situations where status code is reported as `200`, even though it actually was `500` (#225)

# 0.8.0

## Breaking changes
 * Wildcard patterns are case insensitive by default. Prepend `(?-i)` to make the matching case sensitive.

## Features
 * Wildcard patterns are now not limited to only one wildcard in the middle and can be arbitrarily complex now.
   Example: `*foo*bar*baz`.
 * Support for JAX-RS annotations.
   Transactions are named based on your resources (`ResourceClass#resourceMethod`).

## Bug Fixes

# 0.7.0

## Breaking changes
 * Removed `ElasticApm.startSpan`. Spans can now only be created from their transactions via `Transaction#createSpan`.
 * `ElasticApm.startTransaction` and `Transaction#createSpan` don't activate the transaction and spans
   and are thus not available via `ElasticApm.activeTransaction` and `ElasticApm.activeSpan`.

## Features
 * Public API
    * Add `Span#captureException` and `Transaction#captureException` to public API.
      `ElasticApm.captureException` is deprecated now. Use `ElasticApm.currentSpan().captureException(exception)` instead.
    * Added `Transaction.getId` and `Span.getId` methods 
 * Added support for async servlet requests
 * Added support for Payara/Glassfish
 * Incubating support for Apache HttpClient
 * Support for Spring RestTemplate
 * Added configuration options `use_path_as_transaction_name` and `url_groups`,
   which allow to use the URL path as the transaction name.
   As that could contain path parameters, like `/user/$userId` however,
   You can set the `url_groups` option to define a wildcard pattern, like `/user/*`,
   to group those paths together.
   This is especially helpful when using an unsupported Servlet API-based framework. 
 * Support duration suffixes (`ms`, `s` and `m`) for duration configuration options.
   Not using the duration suffix logs out a deprecation warning and will not be supported in future versions.
 * Add ability to add multiple APM server URLs, which enables client-side load balancing.
   The configuration option `server_url` has been renamed to `server_urls` to reflect this change.
   However, `server_url` still works for backwards compatibility.
 * The configuration option `service_name` is now optional.
   It defaults to the main class name,
   the name of the executed jar file (removing the version number),
   or the application server name (for example `tomcat-application`).
   In a lot of cases,
   you will still want to set the `service_name` explicitly.
   But it helps getting started and seeing data easier,
   as there are no required configuration options anymore.
   In the future we will most likely determine more useful application names for Servlet API-based applications.

## Bug Fixes
