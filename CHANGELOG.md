# 1.11.0 (Next)

## Features
 * Add the ability to configure a unique name for a JVM within a service through the [`service_node_name` config option](
 * Add ability to ignore some exceptions to be reported as errors [ignore_exceptions](https://www.elastic.co/guide/en/apm/agent/java/master/config-core.html#config-ignore_exceptions

## Bug Fixes

# 1.10.0

## Features
 * Add ability to manually specify reported [hostname](https://www.elastic.co/guide/en/apm/agent/java/master/config-core.html#config-hostname)
 * Add support for [Redis Jedis client](https://www.elastic.co/guide/en/apm/agent/java/master/supported-technologies-details.html#supported-databases)
 * Add support for identifying target JVM to attach apm agent to using JMV property. See also the documentation of the [`--include` and `--exclude` flags](https://www.elastic.co/guide/en/apm/agent/java/master/setup-attach-cli.html#setup-attach-cli-usage-list)
 * Added [`capture_jmx_metrics`](https://www.elastic.co/guide/en/apm/agent/java/master/config-jmx.html#config-capture-jmx-metrics) configuration option
 * Improve servlet error capture (#812)
  Among others, now also takes Spring MVC `@ExceptionHandler`s into account 
 * Instrument Logger#error(String, Throwable) (#821)
  Automatically captures exceptions when calling `logger.error("message", exception)`
 * Easier log correlation with https://github.com/elastic/java-ecs-logging. See [docs](https://www.elastic.co/guide/en/apm/agent/java/master/log-correlation.html).
 * Avoid creating a temp agent file for each attachment (#859)
 * Instrument `View#render` instead of `DispatcherServlet#render` (#829)
  This makes the transaction breakdown graph more useful. Instead of `dispatcher-servlet`, the graph now shows a type which is based on the view name, for example, `FreeMarker` or `Thymeleaf`.

## Bug Fixes
 * Error in log when setting [server_urls](https://www.elastic.co/guide/en/apm/agent/java/current/config-reporter.html#config-server-urls) 
 to an empty string - `co.elastic.apm.agent.configuration.ApmServerConfigurationSource - Expected previousException not to be null`
 * Avoid terminating the TCP connection to APM Server when polling for configuration updates (#823)
 * Fixes potential segfault if attaching the agent with long arguments (#865)
 
# 1.9.0

## Features
 * Supporting OpenTracing version 0.33 
 * Added annotation and meta-annotation matching support for `trace_methods`

## Bug Fixes
 * A warning in logs saying APM server is not available when using 1.8 with APM server 6.x
 * `ApacheHttpAsyncClientInstrumentation` matching increases startup time considerably
 * Log correlation feature is active when `active==false`
 * The runtime attachment now also works when the `tools.jar` or the `jdk.attach` module is not available.
   This means you don't need a full JDK installation - the JRE is sufficient.
   This makes the runtime attachment work in more environments such as minimal Docker containers.
   Note that the runtime attachment currently does not work for OSGi containers like those used in many application servers such as JBoss and WildFly.
   See the [documentation](https://www.elastic.co/guide/en/apm/agent/java/master/setup-attach-cli.html) for more information.
 * JDBC statement map is leaking in Tomcat if the application that first used it is udeployed/redeployed. See [this 
   related discussion](https://discuss.elastic.co/t/elastic-apm-agent-jdbchelper-seems-to-use-a-lot-of-memory/195295).

# Breaking Changes
 * The `apm-agent-attach.jar` is not executable anymore.
   Use `apm-agent-attach-standalone.jar` instead. 

# 1.8.0

## Features
 * Added support for tracking [time spent by span type](https://www.elastic.co/guide/en/kibana/7.3/transactions.html).
   Can be disabled by setting [`breakdown_metrics`](https://www.elastic.co/guide/en/apm/agent/java/7.3/config-core.html#config-breakdown-metrics) to `false`. 
 * Added support for [central configuration](https://www.elastic.co/guide/en/kibana/7.3/agent-configuration.html).
   Can be disabled by setting [`central_config`](https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-central-config) to `false`.
 * Added support for Spring's JMS flavor - instrumenting `org.springframework.jms.listener.SessionAwareMessageListener`
 * Added support to legacy ApacheHttpClient APIs (which adds support to Axis2 configured to use ApacheHttpClient)
 * Added support for setting [`server_urls`](https://www.elastic.co/guide/en/apm/agent/java/1.x/config-reporter.html#config-server-urls) dynamically via properties file [#723](https://github.com/elastic/apm-agent-java/issues/723)
 * Added [`config_file`](https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-config-file) option 
 * Added option to use `@javax.ws.rs.Path` value as transaction name [`use_jaxrs_path_as_transaction_name`](https://www.elastic.co/guide/en/apm/agent/java/current/config-jax-rs.html#config-use-jaxrs-path-as-transaction-name)
 * Instrument quartz jobs ([docs](https://www.elastic.co/guide/en/apm/agent/java/current/supported-technologies-details.html#supported-scheduling-frameworks))
 * SQL parsing improvements (#696)
 * Introduce priorities for transaction name (#748)
 
   Now uses the path as transaction name if [`use_path_as_transaction_name`](https://www.elastic.co/guide/en/apm/agent/java/current/config-http.html#config-use-path-as-transaction-name) is set to `true`
   rather than `ServletClass#doGet`.
   But if a name can be determined from a high level framework,
   like Spring MVC, that takes precedence.
   User-supplied names from the API always take precedence over any others.
 * Use JSP path name as transaction name as opposed to the generated servlet class name (#751)


## Bug Fixes
 * Some JMS Consumers and Producers are filtered due to class name filtering in instrumentation matching
 * Jetty: When no display name is set and context path is "/" transaction service names will now correctly fall back to configured values
 * JDBC's `executeBatch` is not traced
 * Drops non-String labels when connected to APM Server < 6.7 to avoid validation errors (#687)
 * Parsing container ID in cloud foundry garden (#695)
 * Automatic instrumentation should not override manual results (#752)

## Breaking changes
 * The log correlation feature does not add `span.id` to the MDC anymore but only `trace.id` and `transaction.id` (see #742).

# 1.7.0

## Features
 * Added the `trace_methods_duration_threshold` config option. When using the `trace_methods` config option with wild cards, this 
 enables considerable reduction of overhead by limiting the number of spans captured and reported (see more details in config 
 documentation).
 NOTE: Using wildcards is still not the recommended approach for the `trace_methods` feature
 * Add `Transaction#addCustomContext(String key, String|Number|boolean value)` to public API
 * Added support for AsyncHttpClient 2.x
 * Added [`global_labels`](https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#global-labels) configuration option.
   This requires APM Server 7.2+.
 * Added basic support for JMS- distributed tracing for basic scenarios of `send`, `receive`, `receiveNoWait` and 
   `onMessage`. Both Queues and Topics are supported. Async `send` APIs are not supported in this version. 
   NOTE: This feature is currently marked as "Incubating" and is disabled by default. In order to enable, it is 
   required to set the [`disable_instrumentations`](https://www.elastic.co/guide/en/apm/agent/java/1.x/config-core.html#config-disable-instrumentations) 
   configuration property to an empty string.

## Bug Fixes
 * ClassCastException related to async instrumentation of Pilotfish Executor causing thread hang (applied workaround)
 * NullPointerException when computing Servlet transaction name with null HTTP method name
 * FileNotFoundException when trying to find implementation version of jar with encoded URL
 * NullPointerException when closing Apache AsyncHttpClient request producer

# 1.6.1

## Bug Fixes
 * Fixes transaction name for non-sampled transactions [#581](https://github.com/elastic/apm-agent-java/issues/581)
 * Makes log_file option work again [#594](https://github.com/elastic/apm-agent-java/issues/594)
 * Async context propagation fixes
    * Fixing some async mechanisms lifecycle issues [#605](https://github.com/elastic/apm-agent-java/issues/605)
    * Fixes exceptions when using WildFly managed executor services [#589](https://github.com/elastic/apm-agent-java/issues/589)
    * Exclude glassfish Executor which does not permit wrapped runnables [#596](https://github.com/elastic/apm-agent-java/issues/596)
    * Exclude DumbExecutor [#598](https://github.com/elastic/apm-agent-java/issues/598)
 * Fixes Manifest version reading error to support `jar:file` protocol [#601](https://github.com/elastic/apm-agent-java/issues/601)
 * Fixes transaction name for non-sampled transactions [#597](https://github.com/elastic/apm-agent-java/issues/597)
 * Fixes potential classloader deadlock by preloading `FileSystems.getDefault()` [#603](https://github.com/elastic/apm-agent-java/issues/603)

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
