---
navigation_title: "Core"
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html
---

# Core configuration options [config-core]



## `recording` ([1.15.0]) [config-recording]

::::{note}
This option was available in older versions through the `active` key. The old key is still supported in newer versions, but it is now deprecated.
::::


A boolean specifying if the agent should be recording or not. When recording, the agent instruments incoming HTTP requests, tracks errors and collects and sends metrics. When not recording, the agent works as a noop, not collecting data and not communicating with the APM sever, except for polling the central configuration endpoint. Note that trace context propagation, baggage and log correlation will also be disabled when recording is disabled. As this is a reversible switch, agent threads are not being killed when inactivated, but they will be mostly idle in this state, so the overhead should be negligible.

You can use this setting to dynamically disable Elastic APM at runtime.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.recording` | `recording` | `ELASTIC_APM_RECORDING` |


## `enabled` ([1.18.0]) [config-enabled]

Setting to false will completely disable the agent, including instrumentation and remote config polling. If you want to dynamically change the status of the agent, use [`recording`](#config-recording) instead.

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.enabled` | `enabled` | `ELASTIC_APM_ENABLED` |


## `instrument` ([1.0.0]) [config-instrument]

A boolean specifying if the agent should instrument the application to collect traces for the app. When set to `false`, most built-in instrumentation plugins are disabled, which would minimize the effect on your application. However, the agent would still apply instrumentation related to manual tracing options and it would still collect and send metrics to APM Server.

::::{note}
Both active and instrument needs to be true for instrumentation to be running.
::::


::::{note}
Changing this value at runtime can slow down the application temporarily.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.instrument` | `instrument` | `ELASTIC_APM_INSTRUMENT` |


## `service_name` [config-service-name]

This is used to keep all the errors and transactions of your service together and is the primary filter in the Elastic APM user interface.

Instead of configuring the service name manually, you can also choose to rely on the service name auto-detection mechanisms of the agent. If `service_name` is set explicitly, all auto-detection mechanisms are disabled.

This is how the service name auto-detection works:

* For standalone applications

    * The agent uses `Implementation-Title` in the `META-INF/MANIFEST.MF` file if the application is started via `java -jar`.
    * Falls back to the name of the main class or jar file.

* For applications that are deployed to a servlet container/application server, the agent auto-detects the name for each application.

    * For Spring-based applications, the agent uses the `spring.application.name` property, if set.
    * For servlet-based applications, falls back to the `Implementation-Title` in the `META-INF/MANIFEST.MF` file.
    * Falls back to the `display-name` of the `web.xml`, if available.
    * Falls back to the servlet context path the application is mapped to (unless mapped to the root context).


Generally, it is recommended to rely on the service name detection based on `META-INF/MANIFEST.MF`. Spring Boot automatically adds the relevant manifest entries. For other applications that are built with Maven, this is how you add the manifest entries:

```xml
    <build>
        <plugins>
            <plugin>
                <!-- replace with 'maven-war-plugin' if you're building a war -->
                <artifactId>maven-jar-plugin</artifactId>
                <configuration>
                    <archive>
                        <!-- Adds
                        Implementation-Title based on ${project.name} and
                        Implementation-Version based on ${project.version}
                        -->
                        <manifest>
                            <addDefaultImplementationEntries>true</addDefaultImplementationEntries>
                        </manifest>
                        <!-- To customize the Implementation-* entries, remove addDefaultImplementationEntries and add them manually
                        <manifestEntries>
                            <Implementation-Title>foo</Implementation-Title>
                            <Implementation-Version>4.2.0</Implementation-Version>
                        </manifestEntries>
                        -->
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

The service name must conform to this regular expression: `^[a-zA-Z0-9 _-]+$`. In less regexy terms: Your service name must only contain characters from the ASCII alphabet, numbers, dashes, underscores and spaces.

::::{note}
Service name auto discovery mechanisms require APM Server 7.0+.
::::


