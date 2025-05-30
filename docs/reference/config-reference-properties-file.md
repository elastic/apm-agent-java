---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-reference-properties-file.html
---

# Property file reference [config-reference-properties-file]

```properties
############################################
# Circuit-Breaker                          #
############################################

# A boolean specifying whether the circuit breaker should be enabled or not.
# When enabled, the agent periodically polls stress monitors to detect system/process/JVM stress state.
# If ANY of the monitors detects a stress indication, the agent will become inactive, as if the
# <<config-recording,`recording`>> configuration option has been set to `false`, thus reducing resource consumption to a minimum.
# When inactive, the agent continues polling the same monitors in order to detect whether the stress state
# has been relieved. If ALL monitors approve that the system/process/JVM is not under stress anymore, the
# agent will resume and become fully functional.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: false
#
# circuit_breaker_enabled=false

# The interval at which the agent polls the stress monitors. Must be at least `1s`.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 5s.
# Default value: 5s
#
# stress_monitoring_interval=5s

# The threshold used by the GC monitor to rely on for identifying heap stress.
# The same threshold will be used for all heap pools, so that if ANY has a usage percentage that crosses it,
# the agent will consider it as a heap stress. The GC monitor relies only on memory consumption measured
# after a recent GC.
#
# This setting can be changed at runtime
# Type: Double
# Default value: 0.95
#
# stress_monitor_gc_stress_threshold=0.95

# The threshold used by the GC monitor to rely on for identifying when the heap is not under stress .
# If `stress_monitor_gc_stress_threshold` has been crossed, the agent will consider it a heap-stress state.
# In order to determine that the stress state is over, percentage of occupied memory in ALL heap pools should
# be lower than this threshold. The GC monitor relies only on memory consumption measured after a recent GC.
#
# This setting can be changed at runtime
# Type: Double
# Default value: 0.75
#
# stress_monitor_gc_relief_threshold=0.75

# The minimal time required in order to determine whether the system is
# either currently under stress, or that the stress detected previously has been relieved.
# All measurements during this time must be consistent in comparison to the relevant threshold in
# order to detect a change of stress state. Must be at least `1m`.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 1m.
# Default value: 1m
#
# stress_monitor_cpu_duration_threshold=1m

# The threshold used by the system CPU monitor to detect system CPU stress.
# If the system CPU crosses this threshold for a duration of at least `stress_monitor_cpu_duration_threshold`,
# the monitor considers this as a stress state.
#
# This setting can be changed at runtime
# Type: Double
# Default value: 0.95
#
# stress_monitor_system_cpu_stress_threshold=0.95

# The threshold used by the system CPU monitor to determine that the system is
# not under CPU stress. If the monitor detected a CPU stress, the measured system CPU needs to be below
# this threshold for a duration of at least `stress_monitor_cpu_duration_threshold` in order for the
# monitor to decide that the CPU stress has been relieved.
#
# This setting can be changed at runtime
# Type: Double
# Default value: 0.8
#
# stress_monitor_system_cpu_relief_threshold=0.8

############################################
# Core                                     #
############################################

# NOTE: This option was available in older versions through the `active` key. The old key is still
# supported in newer versions, but it is now deprecated.
#
# A boolean specifying if the agent should be recording or not.
# When recording, the agent instruments incoming HTTP requests, tracks errors and collects and sends metrics.
# When not recording, the agent works as a noop, not collecting data and not communicating with the APM sever,
# except for polling the central configuration endpoint.
# Note that trace context propagation, baggage and log correlation will also be disabled when recording is disabled.
# As this is a reversible switch, agent threads are not being killed when inactivated, but they will be
# mostly idle in this state, so the overhead should be negligible.
#
# You can use this setting to dynamically disable Elastic APM at runtime.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# recording=true

# Setting to false will completely disable the agent, including instrumentation and remote config polling.
# If you want to dynamically change the status of the agent, use <<config-recording,`recording`>> instead.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: true
#
# enabled=true

# A boolean specifying if the agent should instrument the application to collect traces for the app.
#  When set to `false`, most built-in instrumentation plugins are disabled, which would minimize the effect on
# your application. However, the agent would still apply instrumentation related to manual tracing options and it
# would still collect and send metrics to APM Server.
#
# NOTE: Both active and instrument needs to be true for instrumentation to be running.
#
# NOTE: Changing this value at runtime can slow down the application temporarily.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# instrument=true

# The name of your service
#
# This is used to keep all the errors and transactions of your service together
# and is the primary filter in the Elastic APM user interface.
#
# Instead of configuring the service name manually,
# you can also choose to rely on the service name auto-detection mechanisms of the agent.
# If `service_name` is set explicitly, all auto-detection mechanisms are disabled.
#
# This is how the service name auto-detection works:
#
# * For standalone applications
# ** The agent uses `Implementation-Title` in the `META-INF/MANIFEST.MF` file if the application is started via `java -jar`.
# ** Falls back to the name of the main class or jar file.
# * For applications that are deployed to a servlet container/application server, the agent auto-detects the name for each application.
# ** For Spring-based applications, the agent uses the `spring.application.name` property, if set.
# ** For servlet-based applications, falls back to the `Implementation-Title` in the `META-INF/MANIFEST.MF` file.
# ** Falls back to the `display-name` of the `web.xml`, if available.
# ** Falls back to the servlet context path the application is mapped to (unless mapped to the root context).
#
# Generally, it is recommended to rely on the service name detection based on `META-INF/MANIFEST.MF`.
# Spring Boot automatically adds the relevant manifest entries.
# For other applications that are built with Maven, this is how you add the manifest entries:
#
#
# [source,xml]
# .pom.xml
# ----
#     <build>
#         <plugins>
#             <plugin>
#                 <!-- replace with 'maven-war-plugin' if you're building a war -->
#                 <artifactId>maven-jar-plugin</artifactId>
#                 <configuration>
#                     <archive>
#                         <!-- Adds
#                         Implementation-Title based on ${project.name} and
#                         Implementation-Version based on ${project.version}
#                         -->
#                         <manifest>
#                             <addDefaultImplementationEntries>true</addDefaultImplementationEntries>
#                         </manifest>
#                         <!-- To customize the Implementation-* entries, remove addDefaultImplementationEntries and add them manually
#                         <manifestEntries>
#                             <Implementation-Title>foo</Implementation-Title>
#                             <Implementation-Version>4.2.0</Implementation-Version>
#                         </manifestEntries>
#                         -->
#                     </archive>
#                 </configuration>
#             </plugin>
#         </plugins>
#     </build>
# ----
#
#
# The service name must conform to this regular expression: `^[a-zA-Z0-9 _-]+$`.
# In less regexy terms:
# Your service name must only contain characters from the ASCII alphabet, numbers, dashes, underscores and spaces.
#
# NOTE: Service name auto discovery mechanisms require APM Server 7.0+.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value: Auto-detected based on the rules described above
#
# service_name=

# A unique name for the service node
#
# If set, this name is used to distinguish between different nodes of a service,
# therefore it should be unique for each JVM within a service.
# If not set, data aggregations will be done based on a container ID (where valid) or on the reported
# hostname (automatically discovered or manually configured through <<config-hostname, `hostname`>>).
#
# NOTE: JVM metrics views rely on aggregations that are based on the service node name.
# If you have multiple JVMs installed on the same host reporting data for the same service name,
# you must set a unique node name for each in order to view metrics at the JVM level.
#
# NOTE: Metrics views can utilize this configuration since APM Server 7.5
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# service_node_name=

# A version string for the currently deployed version of the service. If you don’t version your deployments, the recommended value for this field is the commit identifier of the deployed revision, e.g. the output of git rev-parse HEAD.
#
# Similar to the auto-detection of <<config-service-name>>, the agent can auto-detect the service version based on the `Implementation-Title` attribute in `META-INF/MANIFEST.MF`.
# See <<config-service-name>> on how to set this attribute.
#
#
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# service_version=

# Allows for the reported hostname to be manually specified. If unset the hostname will be looked up.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# hostname=

# The name of the environment this service is deployed in, e.g. "production" or "staging".
#
# Environments allow you to easily filter data on a global level in the APM app.
# It's important to be consistent when naming environments across agents.
# See {apm-app-ref}/filters.html#environment-selector[environment selector] in the APM app for more information.
#
# NOTE: This feature is fully supported in the APM app in Kibana versions >= 7.2.
# You must use the query bar to filter for a specific environment in versions prior to 7.2.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# environment=

# By default, the agent will sample every transaction (e.g. request to your service). To reduce overhead and storage requirements, you can set the sample rate to a value between 0.0 and 1.0. (For pre-8.0 servers the agent still records and sends overall time and the result for unsampled transactions, but no context information, labels, or spans. When connecting to 8.0+ servers, the unsampled requests are not sent at all).
#
# Value will be rounded with 4 significant digits, as an example, value '0.55555' will be rounded to `0.5556`
#
# This setting can be changed at runtime
# Type: Double
# Default value: 1
#
# transaction_sample_rate=1

# Limits the amount of spans that are recorded per transaction.
#
# This is helpful in cases where a transaction creates a very high amount of spans (e.g. thousands of SQL queries).
#
# Setting an upper limit will prevent overloading the agent and the APM server with too much work for such edge cases.
#
# A message will be logged when the max number of spans has been exceeded but only at a rate of once every 5 minutes to ensure performance is not impacted.
#
# This setting can be changed at runtime
# Type: Integer
# Default value: 500
#
# transaction_max_spans=500

#
# The following transaction, span, and error fields will be truncated at this number of unicode characters before being sent to APM server:
#
# - `transaction.context.request.body`, `error.context.request.body`
# - `transaction.context.message.body`, `error.context.message.body`
# - `span.context.db.statement`
#
# Note that tracing data is limited at the upstream APM server to
# {apm-guide-ref}/configuration-process.html#max_event_size[`max_event_size`],
# which defaults to 300kB. If you configure `long_field_max_length` too large, it
# could result in transactions, spans, or errors that are rejected by APM server.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Integer
# Default value: 10000
#
# long_field_max_length=10000

# Sometimes it is necessary to sanitize the data sent to Elastic APM,
# e.g. remove sensitive data.
#
# Configure a list of wildcard patterns of field names which should be sanitized.
# These apply for example to HTTP headers and `application/x-www-form-urlencoded` data.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# NOTE: Data in the query string is considered non-sensitive,
# as sensitive information should not be sent in the query string.
# See https://www.owasp.org/index.php/Information_exposure_through_query_strings_in_url for more information
#
# NOTE: Review the data captured by Elastic APM carefully to make sure it does not capture sensitive information.
# If you do find sensitive data in the Elasticsearch index,
# you should add an additional entry to this list (make sure to also include the default entries).
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: password,passwd,pwd,secret,*key,*token*,*session*,*credit*,*card*,*auth*,*principal*,set-cookie
#
# sanitize_field_names=password,passwd,pwd,secret,*key,*token*,*session*,*credit*,*card*,*auth*,*principal*,set-cookie

# A list of instrumentations which should be selectively enabled.
# Valid options are `annotations`, `annotations-capture-span`, `annotations-capture-transaction`, `annotations-traced`, `apache-commons-exec`, `apache-httpclient`, `asynchttpclient`, `aws-lambda`, `aws-sdk`, `cassandra`, `concurrent`, `dubbo`, `elasticsearch-restclient`, `exception-handler`, `executor`, `executor-collection`, `experimental`, `finagle-httpclient`, `fork-join`, `grails`, `grpc`, `hibernate-search`, `http-client`, `jakarta-websocket`, `java-ldap`, `javalin`, `javax-websocket`, `jax-rs`, `jax-ws`, `jdbc`, `jdk-httpclient`, `jdk-httpserver`, `jedis`, `jms`, `jsf`, `kafka`, `lettuce`, `log-correlation`, `log-error`, `log-reformatting`, `logging`, `micrometer`, `mongodb`, `mongodb-client`, `okhttp`, `opentelemetry`, `opentelemetry-annotations`, `opentelemetry-metrics`, `opentracing`, `process`, `public-api`, `quartz`, `rabbitmq`, `reactor`, `redis`, `redisson`, `render`, `scala-future`, `scheduled`, `servlet-api`, `servlet-api-async`, `servlet-api-dispatch`, `servlet-input-stream`, `servlet-service-name`, `servlet-version`, `sparkjava`, `spring-amqp`, `spring-mvc`, `spring-resttemplate`, `spring-service-name`, `spring-view-render`, `spring-webclient`, `spring-webflux`, `ssl-context`, `struts`, `timer-task`, `urlconnection`, `vertx`, `vertx-web`, `vertx-webclient`, `websocket`.
# When set to non-empty value, only listed instrumentations will be enabled if they are not disabled through <<config-disable-instrumentations>> or <<config-enable-experimental-instrumentations>>.
# When not set or empty (default), all instrumentations enabled by default will be enabled unless they are disabled through <<config-disable-instrumentations>> or <<config-enable-experimental-instrumentations>>.
#
# NOTE: Changing this value at runtime can slow down the application temporarily.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# enable_instrumentations=

# A list of instrumentations which should be disabled.
# Valid options are `annotations`, `annotations-capture-span`, `annotations-capture-transaction`, `annotations-traced`, `apache-commons-exec`, `apache-httpclient`, `asynchttpclient`, `aws-lambda`, `aws-sdk`, `cassandra`, `concurrent`, `dubbo`, `elasticsearch-restclient`, `exception-handler`, `executor`, `executor-collection`, `experimental`, `finagle-httpclient`, `fork-join`, `grails`, `grpc`, `hibernate-search`, `http-client`, `jakarta-websocket`, `java-ldap`, `javalin`, `javax-websocket`, `jax-rs`, `jax-ws`, `jdbc`, `jdk-httpclient`, `jdk-httpserver`, `jedis`, `jms`, `jsf`, `kafka`, `lettuce`, `log-correlation`, `log-error`, `log-reformatting`, `logging`, `micrometer`, `mongodb`, `mongodb-client`, `okhttp`, `opentelemetry`, `opentelemetry-annotations`, `opentelemetry-metrics`, `opentracing`, `process`, `public-api`, `quartz`, `rabbitmq`, `reactor`, `redis`, `redisson`, `render`, `scala-future`, `scheduled`, `servlet-api`, `servlet-api-async`, `servlet-api-dispatch`, `servlet-input-stream`, `servlet-service-name`, `servlet-version`, `sparkjava`, `spring-amqp`, `spring-mvc`, `spring-resttemplate`, `spring-service-name`, `spring-view-render`, `spring-webclient`, `spring-webflux`, `ssl-context`, `struts`, `timer-task`, `urlconnection`, `vertx`, `vertx-web`, `vertx-webclient`, `websocket`.
# For version `1.25.0` and later, use <<config-enable-experimental-instrumentations>> to enable experimental instrumentations.
#
# NOTE: Changing this value at runtime can slow down the application temporarily.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# disable_instrumentations=

# Whether to apply experimental instrumentations.
#
# NOTE: Changing this value at runtime can slow down the application temporarily.
# Setting to `true` will enable instrumentations in the `experimental` group.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: false
#
# enable_experimental_instrumentations=false

# When reporting exceptions,
# un-nests the exceptions matching the wildcard pattern.
# This can come in handy for Spring's `org.springframework.web.util.NestedServletException`,
# for example.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: (?-i)*Nested*Exception
#
# unnest_exceptions=(?-i)*Nested*Exception

# A list of exceptions that should be ignored and not reported as errors.
# This allows to ignore exceptions thrown in regular control flow that are not actual errors
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# Examples:
#
#  - `com.mycompany.ExceptionToIgnore`: using fully qualified name
#  - `*ExceptionToIgnore`: using wildcard to avoid package name
#  - `*exceptiontoignore`: case-insensitive by default
#
# NOTE: Exception inheritance is not supported, thus you have to explicitly list all the thrown exception types
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# ignore_exceptions=

# For transactions that are HTTP requests, the Java agent can optionally capture the request body (e.g. POST
# variables). For transactions that are initiated by receiving a message from a message broker, the agent can
# capture the textual message body.
#
# If the HTTP request or the message has a body and this setting is disabled, the body will be shown as [REDACTED].
#
# This option is case-insensitive.
#
# NOTE: Currently, the body length is limited to 10000 characters and it is not configurable.
# If the body size exceeds the limit, it will be truncated.
#
# NOTE: Currently, only UTF-8 encoded plain text HTTP content types are supported.
# The option <<config-capture-body-content-types>> determines which content types are captured.
#
# WARNING: Request bodies often contain sensitive values like passwords, credit card numbers etc.
# If your service handles data like this, we advise to only enable this feature with care.
# Turning on body capturing can also significantly increase the overhead in terms of heap usage,
# network utilisation and Elasticsearch index size.
#
# Valid options: off, errors, transactions, all
# This setting can be changed at runtime
# Type: EventType
# Default value: OFF
#
# capture_body=OFF

# If set to `true`, the agent will capture HTTP request and response headers (including cookies),
# as well as messages' headers/properties when using messaging frameworks like Kafka or JMS.
#
# NOTE: Setting this to `false` reduces network bandwidth, disk space and object allocations.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# capture_headers=true

# Labels added to all events, with the format `key=value[,key=value[,...]]`.
# Any labels set by application via the API will override global labels with the same keys.
#
# NOTE: This feature requires APM Server 7.2+
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Map
# Default value:
#
# global_labels=

# A boolean specifying if the agent should instrument pre-Java-1.4 bytecode.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# instrument_ancient_bytecode=false

# When set to true, disables log sending, metrics and trace collection.
# Trace context propagation and log correlation will stay active.
# Note that in contrast to <<config-disable-send, `disable_send`>> the agent will still connect to the APM-server for fetching configuration updates and health checks.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: false
#
# context_propagation_only=false

# Use to exclude specific classes from being instrumented. In order to exclude entire packages,
# use wildcards, as in: `com.project.exclude.*`
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: comma separated list
# Default value:
#
# classes_excluded_from_instrumentation=

# A list of methods for which to create a transaction or span.
#
# If you want to monitor a large number of methods,
# use  <<config-profiling-inferred-spans-enabled, `profiling_inferred_spans_enabled`>> instead.
#
# This works by instrumenting each matching method to include code that creates a span for the method.
# While creating a span is quite cheap in terms of performance,
# instrumenting a whole code base or a method which is executed in a tight loop leads to significant overhead.
#
# Using a pointcut-like syntax, you can match based on
#
#  - Method modifier (optional) +
#    Example: `public`, `protected`, `private` or `*`
#  - Package and class name (wildcards include sub-packages) +
#    Example: `org.example.*`
#  - Method name (optional since 1.4.0) +
#    Example: `myMeth*d`
#  - Method argument types (optional) +
#    Example: `(*lang.String, int[])`
#  - Classes with a specific annotation (optional) +
#    Example: `@*ApplicationScoped`
#  - Classes with a specific annotation that is itself annotated with the given meta-annotation (optional) +
#    Example: `@@javax.enterpr*se.context.NormalScope`
#
# The syntax is `modifier @fully.qualified.AnnotationName fully.qualified.ClassName#methodName(fully.qualified.ParameterType)`.
#
# A few examples:
#
#  - `org.example.*` added:[1.4.0,Omitting the method is possible since 1.4.0]
#  - `org.example.*#*` (before 1.4.0, you need to specify a method matcher)
#  - `org.example.MyClass#myMethod`
#  - `org.example.MyClass#myMethod()`
#  - `org.example.MyClass#myMethod(java.lang.String)`
#  - `org.example.MyClass#myMe*od(java.lang.String, int)`
#  - `private org.example.MyClass#myMe*od(java.lang.String, *)`
#  - `* org.example.MyClas*#myMe*od(*.String, int[])`
#  - `public org.example.services.*Service#*`
#  - `public @java.inject.ApplicationScoped org.example.*`
#  - `public @java.inject.* org.example.*`
#  - `public @@javax.enterprise.context.NormalScope org.example.*, public @@jakarta.enterprise.context.NormalScope org.example.*`
#
# NOTE: Only use wildcards if necessary.
# The more methods you match the more overhead will be caused by the agent.
# Also note that there is a maximum amount of spans per transaction (see <<config-transaction-max-spans, `transaction_max_spans`>>).
#
# NOTE: The agent will create stack traces for spans which took longer than
# <<config-span-stack-trace-min-duration, `span_stack_trace_min_duration`>>.
# When tracing a large number of methods (for example by using wildcards),
# this may lead to high overhead.
# Consider increasing the threshold or disabling stack trace collection altogether.
#
# Common configurations:
#
# Trace all public methods in CDI-Annotated beans:
#
# ----
# public @@javax.enterprise.context.NormalScope your.application.package.*
# public @@jakarta.enterprise.context.NormalScope your.application.package.*
# public @@javax.inject.Scope your.application.package.*
# ----
# NOTE: This method is only available in the Elastic APM Java Agent.
#
# NOTE: Changing this value at runtime can slow down the application temporarily.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# trace_methods=

