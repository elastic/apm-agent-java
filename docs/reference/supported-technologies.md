---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/supported-technologies-details.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
---

# Supported technologies [supported-technologies-details]

The Elastic APM Java Agent automatically instruments various APIs, frameworks and application servers. This section lists all supported technologies.

* [Java versions](#supported-java-versions)
* [Web Frameworks](#supported-web-frameworks)
* [Application Servers/Servlet Containers](#supported-app-servers)
* [Data Stores](#supported-databases)
* [Networking frameworks](#supported-networking-frameworks)
* [Asynchronous frameworks](#supported-async-frameworks)
* [Messaging frameworks](#supported-messaging-frameworks)
* [Scheduling frameworks](#supported-scheduling-frameworks)
* [Logging frameworks](#supported-logging-frameworks)
* [Process frameworks](#supported-process-frameworks)
* [RPC frameworks](#supported-rpc-frameworks)
* [AWS Lambda runtimes](#supported-aws-lambda-runtimes)
* [Java method monitoring](#supported-java-methods)
* [Metrics](#supported-metrics)
* [Caveats](#supported-technologies-caveats)

If your favorite technology is not supported yet, you can vote for it by participating in our [survey](https://docs.google.com/forms/d/e/1FAIpQLScd0RYiwZGrEuxykYkv9z8Hl3exx_LKCtjsqEo1OWx8BkLrOQ/viewform?usp=sf_link). We will use the results to add support for the most requested technologies.

Other options are to add a dependency to the agent’s [public API](/reference/public-api.md) in order to programmatically create custom transactions and spans, or to create your own [plugin](/reference/plugin-api.md) that will instrument the technology you want instrumented.

If you want to extend the auto-instrumentation capabilities of the agent, the [contributing guide](https://github.com/elastic/apm-agent-java/blob/main/CONTRIBUTING.md) should get you started.

::::{note}
If, for example, the HTTP client library of your choice is not listed, it means that there won’t be spans for those outgoing HTTP requests. If the web framework you are using is not supported, the agent does not capture transactions.
::::



## Java versions [supported-java-versions]

::::{note}
As of version 1.33.0, Java 7 support is deprecated and will be removed in a future release
::::


| Vendor     | Supported versions                          | Notes                                                                     |
|------------|---------------------------------------------|---------------------------------------------------------------------------|
| Oracle JDK | ≥7u60*, ≥8u40, 9, 10, 11, 17, 21, 25        | `--module-path` has not been tested yet                                   |
| OpenJDK    | 7u60+*, 8u40+, 9, 10, 11, 17, 21, 25        | `--module-path` has not been tested yet                                   |
| IBM J9 VM  | 8 service refresh 5+ (build 2.9 or 8.0.5.0) | [Sampling profiler](/reference/method-sampling-based.md) is not supported |
| HP-UX JVM  | 7.0.10+*, 8.0.02+                           |                                                                           |
| SAP JVM    | 8.1.065+                                    |                                                                           |

* Java 7 support is deprecated and will be removed in a future release

**Early Java 8 and Java 7**

Early Java 8 versions before update 40 are **not supported** because they have several bugs that might result in JVM crashes when a java agent is active, thus agent **will not start** on those versions. Similarly, Java 7 versions before update 60 are not supported as they are buggy in regard to invokedynamic.

Here is an example of the message displayed when this happens.

```
Failed to start agent - JVM version not supported: 1.8.0_31 Java HotSpot(TM) 64-Bit Server VM 25.31-b07.
To override Java version verification, set the 'elastic.apm.disable_bootstrap_checks' System property,
or the `ELASTIC_APM_DISABLE_BOOTSTRAP_CHECKS` environment variable, to 'true'.
```

As this message states, you can disable this check if required by adding `-Delastic.apm.disable_bootstrap_checks=true` to the JVM arguments, or setting `ELASTIC_APM_DISABLE_BOOTSTRAP_CHECKS=true` for the JVM environment variables.


## Web Frameworks [supported-web-frameworks]

| Framework                         | Supported versions | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Since                                                 |
|-----------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| Servlet API                       | ≥ 3.x, ≤ 6.x       | A transaction will be created for all incoming HTTP requests to your Servlet API-based application. Starting in version 1.18.0, additional spans are created if the servlet dispatches execution to another servlet through the `forward` or `include` APIs, or to an error page. See also [Application Servers/Servlet Containers](#supported-app-servers)                                                                                                                                                                                                                                                                                                                  | 1.0.0, 4.0+ (`jakarta.servlet`) since 1.28.0          |
| Spring Web MVC                    | ≥ 4.x, ≤ 6.x       | If you are using Spring MVC (for example with Spring Boot),  the transactions are named based on your controllers (`ControllerClass#controllerMethod`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | 1.0.0, 6.x since 1.38.0                               |
| Spring Webflux                    | ≥ 5.2.3+, ≤ 6.x    | Creates transactions for incoming HTTP requests, supports annotated and functional endpoints.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | 1.24.0 (experimental), 1.34.0 (GA), 6.1+ since 1.45.0 |
| JavaServer Faces                  | ≥ 2.2.x, ≤ 3.0.0   | If you are using JSF, transactions are named based on the requested Facelets and spans are captured for visibility into execution andrendering                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | 1.0.0, `jakarta.faces` since 1.28.0                   |
| Spring Boot                       | ≥ 1.5.x, ≤ 3.x     | Supports embedded Tomcat, Jetty and Undertow                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 1.0.0, 3.x since 1.38.0                               |
| JAX-RS                            | ≥ 2.x, ≤ 3.x       | The transactions are named based on your resources (`ResourceClass#resourceMethod`). Note that only the packages configured in [`application_packages`](/reference/config-stacktrace.md#config-application-packages) are scanned for JAX-RS resources. If you don’t set this option, all classes are scanned. This comes at the cost of increased startup times, however.<br> Note: JAX-RS is only supported when running on a supported [Application Server/Servlet Container](#supported-app-servers).                                                                                                                                                                     | 1.0.0, `jakarta.ws.rs` since 1.28.0                   |
| JAX-WS                            |                    | The transactions are named based on your `@javax.jws.WebService`, `@jakarta.jws.WebService` annotated classes and `@javax.jws.WebMethod`, `@jakarta.jws.WebMethod` annotated method names (`WebServiceClass#webMethod`). Note that only the packages configured in [`application_packages`](/reference/config-stacktrace.md#config-application-packages) are scanned for JAX-WS resources. If you don’t set this option, all classes are scanned. This comes at the cost of increased startup times, however.<br> Note: JAX-WS is only supported when running on a supported [Application Server/Servlet Container](#supported-app-servers) and when using the HTTP binding. | 1.4.0, `jakarta.jws` since 1.28.0                     |
| Grails                            | ≥ 3.x, ≤ 4.x       |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 1.17.0                                                |
| Apache Struts                     | 2.x                | The transactions are named based on your action (`ActionClass#actionMethod`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | 1.24.0                                                |
| Vert.x Web                        | ≥ 3.6, ≤4.x        | Captures incoming HTTP requests as transactions                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 1.24.0 (experimental)                                 |
| Sparkjava (not Apache Spark)      | 2.x                | The transactions are named based on your route (`GET /foo/:bar`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | 1.25.0                                                |
| com.sun.net.httpserver.HttpServer | ≥ 1.7+             | Captures incoming HTTP requests as transactions                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 1.25.0                                                |
| Javalin                           | ≥ 3.13.8+, ≤ 4.x   |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 1.25.0                                                |
| Java API for WebSocket            | 1.0                | Captures methods annotated with `@OnOpen`, `@OnMessage`, `@OnError`, or `@OnClose` as transactions for classes that are annotated with `@ServerEndpoint`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | 1.29.0                                                |


## Application Servers/Servlet Containers [supported-app-servers]

The Elastic APM Java agent has generic support for the Servlet API 3+. However, some servers require special handling. The servers listed here are tested by an integration test suite to make sure Elastic APM is compatible with them. Other Servlet 3+ compliant servers will most likely work as well.

| Server                                                                                             | Supported versions    |
|----------------------------------------------------------------------------------------------------|-----------------------|
| [Tomcat](/reference/setup-javaagent.md#setup-tomcat)                                               | 7.x, 8.5.x, 9.x, 10.x |
| [WildFly](/reference/setup-javaagent.md#setup-jboss-wildfly)                                       | ≥ 8.x, ≤ 29.x         |
| [JBoss EAP](/reference/setup-javaagent.md#setup-jboss-wildfly)                                     | 6.4, 7.x              |
| [Jetty](/reference/setup-javaagent.md#setup-jetty) (only the `ServletContextHandler` is supported) | 9.2, 9.3, 9.4         |
| [WebSphere Liberty](/reference/setup-javaagent.md#setup-websphere-liberty)                         | 8.5.5, 18.0.x         |
| [Undertow Servlet](/reference/setup-javaagent.md#setup-generic)                                    | 1.4                   |
| [Payara](/reference/setup-javaagent.md#setup-payara)                                               | ≥ 4.x, ≤ 5.x          |
| [Oracle WebLogic](/reference/setup-javaagent.md#setup-weblogic)                                    | 12.2                  |


## Data Stores [supported-databases]

| Database                                | Supported versions                        | Description                                                                                                                                                                                                                                                                                                                                                                      | Since                                             |
|-----------------------------------------|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| JDBC                                    | ≥ 4.1, ≤ 4.3                              | The agent automatically creates DB spans for all your JDBC queries. This includes JDBC queries executed by O/R mappers like Hibernate.<br> **Note:** Make sure that your JDBC driver is at least compiled for Java 1.4. Drivers compiled with a lower version are not supported. IBM DB2 db2jcc drivers are also not supported. Please update to db2jcc4.                        | 1.0.0                                             |
| Elasticsearch Java REST and API clients | ≥ 5.0.2+, ≤ 8.9.x                         | The agent automatically creates Elasticsearch spans for queries done through the official REST client.                                                                                                                                                                                                                                                                           | 1.0.0, async since 1.5.0, API Client since 1.32.0 |
| Hibernate Search                        | 5.x (on by default), 6.x (off by default) | The agent automatically creates Hibernate Search spans for queries done through the Hibernate Search API.<br> **Note:** this feature is marked as experimental for version 6.x, which means it is off by default. In order to enable, set the [`disable_instrumentations` ([1.0.0])](/reference/config-core.md#config-disable-instrumentations) config option to an empty string | 1.9.0                                             |
| Redis Jedis                             | ≥ 1.4x, ≤ 5.2.x                           | The agent creates spans for interactions with the Jedis client.                                                                                                                                                                                                                                                                                                                  | 1.10.0, 4+ since 1.31.0                           |
| Redis Lettuce                           | ≥ 3.4.x, ≤ 3.5.x                          | The agent creates spans for interactions with the Lettuce client.                                                                                                                                                                                                                                                                                                                | 1.13.0                                            |
| Redis Redisson                          | ≥ 2.1.5, ≤ 3.13.x                         | The agent creates spans for interactions with the Redisson client.                                                                                                                                                                                                                                                                                                               | 1.15.0                                            |
| MongoDB driver                          | 3.x                                       | The agent creates spans for interactions with the MongoDB driver. At the moment, only the synchronous driver (mongo-java-driver) is supported. The asynchronous and reactive drivers are currently not supported.<br> The name of the span is `<db>.<collection>.<command>`. The actual query will not be recorded.                                                              | 1.12.0                                            |
| MongoDB Sync Driver                     | ≥ 4.x, ≤ 5.x                              | The agent creates spans for interactions with the MongoDB 4.x and 5.x sync driver. This provides support for `org.mongodb:mongodb-driver-sync`                                                                                                                                                                                                                                   | 1.34.0                                            |
| Cassandra                               | ≥ 2.x, ≤ 4.x                              | The agent creates spans for interactions with the Cassandra Datastax drivers.This provides support for `com.datastax.cassandra:cassandra-driver-core` and`com.datastax.oss:java-driver-core`                                                                                                                                                                                     | 1.23.0                                            |
| AWS DynamoDB                            | ≥ 1.x, ≤ 2.x                              | The agent creates spans for interactions with the AWS DynamoDb service through the AWS Java SDK.                                                                                                                                                                                                                                                                                 | 1.31.0, 2.21+ since 1.44.0                        |
| AWS S3                                  | ≥ 1.x, ≤ 2.x                              | The agent creates spans for interactions with the AWS S3 service through the AWS Java SDK.                                                                                                                                                                                                                                                                                       | 1.31.0, 2.21+ since 1.44.0                        |


## Networking frameworks [supported-networking-frameworks]

Distributed tracing will only work if you are using one of the supported networking frameworks.

For the supported HTTP libraries, the agent automatically creates spans for outgoing HTTP requests and propagates tracing headers. The spans are named after the schema `<method> <host>`, for example `GET elastic.co`.

| Framework                  | Supported versions                                       | Note                                                                                                                                                                                                       | Since                                                                                           |
|----------------------------|----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Apache HttpClient          | ≥ 4.3, ≤ 5.x                                             |                                                                                                                                                                                                            | 0.7.0 (4.3+)  1.8.0 (4.0+)  1.45.0 (5.0+)                                                       |
| Apache HttpClient (Legacy) | 3.0.x                                                    | Requires setting [`instrument_ancient_bytecode`](/reference/config-core.md#config-instrument-ancient-bytecode) to `true`                                                                                   | 1.35.0                                                                                          |
| Apache HttpAsyncClient     | 4.0+                                                     |                                                                                                                                                                                                            | 1.6.0                                                                                           |
| Spring RestTemplate        | ≥ 3.1.1, ≤ 6.x                                           |                                                                                                                                                                                                            | 0.7.0                                                                                           |
| OkHttp                     | ≥ 2.x, ≤ 4.x                                             | 4.4+ since 1.22.0                                                                                                                                                                                          | 1.4.0 (synchronous calls via `Call#execute()`) 1.5.0 (async calls via `Call#enquene(Callback)`) |
| HttpUrlConnection          |                                                          |                                                                                                                                                                                                            | 1.4.0                                                                                           |
| JAX-WS client              |                                                          | JAX-WS clients created via [`javax.xml.ws.Service`](https://docs.oracle.com/javaee/7/api/javax/xml/ws/Service.md) inherently support context propagation as they are using `HttpUrlConnection` underneath. | 1.4.0                                                                                           |
| AsyncHttpClient            | 2.x                                                      |                                                                                                                                                                                                            | 1.7.0                                                                                           |
| Apache Dubbo               | 2.x, except for 2.7.0, 2.7.1, 2.7.2 and versions < 2.5.0 |                                                                                                                                                                                                            | 1.17.0                                                                                          |
| JDK 11 HttpClient          |                                                          |                                                                                                                                                                                                            | 1.18.0                                                                                          |
| Vert.x WebClient           | ≥ 3.6+, ≤ 4.x                                            |                                                                                                                                                                                                            | 1.25.0                                                                                          |
| Spring Webclient           | ≥, 5.2.3+, ≤ 6.x                                         |                                                                                                                                                                                                            | 1.33.0 (experimental), 1.34.0 (GA)                                                              |
| Finagle Http Client        | ≥ 22.x                                                   |                                                                                                                                                                                                            | 1.35.0                                                                                          |
| LdapClient                 |                                                          |                                                                                                                                                                                                            | 1.36.0                                                                                          |
| Spring RestClient          | ≥ 6.1.0                                                  |                                                                                                                                                                                                            | 1.45.0                                                                                          |


## Asynchronous frameworks [supported-async-frameworks]

When a Span is created in a different Thread than its parent, the trace context has to be propagated onto this thread.

This section lists all supported asynchronous frameworks.

| Framework                  | Supported versions | Description                                                                                                                                                                                                                                                                              | Since                              |
|----------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|
| `ExecutorService`          |                    | The agent propagates the context for `ExecutorService` s.                                                                                                                                                                                                                                | 1.4.0                              |
| `ScheduledExecutorService` |                    | The agent propagates the context for `ScheduledExecutorService#schedule` (this does not include `scheduleAtFixedRate` or `scheduleWithFixedDelay`.                                                                                                                                       | 1.17.0                             |
| `ForkJoinPool`             |                    | The agent propagates the context for `ForkJoinPool` s.                                                                                                                                                                                                                                   | 1.17.0                             |
| Scala Future               | 2.13.x             | The agent propagates the context when using the `scala.concurrent.Future` or `scala.concurrent.Promise`.It will propagate the context when using chaining methods such as `map`, `flatMap`, `traverse`, … NOTE: To enable Scala Future support, you need to enable experimental plugins. | 1.18.0                             |
| Reactor                    | 3.2.x              | The agent propagates the context for `Flux` and `Mono`.                                                                                                                                                                                                                                  | 1.24.0 (experimental), 1.34.0 (GA) |


## Messaging frameworks [supported-messaging-frameworks]

When using a messaging framework, sender context is propagated so that receiver events are correlated to the same trace.

| Framework | Supported versions                                            | Description                                                                                                | Since                                                |
|-----------|---------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| JMS       | 1.1, 2.0                                                      | The agent captures JMS sends and receives as spans/transactions                                            | `javax.jms` since 1.13.0, `jakarta.jms` since 1.40.0 |
| Kafka     | <0.11.0 - without distributed tracing; 0.11.0+ - full support | The agent captures Kafka record sends and polls. Kafka streams are not traced.                             | 1.13.0                                               |
| RabbitMQ  | ≥ 3.x, ≤ 5.x                                                  | The agent captures RabbitMQ Message sends, consumption and polling                                         | 1.20.0                                               |
| AWS SQS   | ≥ 1.x, ≤ 2.x                                                  | The agent captures SQS Message sends and polling as well as SQS message sends and consumption through JMS. | 1.34.0, 2.21+ since 1.44.0                           |


### Distributed Tracing [_distributed_tracing]

The Java agent instrumentation for messaging system clients includes both senders and receivers. When an instrumented client sends a message within a traced transaction, a `send` span is created. In addition, if the messaging system supports message/record headers/annotations, the agent would add the `tracecontext` headers to enable distributed tracing.

On the receiver side, instrumented clients will attempt to create the proper distributed trace linkage in one of several ways, depending on how messages are received:

* *Passive message handling:* when the message handling logic is applied by implementing a passive message listener API (like `javax.jms.MessageListener#onMessage` for example), creating the receiver transaction is mostly straightforward as the instrumented API method invocation encapsulates message handling. Still, there are two use cases to consider:

    * *Single message handling:* when the message listener API accepts a single message, the agent would create a `messaging` typed transaction per each received message, as a child transaction of the `send` span that corresponds the received message and with the same trace ID
    * *Batch message handling:* when the message listener API accepts a batch of messages (like `org.springframework.amqp.core.MessageListener.onMessageBatch` for example), the agent will create a single root transaction (i.e. different trace ID from any of the `send` spans) by default to encapsulate the entire batch handling. The batch processing transaction would be of `messaging` type, containing links*** to all `send` spans that correspond the messages in the batch. This can be changed through the (non-documented) `message_batch_strategy` config option, which accepts either `BATCH_HANDLING` (default) or `SINGLE_HANDLING` to enable the creation of a single child transaction per message.

* *Active message polling:* in some cases, message are consumed from the broker through active polling. Whenever the polling action occurs while there is already an active span, the agent will create a `poll` span and add span links*** to it for each message returned by the poll action that contains `tracecontext` headers. Since such polling APIs don’t provide any indication as to when message handling actually occurs, the agent needs to apply some heuristics in order to trace message handling. There are two use cases to consider in this type of message receiving as well:

    * *Polling returns a single message:* in such cases, the agent may apply assumptions with regard to the threads that execute the message handling logic in order to determine when handling starts and ends. Based on that, it would create a transaction per consumed message. If the consumed message contains the `tracecontext` headers, the `receive` transaction will be a child of the corresponding `send` span.
    * *Polling returns a message batch:* typically, in such cases the agent will wrap the message collection and rely on the actual iteration to create a transaction per message as the child of the corresponding `send` span and as part of the same trace. If iteration occurs while there is already an active span, then the agent will add a link*** for each message `send` span to the active (parent) span instead of creating transaction/span per message.


*** Span links are supported by APM Server and Kibana since version 8.3 and by the Java agent since version 1.32.0


### RabbitMQ Specifics [_rabbitmq_specifics]

* `context.message.queue.name` field will contain queue name when using polling, exchange name otherwise.
* `context.message.destination.resource` field will contain `rabbitmq/XXX` where `XXX` is exchange name.

Some exchange/queue names are normalized in order to keep low cardinality and user-friendlyness - default exchange is indicated with `<default>`. - `null` exchange is normalized to `<unknown>`, for example when polling without a message. - generated queues whose name start with `amq.gen-` are normalized to `amq.gen-*`.


## Scheduling frameworks [supported-scheduling-frameworks]

When using a scheduling framework a transaction for every execution will be created.

| Framework             | Supported versions | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Since                                      |
|-----------------------|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| Scheduling Annotation |                    | The agent instruments any method defined in a package configured in [`application_packages`](/reference/config-stacktrace.md#config-application-packages) and annotated with one of the following:`org.springframework.scheduling.annotation.Scheduled``org.springframework.scheduling.annotation.Schedules``javax.ejb.Schedule``javax.ejb.Schedules``jakarta.ejb.Schedule``jakarta.ejb.Schedules` in order to create a transaction with the type `scheduled`, representing the scheduled task execution                                                           | 1.6.0, `jakarta.ejb.Schedule` since 1.28.0 |
| Quartz                | 1.0+               | The agent instruments the `execute` method of any class implementing `org.quartz.Job`, as well as the `executeInternal` method of any class extending `org.springframework.scheduling.quartz.QuartzJobBean`, and creates a transaction with the type `scheduled`, representing the job execution<br>NOTE: only classes from the quartz-jobs dependency will be instrumented automatically. For the instrumentation of other jobs the package must be added to the [`application_packages`](/reference/config-stacktrace.md#config-application-packages) parameter. | 1.8.0 - 2.0+<br>1.26.0 - 1.0+              |
| TimerTask             |                    | The agent instruments the `run` method in a package configured in [`application_packages`](/reference/config-stacktrace.md#config-application-packages) of any class extending `java.util.TimerTask`, and creates a transaction with the type `scheduled`, representing the job execution                                                                                                                                                                                                                                                                          | 1.18.0                                     |


## Logging frameworks [supported-logging-frameworks]

There are multiple log-related features in the agent and their support depend on the logging framework:

* **[Correlation](/reference/logs.md#log-correlation-ids)**: The agent automatically injects `trace.id`, `transaction.id` and `error.id` into the MDC implementation (see below for framework specific MDC implementations used. MDC = Mapped Diagnostic Context, a standard way to enrich log messages with additional information). For service correlation, the agent sets values for `service.name`, `service.version` and `service.environment`, using ECS log format is required (`ecs-logging-java` or reformatting).
* **[Error capturing](/reference/logs.md#log-error-capturing)**: Automatically captures exceptions for calls like `logger.error("message", exception)`.
* **[Reformatting](/reference/config-logging.md#config-log-ecs-reformatting)**: When [`log_ecs_reformatting`](/reference/config-logging.md#config-log-ecs-reformatting) is enabled, logs will be automatically reformatted into ECS-compatible format.

| Framework                 | Supported versions                                         | Description                                                                                                                                                            | Since                                                                                                                                                              |
|---------------------------|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| slf4j                     | ≥ 1.4.1                                                    |                                                                                                                                                                        | Error capturing - 1.10.0                                                                                                                                           |
| log4j2                    | Correlation - 2.0+<br>Reformatting - 2.6+, < 3.x           | [`org.apache.logging.log4j.ThreadContext`](https://logging.apache.org/log4j/2.x/manual/thread-context.md) is used for correlation.                                     | Correlation (traces) - 1.13.0<br>Correlation (service) - 1.29.0<br>Error capturing - 1.10.0<br>Reformatting - 1.22.0                                               |
| log4j1                    | Correlation & error capture - 1.x<br>Reformatting - 1.2.17 | [`org.apache.log4j.MDC`](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.md) is used for correlation.                                                | Correlation (traces) - 1.13.0<br>Correlation (service) - 1.38.0<br>Reformatting - 1.22.0<br>Error capturing - 1.30.0                                               |
| Logback                   | ≥ 1.1.x, ≤ 1.4.x                                           | [`org.slf4j.MDC`](https://www.slf4j.org/api/org/slf4j/MDC.md) is used for correlation.                                                                                 | Correlation (traces) - 1.0.0<br>Correlation (service) - 1.38.0<br>ECS Reformatting - 1.22.0                                                                        |
| JBoss Logging             | 3.x                                                        | [`org.jboss.logging.MDC`](http://javadox.com/org.jboss.logging/jboss-logging/3.3.0.Final/org/jboss/logging/MDC.md) is used for correlation.                            | Correlation (traces) - 1.23.0 (LogManager 1.30.0)<br>Correlation (service) - 1.38.0<br>Reformatting - 1.31.0                                                       |
| JUL - `java.util.logging` | All supported Java versions                                | Correlation is only supported with ECS logging (library or reformatting) as JUL doesnot provide any MDC implementation.                                                | Correlation (traces) - 1.35.0 (requires ECS logging)<br>Correlation (service) - 1.38.0 (requires ECS logging)<br>Reformatting - 1.31.0<br>Error capturing - 1.31.0 |
| Tomcat JULI               | ≥ 7.x                                                      | Trace correlation is only supported with ECS logging (library or reformatting) as JUL doesnot provide any MDC implementation.<br>Tomcat access logs are not supported. | Correlation (traces) - 1.35.0 (requires ECS logging)<br>Correlation (service) - 1.38.0 (requires ECS logging)<br>Reformatting - 1.35.0<br>Error capturing - 1.35.0 |


## Process frameworks [supported-process-frameworks]

| Framework           | Supported versions | Description                                                                                             | Since  |
|---------------------|--------------------|---------------------------------------------------------------------------------------------------------|--------|
| `java.lang.Process` |                    | Instruments `java.lang.Process` execution. Java 9 API using `ProcessHandler` is not supported yet.      | 1.13.0 |
| Apache commons-exec | 1.3.x              | Async process support through `org.apache.commons.exec.DefaultExecutor` and subclasses instrumentation. | 1.13.0 |


## RPC frameworks [supported-rpc-frameworks]

| Framework | Supported versions | Description                                                                                                    | Since  |
|-----------|--------------------|----------------------------------------------------------------------------------------------------------------|--------|
| gRPC      | ≥ 1.6.1            | Client (synchronous & asynchronous) & Server instrumentation.  Streaming calls are currently not instrumented. | 1.16.0 |


## AWS Lambda runtimes [supported-aws-lambda-runtimes]

AWS Lambda provides multiple [JVM base images](https://docs.aws.amazon.com/lambda/latest/dg/java-image.md). Only those that support the `AWS_LAMBDA_EXEC_WRAPPER` environment variables are supported out of the box.

Running with unsupported images is still possible but requires providing agent configuration through environment variables explicitly.

| Tags  | Java Runtime       | Operating System     | Supported |
|-------|--------------------|----------------------|-----------|
| 11    | Java 11 (Corretto) | Amazon Linux 2       | yes       |
| 8.al2 | Java 8 (Corretto)  | Amazon Linux 2       | yes       |
| 8     | Java 8 (OpenJDK)   | Amazon Linux 2018.03 | no        |


## Java method monitoring [supported-java-methods]

If you are seeing gaps in the span timeline and want to include additional methods, there are several options. See [*How to find slow methods*](/reference/how-to-find-slow-methods.md) for more information.


## Metrics [supported-metrics]

| Framework        | Description                                                                                                                                                 | Since  |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| Built-in metrics | The agent sends various system, JVM, and application metrics. See the [metrics](/reference/metrics.md) documentation.                                       | 1.3.0  |
| JMX              | Set the configuration option [`capture_jmx_metrics`](/reference/config-jmx.md#config-capture-jmx-metrics) in order to monitor any JMX metric.               | 1.11.0 |
| Micrometer       | Automatically detects and reports the metrics of each `MeterRegistry`. See [Micrometer metrics](/reference/metrics.md#metrics-micrometer) for more details. | 1.18.0 |


## Caveats [supported-technologies-caveats]

* Other JVM languages, like Scala, Kotlin and Groovy have not been tested yet.