| Default | Type | Dynamic |
| --- | --- | --- |
| Auto-detected based on the rules described above | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.service_name` | `service_name` | `ELASTIC_APM_SERVICE_NAME` |


## `service_node_name` ([1.11.0]) [config-service-node-name]

If set, this name is used to distinguish between different nodes of a service, therefore it should be unique for each JVM within a service. If not set, data aggregations will be done based on a container ID (where valid) or on the reported hostname (automatically discovered or manually configured through [`hostname`](#config-hostname)).

::::{note}
JVM metrics views rely on aggregations that are based on the service node name. If you have multiple JVMs installed on the same host reporting data for the same service name, you must set a unique node name for each in order to view metrics at the JVM level.
::::


::::{note}
Metrics views can utilize this configuration since APM Server 7.5
::::


| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.service_node_name` | `service_node_name` | `ELASTIC_APM_SERVICE_NODE_NAME` |


## `service_version` [config-service-version]

A version string for the currently deployed version of the service. If you don’t version your deployments, the recommended value for this field is the commit identifier of the deployed revision, e.g. the output of git rev-parse HEAD.

Similar to the auto-detection of [`service_name`](#config-service-name), the agent can auto-detect the service version based on the `Implementation-Title` attribute in `META-INF/MANIFEST.MF`. See [`service_name`](#config-service-name) on how to set this attribute.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.service_version` | `service_version` | `ELASTIC_APM_SERVICE_VERSION` |


## `hostname` ([1.10.0]) [config-hostname]

Allows for the reported hostname to be manually specified. If unset the hostname will be looked up.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.hostname` | `hostname` | `ELASTIC_APM_HOSTNAME` |


## `environment` [config-environment]

The name of the environment this service is deployed in, e.g. "production" or "staging".

Environments allow you to easily filter data on a global level in the APM app. It’s important to be consistent when naming environments across agents. See [environment selector](docs-content://solutions/observability/apm/filter-data.md#apm-filter-your-data-service-environment-filter) in the APM app for more information.

::::{note}
This feature is fully supported in the APM app in Kibana versions >= 7.2. You must use the query bar to filter for a specific environment in versions prior to 7.2.
::::


| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.environment` | `environment` | `ELASTIC_APM_ENVIRONMENT` |


## `transaction_sample_rate` (performance) [config-transaction-sample-rate]

By default, the agent will sample every transaction (e.g. request to your service). To reduce overhead and storage requirements, you can set the sample rate to a value between 0.0 and 1.0. (For pre-8.0 servers the agent still records and sends overall time and the result for unsampled transactions, but no context information, labels, or spans. When connecting to 8.0+ servers, the unsampled requests are not sent at all).

Value will be rounded with 4 significant digits, as an example, value *0.55555* will be rounded to `0.5556`

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `1` | Double | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.transaction_sample_rate` | `transaction_sample_rate` | `ELASTIC_APM_TRANSACTION_SAMPLE_RATE` |


## `transaction_max_spans` (performance) [config-transaction-max-spans]

Limits the amount of spans that are recorded per transaction.

This is helpful in cases where a transaction creates a very high amount of spans (e.g. thousands of SQL queries).

Setting an upper limit will prevent overloading the agent and the APM server with too much work for such edge cases.

A message will be logged when the max number of spans has been exceeded but only at a rate of once every 5 minutes to ensure performance is not impacted.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `500` | Integer | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.transaction_max_spans` | `transaction_max_spans` | `ELASTIC_APM_TRANSACTION_MAX_SPANS` |


## `long_field_max_length` (performance [1.37.0]) [config-long-field-max-length]

The following transaction, span, and error fields will be truncated at this number of unicode characters before being sent to APM server:

* `transaction.context.request.body`, `error.context.request.body`
* `transaction.context.message.body`, `error.context.message.body`
* `span.context.db.statement`

Note that tracing data is limited at the upstream APM server to [`max_event_size`](docs-content://solutions/observability/apm/general-configuration-options.md#apm-max_event_size), which defaults to 300kB. If you configure `long_field_max_length` too large, it could result in transactions, spans, or errors that are rejected by APM server.

| Default | Type | Dynamic |
| --- | --- | --- |
| `10000` | Integer | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.long_field_max_length` | `long_field_max_length` | `ELASTIC_APM_LONG_FIELD_MAX_LENGTH` |


## `sanitize_field_names` (security) [config-sanitize-field-names]

Sometimes it is necessary to sanitize the data sent to Elastic APM, e.g. remove sensitive data.

Configure a list of wildcard patterns of field names which should be sanitized. These apply for example to HTTP headers and `application/x-www-form-urlencoded` data.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

::::{note}
Data in the query string is considered non-sensitive, as sensitive information should not be sent in the query string. See [https://www.owasp.org/index.php/Information_exposure_through_query_strings_in_url](https://www.owasp.org/index.php/Information_exposure_through_query_strings_in_url) for more information
::::


::::{note}
Review the data captured by Elastic APM carefully to make sure it does not capture sensitive information. If you do find sensitive data in the Elasticsearch index, you should add an additional entry to this list (make sure to also include the default entries).
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `password, passwd, pwd, secret, *key, *token*, *session*, *credit*, *card*, *auth*, *principal*, set-cookie` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.sanitize_field_names` | `sanitize_field_names` | `ELASTIC_APM_SANITIZE_FIELD_NAMES` |


## `enable_instrumentations` ([1.28.0]) [config-enable-instrumentations]

A list of instrumentations which should be selectively enabled. Valid options are `annotations`, `annotations-capture-span`, `annotations-capture-transaction`, `annotations-traced`, `apache-commons-exec`, `apache-httpclient`, `asynchttpclient`, `aws-lambda`, `aws-sdk`, `cassandra`, `concurrent`, `dubbo`, `elasticsearch-restclient`, `exception-handler`, `executor`, `executor-collection`, `experimental`, `finagle-httpclient`, `fork-join`, `grails`, `grpc`, `hibernate-search`, `http-client`, `jakarta-websocket`, `java-ldap`, `javalin`, `javax-websocket`, `jax-rs`, `jax-ws`, `jdbc`, `jdk-httpclient`, `jdk-httpserver`, `jedis`, `jms`, `jsf`, `kafka`, `lettuce`, `log-correlation`, `log-error`, `log-reformatting`, `logging`, `micrometer`, `mongodb`, `mongodb-client`, `okhttp`, `opentelemetry`, `opentelemetry-annotations`, `opentelemetry-metrics`, `opentracing`, `process`, `public-api`, `quartz`, `rabbitmq`, `reactor`, `redis`, `redisson`, `render`, `scala-future`, `scheduled`, `servlet-api`, `servlet-api-async`, `servlet-api-dispatch`, `servlet-input-stream`, `servlet-service-name`, `servlet-version`, `sparkjava`, `spring-amqp`, `spring-mvc`, `spring-resttemplate`, `spring-service-name`, `spring-view-render`, `spring-webclient`, `spring-webflux`, `ssl-context`, `struts`, `timer-task`, `urlconnection`, `vertx`, `vertx-web`, `vertx-webclient`, `websocket`. When set to non-empty value, only listed instrumentations will be enabled if they are not disabled through [`disable_instrumentations` ([1.0.0])](#config-disable-instrumentations) or [`enable_experimental_instrumentations` ([1.25.0])](#config-enable-experimental-instrumentations). When not set or empty (default), all instrumentations enabled by default will be enabled unless they are disabled through [`disable_instrumentations` ([1.0.0])](#config-disable-instrumentations) or [`enable_experimental_instrumentations` ([1.25.0])](#config-enable-experimental-instrumentations).

::::{note}
Changing this value at runtime can slow down the application temporarily.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | Collection | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.enable_instrumentations` | `enable_instrumentations` | `ELASTIC_APM_ENABLE_INSTRUMENTATIONS` |


## `disable_instrumentations` ([1.0.0]) [config-disable-instrumentations]

A list of instrumentations which should be disabled. Valid options are `annotations`, `annotations-capture-span`, `annotations-capture-transaction`, `annotations-traced`, `apache-commons-exec`, `apache-httpclient`, `asynchttpclient`, `aws-lambda`, `aws-sdk`, `cassandra`, `concurrent`, `dubbo`, `elasticsearch-restclient`, `exception-handler`, `executor`, `executor-collection`, `experimental`, `finagle-httpclient`, `fork-join`, `grails`, `grpc`, `hibernate-search`, `http-client`, `jakarta-websocket`, `java-ldap`, `javalin`, `javax-websocket`, `jax-rs`, `jax-ws`, `jdbc`, `jdk-httpclient`, `jdk-httpserver`, `jedis`, `jms`, `jsf`, `kafka`, `lettuce`, `log-correlation`, `log-error`, `log-reformatting`, `logging`, `micrometer`, `mongodb`, `mongodb-client`, `okhttp`, `opentelemetry`, `opentelemetry-annotations`, `opentelemetry-metrics`, `opentracing`, `process`, `public-api`, `quartz`, `rabbitmq`, `reactor`, `redis`, `redisson`, `render`, `scala-future`, `scheduled`, `servlet-api`, `servlet-api-async`, `servlet-api-dispatch`, `servlet-input-stream`, `servlet-service-name`, `servlet-version`, `sparkjava`, `spring-amqp`, `spring-mvc`, `spring-resttemplate`, `spring-service-name`, `spring-view-render`, `spring-webclient`, `spring-webflux`, `ssl-context`, `struts`, `timer-task`, `urlconnection`, `vertx`, `vertx-web`, `vertx-webclient`, `websocket`. For version `1.25.0` and later, use [`enable_experimental_instrumentations` ([1.25.0])](#config-enable-experimental-instrumentations) to enable experimental instrumentations.

::::{note}
Changing this value at runtime can slow down the application temporarily.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | Collection | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.disable_instrumentations` | `disable_instrumentations` | `ELASTIC_APM_DISABLE_INSTRUMENTATIONS` |


## `enable_experimental_instrumentations` ([1.25.0]) [config-enable-experimental-instrumentations]

Whether to apply experimental instrumentations.

::::{note}
Changing this value at runtime can slow down the application temporarily. Setting to `true` will enable instrumentations in the `experimental` group.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.enable_experimental_instrumentations` | `enable_experimental_instrumentations` | `ELASTIC_APM_ENABLE_EXPERIMENTAL_INSTRUMENTATIONS` |


## `unnest_exceptions` [config-unnest-exceptions]

When reporting exceptions, un-nests the exceptions matching the wildcard pattern. This can come in handy for Spring’s `org.springframework.web.util.NestedServletException`, for example.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `(?-i)*Nested*Exception` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.unnest_exceptions` | `unnest_exceptions` | `ELASTIC_APM_UNNEST_EXCEPTIONS` |


## `ignore_exceptions` ([1.11.0]) [config-ignore-exceptions]

A list of exceptions that should be ignored and not reported as errors. This allows to ignore exceptions thrown in regular control flow that are not actual errors

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

Examples:

* `com.mycompany.ExceptionToIgnore`: using fully qualified name
* `*ExceptionToIgnore`: using wildcard to avoid package name
* `*exceptiontoignore`: case-insensitive by default

::::{note}
Exception inheritance is not supported, thus you have to explicitly list all the thrown exception types
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.ignore_exceptions` | `ignore_exceptions` | `ELASTIC_APM_IGNORE_EXCEPTIONS` |


## `capture_body` (performance) [config-capture-body]

For transactions that are HTTP requests, the Java agent can optionally capture the request body (e.g. POST variables). For transactions that are initiated by receiving a message from a message broker, the agent can capture the textual message body.

If the HTTP request or the message has a body and this setting is disabled, the body will be shown as [REDACTED].

This option is case-insensitive.

::::{note}
Currently, the body length is limited to 10000 characters and it is not configurable. If the body size exceeds the limit, it will be truncated.
::::


::::{note}
Currently, only UTF-8 encoded plain text HTTP content types are supported. The option [`capture_body_content_types` ([1.5.0] performance)](/reference/config-http.md#config-capture-body-content-types) determines which content types are captured.
::::


::::{warning}
Request bodies often contain sensitive values like passwords, credit card numbers etc. If your service handles data like this, we advise to only enable this feature with care. Turning on body capturing can also significantly increase the overhead in terms of heap usage, network utilisation and Elasticsearch index size.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Valid options: `off`, `errors`, `transactions`, `all`

| Default | Type | Dynamic |
| --- | --- | --- |
| `OFF` | EventType | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.capture_body` | `capture_body` | `ELASTIC_APM_CAPTURE_BODY` |


## `capture_headers` (performance) [config-capture-headers]

If set to `true`, the agent will capture HTTP request and response headers (including cookies), as well as messages' headers/properties when using messaging frameworks like Kafka or JMS.

::::{note}
Setting this to `false` reduces network bandwidth, disk space and object allocations.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.capture_headers` | `capture_headers` | `ELASTIC_APM_CAPTURE_HEADERS` |


## `global_labels` ([1.7.0]) [config-global-labels]

Labels added to all events, with the format `key=value[,key=value[,...]]`. Any labels set by application via the API will override global labels with the same keys.

::::{note}
This feature requires APM Server 7.2+
::::


| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | Map | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.global_labels` | `global_labels` | `ELASTIC_APM_GLOBAL_LABELS` |


## `instrument_ancient_bytecode` ([1.35.0]) [config-instrument-ancient-bytecode]

A boolean specifying if the agent should instrument pre-Java-1.4 bytecode.

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.instrument_ancient_bytecode` | `instrument_ancient_bytecode` | `ELASTIC_APM_INSTRUMENT_ANCIENT_BYTECODE` |


## `context_propagation_only` ([1.44.0]) [config-context-propagation-only]

When set to true, disables log sending, metrics and trace collection. Trace context propagation and log correlation will stay active. Note that in contrast to [`disable_send`](/reference/config-reporter.md#config-disable-send) the agent will still connect to the APM-server for fetching configuration updates and health checks.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.context_propagation_only` | `context_propagation_only` | `ELASTIC_APM_CONTEXT_PROPAGATION_ONLY` |


## `classes_excluded_from_instrumentation` [config-classes-excluded-from-instrumentation]

Use to exclude specific classes from being instrumented. In order to exclude entire packages, use wildcards, as in: `com.project.exclude.*` This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.classes_excluded_from_instrumentation` | `classes_excluded_from_instrumentation` | `ELASTIC_APM_CLASSES_EXCLUDED_FROM_INSTRUMENTATION` |


## `trace_methods` ([1.0.0]) [config-trace-methods]

A list of methods for which to create a transaction or span.

If you want to monitor a large number of methods, use  [`profiling_inferred_spans_enabled`](/reference/config-profiling.md#config-profiling-inferred-spans-enabled) instead.

This works by instrumenting each matching method to include code that creates a span for the method. While creating a span is quite cheap in terms of performance, instrumenting a whole code base or a method which is executed in a tight loop leads to significant overhead.

Using a pointcut-like syntax, you can match based on

* Method modifier (optional)<br> Example: `public`, `protected`, `private` or `*`
* Package and class name (wildcards include sub-packages)<br> Example: `org.example.*`
* Method name (optional since 1.4.0)<br> Example: `myMeth*d`
* Method argument types (optional)<br> Example: `(*lang.String, int[])`
* Classes with a specific annotation (optional)<br> Example: `@*ApplicationScoped`
* Classes with a specific annotation that is itself annotated with the given meta-annotation (optional)<br> Example: `@@javax.enterpr*se.context.NormalScope`

The syntax is `modifier @fully.qualified.AnnotationName fully.qualified.ClassName#methodName(fully.qualified.ParameterType)`.

A few examples:

* `org.example.*` [1.4.0]
* `org.example.*#*` (before 1.4.0, you need to specify a method matcher)
* `org.example.MyClass#myMethod`
* `org.example.MyClass#myMethod()`
* `org.example.MyClass#myMethod(java.lang.String)`
* `org.example.MyClass#myMe*od(java.lang.String, int)`
* `private org.example.MyClass#myMe*od(java.lang.String, *)`
* `* org.example.MyClas*#myMe*od(*.String, int[])`
* `public org.example.services.*Service#*`
* `public @java.inject.ApplicationScoped org.example.*`
* `public @java.inject.* org.example.*`
* `public @@javax.enterprise.context.NormalScope org.example.*, public @@jakarta.enterprise.context.NormalScope org.example.*`

::::{note}
Only use wildcards if necessary. The more methods you match the more overhead will be caused by the agent. Also note that there is a maximum amount of spans per transaction (see [`transaction_max_spans`](#config-transaction-max-spans)).
::::


::::{note}
The agent will create stack traces for spans which took longer than [`span_stack_trace_min_duration`](/reference/config-stacktrace.md#config-span-stack-trace-min-duration). When tracing a large number of methods (for example by using wildcards), this may lead to high overhead. Consider increasing the threshold or disabling stack trace collection altogether.
::::


Common configurations:

Trace all public methods in CDI-Annotated beans:

```
public @@javax.enterprise.context.NormalScope your.application.package.*
public @@jakarta.enterprise.context.NormalScope your.application.package.*
public @@javax.inject.Scope your.application.package.*
```

::::{note}
This method is only available in the Elastic APM Java Agent.
::::


::::{note}
Changing this value at runtime can slow down the application temporarily.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.trace_methods` | `trace_methods` | `ELASTIC_APM_TRACE_METHODS` |


## `trace_methods_duration_threshold` ([1.7.0]) [config-trace-methods-duration-threshold]

If [`trace_methods`](#config-trace-methods) config option is set, provides a threshold to limit spans based on duration. When set to a value greater than 0, spans representing methods traced based on `trace_methods` will be discarded by default. Such methods will be traced and reported if one of the following applies:

* This method’s duration crossed the configured threshold.
* This method ended with Exception.
* A method executed as part of the execution of this method crossed the threshold or ended with Exception.
* A "forcibly-traced method" (e.g. DB queries, HTTP exits, custom) was executed during the execution of this method.

Set to 0 to disable.

::::{note}
Transactions are never discarded, regardless of their duration. This configuration affects only spans. In order not to break span references, all spans leading to an async operation or an exit span (such as a HTTP request or a DB query) are never discarded, regardless of their duration.
::::


::::{note}
If this option and [`span_min_duration`](#config-span-min-duration) are both configured, the higher of both thresholds will determine which spans will be discarded.
::::


Supports the duration suffixes `ms`, `s` and `m`. Example: `0ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `0ms` | TimeDuration | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.trace_methods_duration_threshold` | `trace_methods_duration_threshold` | `ELASTIC_APM_TRACE_METHODS_DURATION_THRESHOLD` |


## `central_config` ([1.8.0]) [config-central-config]

When enabled, the agent will make periodic requests to the APM Server to fetch updated configuration. The frequency of the periodic request is driven by the `Cache-Control` header returned from APM Server/Integration, falling back to 5 minutes if not defined.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.central_config` | `central_config` | `ELASTIC_APM_CENTRAL_CONFIG` |


## `breakdown_metrics` ([1.8.0]) [config-breakdown-metrics]

Disables the collection of breakdown metrics (`span.self_time`)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.breakdown_metrics` | `breakdown_metrics` | `ELASTIC_APM_BREAKDOWN_METRICS` |


## `config_file` ([1.8.0]) [config-config-file]

Sets the path of the agent config file. The special value `_AGENT_HOME_` is a placeholder for the folder the `elastic-apm-agent.jar` is in. The file has to be on the file system. You can not refer to classpath locations.

::::{note}
this option can only be set via system properties, environment variables or the attacher options.
::::


| Default | Type | Dynamic |
| --- | --- | --- |
| `_AGENT_HOME_/elasticapm.properties` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.config_file` | `config_file` | `ELASTIC_APM_CONFIG_FILE` |


## `plugins_dir` (experimental) [config-plugins-dir]

::::{note}
This feature is currently experimental, which means it is disabled by default and it is not guaranteed to be backwards compatible in future releases.
::::


A folder that contains external agent plugins.

Use the `apm-agent-plugin-sdk` and the `apm-agent-api` artifacts to create a jar and place it into the plugins folder. The agent will load all instrumentations that are declared in the `META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation` service descriptor. See `integration-tests/external-plugin-test` for an example plugin.

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | String | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.plugins_dir` | `plugins_dir` | `ELASTIC_APM_PLUGINS_DIR` |


## `use_elastic_traceparent_header` ([1.14.0]) [config-use-elastic-traceparent-header]

To enable [distributed tracing](docs-content://solutions/observability/apm/traces.md), the agent adds trace context headers to outgoing requests (like HTTP requests, Kafka records, gRPC requests etc.). These headers (`traceparent` and `tracestate`) are defined in the [W3C Trace Context](https://www.w3.org/TR/trace-context-1/) specification.

When this setting is `true`, the agent will also add the header `elastic-apm-traceparent` for backwards compatibility with older versions of Elastic APM agents.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `true` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.use_elastic_traceparent_header` | `use_elastic_traceparent_header` | `ELASTIC_APM_USE_ELASTIC_TRACEPARENT_HEADER` |


## `disable_outgoing_tracecontext_headers` ([1.37.0]) [config-disable-outgoing-tracecontext-headers]

Use this option to disable `tracecontext` headers injection to any outgoing communication.

::::{note}
Disabling `tracecontext` headers injection means that [distributed tracing](docs-content://solutions/observability/apm/traces.md) will not work on downstream services.
::::


[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.disable_outgoing_tracecontext_headers` | `disable_outgoing_tracecontext_headers` | `ELASTIC_APM_DISABLE_OUTGOING_TRACECONTEXT_HEADERS` |


## `span_min_duration` ([1.16.0]) [config-span-min-duration]

Sets the minimum duration of spans. Spans that execute faster than this threshold are attempted to be discarded.

The attempt fails if they lead up to a span that can’t be discarded. Spans that propagate the trace context to downstream services, such as outgoing HTTP requests, can’t be discarded. Additionally, spans that lead to an error or that may be a parent of an async operation can’t be discarded.

However, external calls that don’t propagate context, such as calls to a database, can be discarded using this threshold.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Supports the duration suffixes `ms`, `s` and `m`. Example: `0ms`.

| Default | Type | Dynamic |
| --- | --- | --- |
| `0ms` | TimeDuration | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.span_min_duration` | `span_min_duration` | `ELASTIC_APM_SPAN_MIN_DURATION` |


## `cloud_provider` ([1.21.0]) [config-cloud-provider]

This config value allows you to specify which cloud provider should be assumed for metadata collection. By default, the agent will attempt to detect the cloud provider or, if that fails, will use trial and error to collect the metadata.

Valid options: `AUTO`, `AWS`, `GCP`, `AZURE`, `NONE`

| Default | Type | Dynamic |
| --- | --- | --- |
| `AUTO` | CloudProvider | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.cloud_provider` | `cloud_provider` | `ELASTIC_APM_CLOUD_PROVIDER` |


## `enable_public_api_annotation_inheritance` (performance) [config-enable-public-api-annotation-inheritance]

A boolean specifying if the agent should search the class hierarchy for public api annotations (`@CaptureTransaction`, `@CaptureSpan`, `@Traced` and from 1.45.0 `@WithSpan`). When set to `false`, a method is instrumented if it is annotated with a public api annotation. When set to `true` methods overriding annotated methods will be instrumented as well. Either way, methods will only be instrumented if they are included in the configured [`application_packages`](/reference/config-stacktrace.md#config-application-packages).

| Default | Type | Dynamic |
| --- | --- | --- |
| `false` | Boolean | false |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.enable_public_api_annotation_inheritance` | `enable_public_api_annotation_inheritance` | `ELASTIC_APM_ENABLE_PUBLIC_API_ANNOTATION_INHERITANCE` |


## `transaction_name_groups` ([1.33.0]) [config-transaction-name-groups]

With this option, you can group transaction names that contain dynamic parts with a wildcard expression. For example, the pattern `GET /user/*/cart` would consolidate transactions, such as `GET /users/42/cart` and `GET /users/73/cart` into a single transaction name `GET /users/*/cart`, hence reducing the transaction name cardinality.

This option supports the wildcard `*`, which matches zero or more characters. Examples: `/foo/*/bar/*/baz*`, `*foo*`. Matching is case insensitive by default. Prepending an element with `(?-i)` makes the matching case sensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `<none>` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.transaction_name_groups` | `transaction_name_groups` | `ELASTIC_APM_TRANSACTION_NAME_GROUPS` |


## `trace_continuation_strategy` ([1.34.0]) [config-trace-continuation-strategy]

This option allows some control over how the APM agent handles W3C trace-context headers on incoming requests. By default, the `traceparent` and `tracestate` headers are used per W3C spec for distributed tracing. However, in certain cases it can be helpful to not use the incoming `traceparent` header. Some example use cases:

* An Elastic-monitored service is receiving requests with `traceparent` headers from unmonitored services.
* An Elastic-monitored service is publicly exposed, and does not want tracing data (trace-ids, sampling decisions) to possibly be spoofed by user requests.

Valid values are:

* *continue*: The default behavior. An incoming `traceparent` value is used to continue the trace and determine the sampling decision.
* *restart*: Always ignores the `traceparent` header of incoming requests. A new trace-id will be generated and the sampling decision will be made based on transaction_sample_rate. A span link will be made to the incoming `traceparent`.
* *restart_external*: If an incoming request includes the `es` vendor flag in `tracestate`, then any `traceparent` will be considered internal and will be handled as described for *continue* above. Otherwise, any `traceparent` is considered external and will be handled as described for *restart* above.

Starting with Elastic Observability 8.2, span links are visible in trace views.

This option is case-insensitive.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

Valid options: `continue`, `restart`, `restart_external`

| Default | Type | Dynamic |
| --- | --- | --- |
| `CONTINUE` | TraceContinuationStrategy | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.trace_continuation_strategy` | `trace_continuation_strategy` | `ELASTIC_APM_TRACE_CONTINUATION_STRATEGY` |


## `baggage_to_attach` ([1.43.0]) [config-baggage-to-attach]

If any baggage key matches any of the patterns provided via this config option, the corresponding baggage key and value will be automatically stored on the corresponding transactions, spans and errors. The baggage keys will be prefixed with "baggage." on storage.

[![dynamic config](images/dynamic-config.svg "") ](/reference/configuration.md#configuration-dynamic)

| Default | Type | Dynamic |
| --- | --- | --- |
| `*` | List | true |

| Java System Properties | Property file | Environment |
| --- | --- | --- |
| `elastic.apm.baggage_to_attach` | `baggage_to_attach` | `ELASTIC_APM_BAGGAGE_TO_ATTACH` |