# If <<config-trace-methods, `trace_methods`>> config option is set, provides a threshold to limit spans based on
# duration. When set to a value greater than 0, spans representing methods traced based on `trace_methods` will be discarded by default.
# Such methods will be traced and reported if one of the following applies:
#
#  - This method's duration crossed the configured threshold.
#  - This method ended with Exception.
#  - A method executed as part of the execution of this method crossed the threshold or ended with Exception.
#  - A "forcibly-traced method" (e.g. DB queries, HTTP exits, custom) was executed during the execution of this method.
#
# Set to 0 to disable.
#
# NOTE: Transactions are never discarded, regardless of their duration.
# This configuration affects only spans.
# In order not to break span references,
# all spans leading to an async operation or an exit span (such as a HTTP request or a DB query) are never discarded,
# regardless of their duration.
#
# NOTE: If this option and <<config-span-min-duration,`span_min_duration`>> are both configured,
# the higher of both thresholds will determine which spans will be discarded.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 0ms.
# Default value: 0ms
#
# trace_methods_duration_threshold=0ms

# When enabled, the agent will make periodic requests to the APM Server to fetch updated configuration.
# The frequency of the periodic request is driven by the `Cache-Control` header returned from APM Server/Integration, falling back to 5 minutes if not defined.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# central_config=true

# Disables the collection of breakdown metrics (`span.self_time`)
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: true
#
# breakdown_metrics=true

# Sets the path of the agent config file.
# The special value `_AGENT_HOME_` is a placeholder for the folder the `elastic-apm-agent.jar` is in.
# The file has to be on the file system.
# You can not refer to classpath locations.
#
# NOTE: this option can only be set via system properties, environment variables or the attacher options.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value: _AGENT_HOME_/elasticapm.properties
#
# config_file=_AGENT_HOME_/elasticapm.properties

# A folder that contains external agent plugins.
#
# Use the `apm-agent-plugin-sdk` and the `apm-agent-api` artifacts to create a jar and place it into the plugins folder.
# The agent will load all instrumentations that are declared in the
# `META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation` service descriptor.
# See `integration-tests/external-plugin-test` for an example plugin.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# plugins_dir=

# To enable {apm-guide-ref}/apm-distributed-tracing.html[distributed tracing], the agent
# adds trace context headers to outgoing requests (like HTTP requests, Kafka records, gRPC requests etc.).
# These headers (`traceparent` and `tracestate`) are defined in the
# https://www.w3.org/TR/trace-context-1/[W3C Trace Context] specification.
#
# When this setting is `true`, the agent will also add the header `elastic-apm-traceparent`
# for backwards compatibility with older versions of Elastic APM agents.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# use_elastic_traceparent_header=true

# Use this option to disable `tracecontext` headers injection to any outgoing communication.
#
# NOTE: Disabling `tracecontext` headers injection means that {apm-guide-ref}/apm-distributed-tracing.html[distributed tracing]
# will not work on downstream services.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: false
#
# disable_outgoing_tracecontext_headers=false

# Sets the minimum duration of spans.
# Spans that execute faster than this threshold are attempted to be discarded.
#
# The attempt fails if they lead up to a span that can't be discarded.
# Spans that propagate the trace context to downstream services,
# such as outgoing HTTP requests,
# can't be discarded.
# Additionally, spans that lead to an error or that may be a parent of an async operation can't be discarded.
#
# However, external calls that don't propagate context,
# such as calls to a database, can be discarded using this threshold.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 0ms.
# Default value: 0ms
#
# span_min_duration=0ms

# This config value allows you to specify which cloud provider should be assumed
# for metadata collection. By default, the agent will attempt to detect the cloud
# provider or, if that fails, will use trial and error to collect the metadata.
#
# Valid options: AUTO, AWS, GCP, AZURE, NONE
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: CloudProvider
# Default value: AUTO
#
# cloud_provider=AUTO

# A boolean specifying if the agent should search the class hierarchy for public api annotations (`@CaptureTransaction`, `@CaptureSpan`, `@Traced` and from 1.45.0 `@WithSpan`).
#  When set to `false`, a method is instrumented if it is annotated with a public api annotation.
#   When set to `true` methods overriding annotated methods will be instrumented as well.
#  Either way, methods will only be instrumented if they are included in the configured <<config-application-packages>>.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# enable_public_api_annotation_inheritance=false

# With this option,
# you can group transaction names that contain dynamic parts with a wildcard expression.
# For example,
# the pattern `GET /user/*/cart` would consolidate transactions,
# such as `GET /users/42/cart` and `GET /users/73/cart` into a single transaction name `GET /users/*/cart`,
# hence reducing the transaction name cardinality.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# transaction_name_groups=

# This option allows some control over how the APM agent handles W3C trace-context headers on incoming requests. By default, the `traceparent` and `tracestate` headers are used per W3C spec for distributed tracing. However, in certain cases it can be helpful to not use the incoming `traceparent` header. Some example use cases:
#
# * An Elastic-monitored service is receiving requests with `traceparent` headers from unmonitored services.
# * An Elastic-monitored service is publicly exposed, and does not want tracing data (trace-ids, sampling decisions) to possibly be spoofed by user requests.
#
# Valid values are:
#
# * 'continue': The default behavior. An incoming `traceparent` value is used to continue the trace and determine the sampling decision.
# * 'restart': Always ignores the `traceparent` header of incoming requests. A new trace-id will be generated and the sampling decision will be made based on transaction_sample_rate. A span link will be made to the incoming `traceparent`.
# * 'restart_external': If an incoming request includes the `es` vendor flag in `tracestate`, then any `traceparent` will be considered internal and will be handled as described for 'continue' above. Otherwise, any `traceparent` is considered external and will be handled as described for 'restart' above.
#
# Starting with Elastic Observability 8.2, span links are visible in trace views.
#
# This option is case-insensitive.
#
# Valid options: continue, restart, restart_external
# This setting can be changed at runtime
# Type: TraceContinuationStrategy
# Default value: CONTINUE
#
# trace_continuation_strategy=CONTINUE

# If any baggage key matches any of the patterns provided via this config option, the corresponding baggage key and value will be automatically stored on the corresponding transactions, spans and errors. The baggage keys will be prefixed with "baggage." on storage.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: *
#
# baggage_to_attach=*

############################################
# Datastore                                #
############################################

# The URL path patterns for which the APM agent will capture the request body of outgoing requests to Elasticsearch made with the `elasticsearch-restclient` instrumentation. The default setting captures the body for Elasticsearch REST APIs searches and counts.
#
# The captured request body (if any) is stored on the `span.db.statement` field. Captured request bodies are truncated to a maximum length defined by <<config-long-field-max-length>>.
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: *_search,*_msearch,*_msearch/template,*_search/template,*_count,*_sql,*_eql/search,*_async_search
#
# elasticsearch_capture_body_urls=*_search,*_msearch,*_msearch/template,*_search/template,*_count,*_sql,*_eql/search,*_async_search

# MongoDB command names for which the command document will be captured, limited to common read-only operations by default.
# Set to ` ""` (empty) to disable capture, and `"*"` to capture all (which is discouraged as it may lead to sensitive information capture).
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: find,aggregate,count,distinct,mapReduce
#
# mongodb_capture_statement_commands=find,aggregate,count,distinct,mapReduce

############################################
# HTTP                                     #
############################################

# Configures which content types should be recorded.
#
# The defaults end with a wildcard so that content types like `text/plain; charset=utf-8` are captured as well.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: application/x-www-form-urlencoded*,text/*,application/json*,application/xml*
#
# capture_body_content_types=application/x-www-form-urlencoded*,text/*,application/json*,application/xml*

# Used to restrict requests to certain URLs from being instrumented.
#
# This property should be set to an array containing one or more strings.
# When an incoming HTTP request is detected, its URL will be tested against each element in this list.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: /VAADIN/*,/heartbeat*,/favicon.ico,*.js,*.css,*.jpg,*.jpeg,*.png,*.gif,*.webp,*.svg,*.woff,*.woff2
#
# transaction_ignore_urls=/VAADIN/*,/heartbeat*,/favicon.ico,*.js,*.css,*.jpg,*.jpeg,*.png,*.gif,*.webp,*.svg,*.woff,*.woff2

# Used to restrict requests from certain User-Agents from being instrumented.
#
# When an incoming HTTP request is detected,
# the User-Agent from the request headers will be tested against each element in this list.
# Example: `curl/*`, `*pingdom*`
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# transaction_ignore_user_agents=

# If set to `true`,
# transaction names of unsupported or partially-supported frameworks will be in the form of `$method $path` instead of just `$method unknown route`.
#
# WARNING: If your URLs contain path parameters like `/user/$userId`,
# you should be very careful when enabling this flag,
# as it can lead to an explosion of transaction groups.
# Take a look at the <<config-transaction-name-groups,`transaction_name_groups`>> option on how to mitigate this problem by grouping URLs together.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: false
#
# use_path_as_transaction_name=false

# Deprecated in favor of <<config-transaction-name-groups,`transaction_name_groups`>>.
#
# This option is only considered, when `use_path_as_transaction_name` is active.
#
# With this option, you can group several URL paths together by using a wildcard expression like `/user/*`.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# url_groups=

# Configures that the first n bytes of http-client request bodies shall be captured. Note that only request bodies will be captured for content types matching the <<config-transaction-name-groups,`transaction_name_groups`>> configuration. A value of 0 disables body capturing. Note that even if this option is configured higher, the maximum amount of decoded characters will still be limited by the value of the <<config-long-field-max-length, `long_field_max_length`>> option.
#
# Currently only support for Apache Http Client v4 and v5, HttpUrlConnection, Spring Webflux WebClient and other frameworks building on top of these (e.g. Spring RestTemplate).
#
# This setting can be changed at runtime
# Type: Integer
# Default value: 0
#
# capture_http_client_request_body_size=0

# If `capture_http_client_request_body_size` is configured, by default the request body will be stored in the `http.request.body.orginal` field. This requires APM-server version 8.18+. For compatibility with older APM-server versions, this option can be set to `true`, which will make the agent store the body in the `labels.http_request_body_content` field instead. Note that in this case only a maximum of 1000 characters are supported.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# capture_http_client_request_body_as_label=false

############################################
# Huge Traces                              #
############################################

# Setting this option to true will enable span compression feature.
# Span compression reduces the collection, processing, and storage overhead, and removes clutter from the UI. The tradeoff is that some information such as DB statements of all the compressed spans will not be collected.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# span_compression_enabled=true

# Consecutive spans that are exact match and that are under this threshold will be compressed into a single composite span. This option does not apply to composite spans. This reduces the collection, processing, and storage overhead, and removes clutter from the UI. The tradeoff is that the DB statements of all the compressed spans will not be collected.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 50ms.
# Default value: 50ms
#
# span_compression_exact_match_max_duration=50ms

# Consecutive spans to the same destination that are under this threshold will be compressed into a single composite span. This option does not apply to composite spans. This reduces the collection, processing, and storage overhead, and removes clutter from the UI. The tradeoff is that the DB statements of all the compressed spans will not be collected.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 0ms.
# Default value: 0ms
#
# span_compression_same_kind_max_duration=0ms

# Exit spans are spans that represent a call to an external service, like a database. If such calls are very short, they are usually not relevant and can be ignored.
#
# NOTE: If a span propagates distributed tracing ids, it will not be ignored, even if it is shorter than the configured threshold. This is to ensure that no broken traces are recorded.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes us, ms, s and m. Example: 0ms.
# Default value: 0ms
#
# exit_span_min_duration=0ms

############################################
# JAX-RS                                   #
############################################

# By default, the agent will scan for @Path annotations on the whole class hierarchy, recognizing a class as a JAX-RS resource if the class or any of its superclasses/interfaces has a class level @Path annotation.
# If your application does not use @Path annotation inheritance, set this property to 'false' to only scan for direct @Path annotations. This can improve the startup time of the agent.
#
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: true
#
# enable_jaxrs_annotation_inheritance=true

# By default, the agent will use `ClassName#methodName` for the transaction name of JAX-RS requests.
# If you want to use the URI template from the `@Path` annotation, set the value to `true`.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# use_jaxrs_path_as_transaction_name=false

############################################
# JMX                                      #
############################################

# Report metrics from JMX to the APM Server
#
# Can contain multiple comma separated JMX metric definitions:
#
# ----
# object_name[<JMX object name pattern>] attribute[<JMX attribute>:metric_name=<optional metric name>]
# ----
#
# * `object_name`:
# +
# For more information about the JMX object name pattern syntax,
# see the https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html[`ObjectName` Javadocs].
# * `attribute`:
# +
# The name of the JMX attribute.
# The JMX value has to be either a `Number` or a composite where the composite items are numbers.
# This element can be defined multiple times.
# An attribute can contain optional properties.
# The syntax for that is the same as for https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html[`ObjectName`].
# +
# ** `metric_name`:
# +
# A property within `attribute`.
# This is the name under which the metric will be stored.
# Setting this is optional and will be the same as the `attribute` if not set.
# Note that all JMX metric names will be prefixed with `jvm.jmx.` by the agent.
#
# The agent creates `labels` for each link:https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html#getKeyPropertyList()[JMX key property] such as `type` and `name`.
#
# The link:https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html[JMX object name pattern] supports wildcards. The attribute definition does NOT support wildcards, but a special definition `attribute[*]` is accepted (from 1.44.0) to mean match all possible (numeric) attributes for the associated object name pattern
# The definition `object_name[*:type=*,name=*] attribute[*]` would match all possible JMX metrics
# In the following example, the agent will create a metricset for each memory pool `name` (such as `G1 Old Generation` and `G1 Young Generation`)
#
# ----
# object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime]
# ----
#
# The resulting documents in Elasticsearch look similar to these (metadata omitted for brevity):
#
# [source,json]
# ----
# {
#   "@timestamp": "2019-08-20T16:51:07.512Z",
#   "jvm": {
#     "jmx": {
#       "collection_count": 0,
#       "CollectionTime":   0
#     }
#   },
#   "labels": {
#     "type": "GarbageCollector",
#     "name": "G1 Old Generation"
#   }
# }
# ----
#
# [source,json]
# ----
# {
#   "@timestamp": "2019-08-20T16:51:07.512Z",
#   "jvm": {
#     "jmx": {
#       "collection_count": 2,
#       "CollectionTime":  11
#     }
#   },
#   "labels": {
#     "type": "GarbageCollector",
#     "name": "G1 Young Generation"
#   }
# }
# ----
#
#
# The agent also supports composite values for the attribute value.
# In this example, `HeapMemoryUsage` is a composite value, consisting of `committed`, `init`, `used` and `max`.
# ----
# object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=heap]
# ----
#
# The resulting documents in Elasticsearch look similar to this:
#
# [source,json]
# ----
# {
#   "@timestamp": "2019-08-20T16:51:07.512Z",
#   "jvm": {
#     "jmx": {
#       "heap": {
#         "max":      4294967296,
#         "init":      268435456,
#         "committed": 268435456,
#         "used":       22404496
#       }
#     }
#   },
#   "labels": {
#     "type": "Memory"
#   }
# }
# ----
#
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# capture_jmx_metrics=

############################################
# Logging                                  #
############################################

# Sets the logging level for the agent.
# This option is case-insensitive.
#
# NOTE: `CRITICAL` is a valid option, but it is mapped to `ERROR`; `WARN` and `WARNING` are equivalent;
# `OFF` is only available since version 1.16.0
#
# Valid options: OFF, ERROR, CRITICAL, WARN, WARNING, INFO, DEBUG, TRACE
# This setting can be changed at runtime
# Type: LogLevel
# Default value: INFO
#
# log_level=INFO

# Sets the path of the agent logs.
# The special value `_AGENT_HOME_` is a placeholder for the folder the elastic-apm-agent.jar is in.
# Example: `_AGENT_HOME_/logs/elastic-apm.log`
#
# When set to the special value 'System.out',
# the logs are sent to standard out.
#
# NOTE: When logging to a file,
# the log will be formatted in new-line-delimited JSON.
# When logging to std out, the log will be formatted as plain-text.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value: System.out
#
# log_file=System.out

# Specifying whether and how the agent should automatically reformat application logs
# into {ecs-logging-ref}/intro.html[ECS-compatible JSON], suitable for ingestion into Elasticsearch for
# further Log analysis. This functionality is available for log4j1, log4j2, Logback and `java.util.logging`.
# The ECS log lines will include active trace/transaction/error IDs, if there are such.
#
# This option only applies to pattern layouts/formatters by default.
# See also <<config-log-ecs-formatter-allow-list, `log_ecs_formatter_allow_list`>>.
# To properly ingest and parse ECS JSON logs, follow the {ecs-logging-java-ref}/setup.html#setup-step-2[getting started guide].
#
# Available options:
#
#  - OFF - application logs are not reformatted.
#  - SHADE - agent logs are reformatted and "shade" ECS-JSON-formatted logs are automatically created in
#    addition to the original application logs. Shade logs will have the same name as the original logs,
#    but with the ".ecs.json" extension instead of the original extension. Destination directory for the
#    shade logs can be configured through the <<config-log-ecs-reformatting-dir,`log_ecs_reformatting_dir`>>
#    configuration. Shade logs do not inherit file-rollover strategy from the original logs. Instead, they
#    use their own size-based rollover strategy according to the <<config-log-file-size, `log_file_size`>>
#    configuration and while allowing maximum of two shade log files.
#  - REPLACE - similar to `SHADE`, but the original logs will not be written. This option is useful if
#    you wish to maintain similar logging-related overhead, but write logs to a different location and/or
#    with a different file extension.
#  - OVERRIDE - same log output is used, but in ECS-compatible JSON format instead of the original format.
#
# NOTE: while `SHADE` and `REPLACE` options are only relevant to file log appenders, the `OVERRIDE` option
# is also valid for other appenders, like System out and console.
#
#
# Valid options: OFF, SHADE, REPLACE, OVERRIDE
# This setting can be changed at runtime
# Type: LogEcsReformatting
# Default value: OFF
#
# log_ecs_reformatting=OFF

# A comma-separated list of key-value pairs that will be added as additional fields to all log events.
#  Takes the format `key=value[,key=value[,...]]`, for example: `key1=value1,key2=value2`.
#  Only relevant if <<config-log-ecs-reformatting,`log_ecs_reformatting`>> is set to any option other than `OFF`.
# Additional fields are currently not supported for direct log sending through the agent.
#
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Map
# Default value:
#
# log_ecs_reformatting_additional_fields=

# Only formatters that match an item on this list will be automatically reformatted to ECS when
# <<config-log-ecs-reformatting,`log_ecs_reformatting`>> is set to any option other than `OFF`.
# A formatter is the logging-framework-specific entity that is responsible for the formatting
# of log events. For example, in log4j it would be a `Layout` implementation, whereas in Logback it would
# be an `Encoder` implementation.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: comma separated list
# Default value: *PatternLayout*,org.apache.log4j.SimpleLayout,ch.qos.logback.core.encoder.EchoEncoder,java.util.logging.SimpleFormatter,org.apache.juli.OneLineFormatter,org.springframework.boot.logging.java.SimpleFormatter
#
# log_ecs_formatter_allow_list=*PatternLayout*,org.apache.log4j.SimpleLayout,ch.qos.logback.core.encoder.EchoEncoder,java.util.logging.SimpleFormatter,org.apache.juli.OneLineFormatter,org.springframework.boot.logging.java.SimpleFormatter

# If <<config-log-ecs-reformatting,`log_ecs_reformatting`>> is set to `SHADE` or `REPLACE`,
# the shade log files will be written alongside the original logs in the same directory by default.
# Use this configuration in order to write the shade logs into an alternative destination. Omitting this
# config or setting it to an empty string will restore the default behavior. If relative path is used,
# this path will be used relative to the original logs directory.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# log_ecs_reformatting_dir=

# The size of the log file.
#
# The agent always keeps one history file so that the max total log file size is twice the value of this setting.
#
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: ByteValue
# Default value: 50mb
#
# log_file_size=50mb

# Defines the log format when logging to `System.out`.
#
# When set to `JSON`, the agent will format the logs in an https://github.com/elastic/ecs-logging-java[ECS-compliant JSON format]
# where each log event is serialized as a single line.
#
# Valid options: PLAIN_TEXT, JSON
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: LogFormat
# Default value: PLAIN_TEXT
#
# log_format_sout=PLAIN_TEXT

# Defines the log format when logging to a file.
#
# When set to `JSON`, the agent will format the logs in an https://github.com/elastic/ecs-logging-java[ECS-compliant JSON format]
# where each log event is serialized as a single line.
#
# Valid options: PLAIN_TEXT, JSON
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: LogFormat
# Default value: PLAIN_TEXT
#
# log_format_file=PLAIN_TEXT

# Sends agent and application logs directly to APM Server.
#
# Note that logs can get lost if the agent can't keep up with the logs,
# if APM Server is not available,
# or if Elasticsearch can't index the logs fast enough.
#
# For better delivery guarantees, it's recommended to ship ECS JSON log files with Filebeat
# See also <<config-log-ecs-reformatting,`log_ecs_reformatting`>>.
# Log sending does not currently support custom MDC fields, `log_ecs_reformatting` and shipping the logs with Filebeat must be used if custom MDC fields are required.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: false
#
# log_sending=false

############################################
# Messaging                                #
############################################

# Used to filter out specific messaging queues/topics from being traced.
#
# This property should be set to an array containing one or more strings.
# When set, sends-to and receives-from the specified queues/topic will be ignored.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# ignore_message_queues=

# Defines which packages contain JMS MessageListener implementations for instrumentation.
# When empty (default), all inner-classes or any classes that have 'Listener' or 'Message' in their names are considered.
#
# This configuration option helps to make MessageListener type matching faster and improve application startup performance.
#
# Starting from version 1.43.0, the classes that are part of the 'application_packages' option are also included in the list of classes considered.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: comma separated list
# Default value:
#
# jms_listener_packages=

# Defines whether the agent should use the exchanges, the routing key or the queue for the naming of RabbitMQ Transactions. Valid options are `QUEUE`, `ROUTING_KEY` and `EXCHANGE`.
# Note that `QUEUE` only works when using RabbitMQ via spring-amqp and `ROUTING_KEY` only works for the non spring-client.
#
# Valid options: EXCHANGE, QUEUE, ROUTING_KEY
# This setting can be changed at runtime
# Type: RabbitMQNamingMode
# Default value: EXCHANGE
#
# rabbitmq_naming_mode=EXCHANGE

############################################
# Metrics                                  #
############################################

# Replaces dots with underscores in the metric names for Micrometer metrics.
#
# WARNING: Setting this to `false` can lead to mapping conflicts as dots indicate nesting in Elasticsearch.
# An example of when a conflict happens is two metrics with the name `foo` and `foo.bar`.
# The first metric maps `foo` to a number and the second metric maps `foo` as an object.
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# dedot_custom_metrics=true

# Defines the default bucket boundaries to use for OpenTelemetry histograms.
#
# Note that for OpenTelemetry 1.32.0 or newer this setting will only work when using API only. The default buckets will not be applied when bringing your own SDK.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: comma separated list
# Default value: 0.00390625,0.00552427,0.0078125,0.0110485,0.015625,0.0220971,0.03125,0.0441942,0.0625,0.0883883,0.125,0.176777,0.25,0.353553,0.5,0.707107,1.0,1.41421,2.0,2.82843,4.0,5.65685,8.0,11.3137,16.0,22.6274,32.0,45.2548,64.0,90.5097,128.0,181.019,256.0,362.039,512.0,724.077,1024.0,1448.15,2048.0,2896.31,4096.0,5792.62,8192.0,11585.2,16384.0,23170.5,32768.0,46341.0,65536.0,92681.9,131072.0
#
# custom_metrics_histogram_boundaries=0.00390625,0.00552427,0.0078125,0.0110485,0.015625,0.0220971,0.03125,0.0441942,0.0625,0.0883883,0.125,0.176777,0.25,0.353553,0.5,0.707107,1.0,1.41421,2.0,2.82843,4.0,5.65685,8.0,11.3137,16.0,22.6274,32.0,45.2548,64.0,90.5097,128.0,181.019,256.0,362.039,512.0,724.077,1024.0,1448.15,2048.0,2896.31,4096.0,5792.62,8192.0,11585.2,16384.0,23170.5,32768.0,46341.0,65536.0,92681.9,131072.0

# Limits the number of active metric sets.
# The metrics sets have associated labels, and the metrics sets are held internally in a map using the labels as keys. The map is limited in size by this option to prevent unbounded growth. If you hit the limit, you'll receive a warning in the agent log.
# The recommended option to workaround the limit is to try to limit the cardinality of the labels, eg naming your transactions so that there are fewer distinct transaction names.
# But if you must, you can use this option to increase the limit.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Integer
# Default value: 1000
#
# metric_set_limit=1000

# Enables metrics which capture the health state of the agent's event reporting mechanism.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# agent_reporter_health_metrics=false

# Enables metrics which capture the resource consumption of agent background tasks.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# agent_background_overhead_metrics=false

############################################
# Profiling                                #
############################################

# If enabled, the apm agent will correlate it's transaction with the profiling data from elastic universal profiling running on the same host.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# universal_profiling_integration_enabled=false

# The feature needs to buffer ended local-root spans for a short duration to ensure that all of its profiling data has been received.This configuration option configures the buffer size in number of spans. The higher the number of local root spans per second, the higher this buffer size should be set.
# The agent will log a warning if it is not capable of buffering a span due to insufficient buffer size. This will cause the span to be exported immediately instead with possibly incomplete profiling correlation data.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Integer
# Default value: 4096
#
# universal_profiling_integration_buffer_size=4096

# The extension needs to bind a socket to a file for communicating with the universal profiling host agent.This configuration option can be used to change the location. Note that the total path name (including the socket) must not exceed 100 characters due to OS restrictions.
# If unset, the value of the `java.io.tmpdir` system property will be used.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# universal_profiling_integration_socket_dir=

# Set to `true` to make the agent create spans for method executions based on
# https://github.com/jvm-profiling-tools/async-profiler[async-profiler], a sampling aka statistical profiler.
#
# Due to the nature of how sampling profilers work,
# the duration of the inferred spans are not exact, but only estimations.
# The <<config-profiling-inferred-spans-sampling-interval, `profiling_inferred_spans_sampling_interval`>> lets you fine tune the trade-off between accuracy and overhead.
#
# The inferred spans are created after a profiling session has ended.
# This means there is a delay between the regular and the inferred spans being visible in the UI.
#
# Only platform threads are supported. Virtual threads are not supported and will not be profiled.
#
# NOTE: This feature is not available on Windows and on OpenJ9
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: false
#
# profiling_inferred_spans_enabled=false

# By default, async profiler prints warning messages about missing JVM symbols to standard output.
# Set this option to `false` to suppress such messages
#
# This setting can be changed at runtime
# Type: Boolean
# Default value: true
#
# profiling_inferred_spans_logging_enabled=true

# The frequency at which stack traces are gathered within a profiling session.
# The lower you set it, the more accurate the durations will be.
# This comes at the expense of higher overhead and more spans for potentially irrelevant operations.
# The minimal duration of a profiling-inferred span is the same as the value of this setting.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 50ms.
# Default value: 50ms
#
# profiling_inferred_spans_sampling_interval=50ms

# The minimum duration of an inferred span.
# Note that the min duration is also implicitly set by the sampling interval.
# However, increasing the sampling interval also decreases the accuracy of the duration of inferred spans.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 0ms.
# Default value: 0ms
#
# profiling_inferred_spans_min_duration=0ms

# If set, the agent will only create inferred spans for methods which match this list.
# Setting a value may slightly reduce overhead and can reduce clutter by only creating spans for the classes you are interested in.
# Example: `org.example.myapp.*`
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: *
#
# profiling_inferred_spans_included_classes=*

# Excludes classes for which no profiler-inferred spans should be created.
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value: (?-i)java.*,(?-i)javax.*,(?-i)sun.*,(?-i)com.sun.*,(?-i)jdk.*,(?-i)org.apache.tomcat.*,(?-i)org.apache.catalina.*,(?-i)org.apache.coyote.*,(?-i)org.jboss.as.*,(?-i)org.glassfish.*,(?-i)org.eclipse.jetty.*,(?-i)com.ibm.websphere.*,(?-i)io.undertow.*
#
# profiling_inferred_spans_excluded_classes=(?-i)java.*,(?-i)javax.*,(?-i)sun.*,(?-i)com.sun.*,(?-i)jdk.*,(?-i)org.apache.tomcat.*,(?-i)org.apache.catalina.*,(?-i)org.apache.coyote.*,(?-i)org.jboss.as.*,(?-i)org.glassfish.*,(?-i)org.eclipse.jetty.*,(?-i)com.ibm.websphere.*,(?-i)io.undertow.*

# Profiling requires that the https://github.com/jvm-profiling-tools/async-profiler[async-profiler] shared library is exported to a temporary location and loaded by the JVM.
# The partition backing this location must be executable, however in some server-hardened environments, `noexec` may be set on the standard `/tmp` partition, leading to `java.lang.UnsatisfiedLinkError` errors.
# Set this property to an alternative directory (e.g. `/var/tmp`) to resolve this.
# If unset, the value of the `java.io.tmpdir` system property will be used.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# profiling_inferred_spans_lib_directory=

############################################
# Reporter                                 #
############################################

# This string is used to ensure that only your agents can send data to your APM server.
#
# Both the agents and the APM server have to be configured with the same secret token.
# Use if APM Server requires a token.
#
# This setting can be changed at runtime
# Type: String
# Default value:
#
# secret_token=

# This string is used to ensure that only your agents can send data to your APM server.
#
# Agents can use API keys as a replacement of secret token, APM server can have multiple API keys.
# When both secret token and API key are used, API key has priority and secret token is ignored.
# Use if APM Server requires an API key.
#
# This setting can be changed at runtime
# Type: String
# Default value:
#
# api_key=

# The URL for your APM Server
#
# The URL must be fully qualified, including protocol (http or https) and port.
#
# If SSL is enabled on the APM Server, use the `https` protocol. For more information, see
# <<ssl-configuration>>.
#
# If outgoing HTTP traffic has to go through a proxy,
# you can use the Java system properties `http.proxyHost` and `http.proxyPort` to set that up.
# See also https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html[Java's proxy documentation]
# for more information.
#
# NOTE: This configuration can only be reloaded dynamically as of 1.8.0
#
# This setting can be changed at runtime
# Type: URL
# Default value: http://127.0.0.1:8200
#
# server_url=http://127.0.0.1:8200

# The URLs for your APM Servers
#
# The URLs must be fully qualified, including protocol (http or https) and port.
#
# Fails over to the next APM Server URL in the event of connection errors.
# Achieves load-balancing by shuffling the list of configured URLs.
# When multiple agents are active, they'll tend towards spreading evenly across the set of servers due to randomization.
#
# If SSL is enabled on the APM Server, use the `https` protocol. For more information, see
# <<ssl-configuration>>.
#
# If outgoing HTTP traffic has to go through a proxy,
# you can use the Java system properties `http.proxyHost` and `http.proxyPort` to set that up.
# See also https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html[Java's proxy documentation]
# for more information.
#
# NOTE: This configuration is specific to the Java agent and does not align with any other APM agent. In order
# to use a cross-agent config, use <<config-server-url>> instead, which is the recommended option regardless if you
# are only setting a single URL.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# server_urls=

# If set to `true`, the agent will work as usual, except from any task requiring communication with
# the APM server. Events will be dropped and the agent won't be able to receive central configuration, which
# means that any other configuration cannot be changed in this state without restarting the service.
# An example use case for this would be maintaining the ability to create traces and log
# trace/transaction/span IDs through the log correlation feature, without setting up an APM Server.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# disable_send=false

# Server timeout
#
# If a request to the APM server takes longer than the configured timeout,
# the request is cancelled and the event (exception or transaction) is discarded.
# Set to 0 to disable timeouts.
#
# WARNING: If timeouts are disabled or set to a high value, your app could experience memory issues if the APM server times out.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 5s.
# Default value: 5s
#
# server_timeout=5s

# By default, the agent verifies the SSL certificate if you use an HTTPS connection to the APM server.
#
# Verification can be disabled by changing this setting to false.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: true
#
# verify_server_cert=true

# The maximum size of buffered events.
#
# Events like transactions and spans are buffered when the agent can't keep up with sending them to the APM Server or if the APM server is down.
#
# If the queue is full, events are rejected which means you will lose transactions and spans in that case.
# This guards the application from crashing in case the APM server is unavailable for a longer period of time.
#
# A lower value will decrease the heap overhead of the agent,
# while a higher value makes it less likely to lose events in case of a temporary spike in throughput.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Integer
# Default value: 512
#
# max_queue_size=512

# Whether each transaction should have the process arguments attached.
# Disabled by default to save disk space.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Boolean
# Default value: false
#
# include_process_args=false

# Maximum time to keep an HTTP request to the APM Server open for.
#
# NOTE: This value has to be lower than the APM Server's `read_timeout` setting.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 10s.
# Default value: 10s
#
# api_request_time=10s

# The maximum total compressed size of the request body which is sent to the APM server intake api via a chunked encoding (HTTP streaming).
# Note that a small overshoot is possible.
#
# Allowed byte units are `b`, `kb` and `mb`. `1kb` is equal to `1024b`.
#
# This setting can be changed at runtime
# Type: ByteValue
# Default value: 768kb
#
# api_request_size=768kb

# The interval at which the agent sends metrics to the APM Server, rounded down to the nearest second (ie 3783ms would be applied as 3000ms).
# If there is an interval (step) defined in the Meter, that interval (to the nearest second) will instead be used, for that Meter. If the Meter step interval is less than 1 second, the meter will not be reported.
# Must be at least `1s`.
# Set to `0s` to deactivate.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 30s.
# Default value: 30s
#
# metrics_interval=30s

# Disables the collection of certain metrics.
# If the name of a metric matches any of the wildcard expressions, it will not be collected.
# Example: `foo.*,bar.*`
#
# This option supports the wildcard `*`, which matches zero or more characters.
# Examples: `/foo/*/bar/*/baz*`, `*foo*`.
# Matching is case insensitive by default.
# Prepending an element with `(?-i)` makes the matching case sensitive.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: comma separated list
# Default value:
#
# disable_metrics=

############################################
# Serverless                               #
############################################

# This config option must be used when running the agent in an AWS Lambda context.
# This config value allows to specify the fully qualified name of the class handling the lambda function.
# An empty value (default value) indicates that the agent is not running within an AWS lambda function.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: String
# Default value:
#
# aws_lambda_handler=

# This config value allows to specify the timeout in milliseconds for flushing APM data at the end of a serverless function.
# For serverless functions, APM data is written in a synchronous way, thus, blocking the termination of the function util data is written or the specified timeout is reached.
#
# This setting can not be changed at runtime. Changes require a restart of the application.
# Type: Long
# Default value: 1000
#
# data_flush_timeout=1000

############################################
# Stacktrace                               #
############################################

# Used to determine whether a stack trace frame is an 'in-app frame' or a 'library frame'.
# This allows the APM app to collapse the stack frames of library code,
# and highlight the stack frames that originate from your application.
# Multiple root packages can be set as a comma-separated list;
# there's no need to configure sub-packages.
# Because this setting helps determine which classes to scan on startup,
# setting this option can also improve startup time.
#
# You must set this option in order to use the API annotations `@CaptureTransaction` and `@CaptureSpan`.
#
# **Example**
#
# Most Java projects have a root package, e.g. `com.myproject`. You can set the application package using Java system properties:
# `-Delastic.apm.application_packages=com.myproject`
#
# If you are only interested in specific subpackages, you can separate them with commas:
# `-Delastic.apm.application_packages=com.myproject.api,com.myproject.impl`
#
# NOTE: the instrumentation aspect of this configuration option - specifying which classes to scan - only applies at startup of the agent and changing the value later won't affect which classesgot scanned. The UI aspect, showing where stack frames can be collapsed, can be changed at any time.
#
# This setting can be changed at runtime
# Type: comma separated list
# Default value:
#
# application_packages=

# Setting it to 0 will disable stack trace collection. Any positive integer value will be used as the maximum number of frames to collect. Setting it -1 means that all frames will be collected.
#
# This setting can be changed at runtime
# Type: Integer
# Default value: 50
#
# stack_trace_limit=50

# While this is very helpful to find the exact place in your code that causes the span, collecting this stack trace does have some overhead.
# When setting this option to value `0ms`, stack traces will be collected for all spans. Setting it to a positive value, e.g. `5ms`, will limit stack trace collection to spans with durations equal to or longer than the given value, e.g. 5 milliseconds.
#
# To disable stack trace collection for spans completely, set the value to `-1ms`.
#
# This setting can be changed at runtime
# Type: TimeDuration
# Supports the duration suffixes ms, s and m. Example: 5ms.
# Default value: 5ms
#
# span_stack_trace_min_duration=5ms

```
