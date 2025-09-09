---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/logs.html
applies_to:
  stack:
  serverless:
    observability:
  product:
    apm_agent_java: ga
products:
  - id: cloud-serverless
  - id: observability
  - id: apm  
---

# Logs [logs]

Elastic Java APM Agent provides the following log features:

* [Log correlation](#log-correlation-ids) : Automatically inject correlation IDs that allow navigation between logs, traces and services.
* [Log reformatting (experimental)](#log-reformatting) : Automatically reformat plaintext logs in [ECS logging](ecs-logging://reference/intro.md) format.
* [Error capturing](#log-error-capturing) : Automatically captures exceptions for calls like `logger.error("message", exception)`.
* [Log sending (experimental)](#log-sending) : Automatically send logs to APM Server without filebeat

Those features are part of [Application log ingestion strategies](docs-content://solutions/observability/logs/stream-application-logs.md).

The [`ecs-logging-java`](ecs-logging-java://reference/index.md) library can also be used to use the [ECS logging](ecs-logging://reference/intro.md) format without an APM agent. When deployed with the Java APM agent, the agent will provide [log correlation](#log-correlation-ids) IDs.


## Log correlation [log-correlation-ids]

[Log correlation](docs-content://solutions/observability/apm/logs.md) allows you to navigate to all logs belonging to a particular trace and vice-versa: for a specific log, see in which context it has been logged and which parameters the user provided.

::::{note}
Starting in APM agent version 1.30.0, log correlation is enabled by default. In previous versions, log correlation must be explicitly enabled by setting the `enable_log_correlation` configuration variable to `true`.
::::


In order to correlate logs from your application with traces and errors captured by the Elastic APM Java Agent, the agent injects the following IDs into [slf4j-MDC](https://www.slf4j.org/api/org/slf4j/MDC.md)-equivalents of [supported logging frameworks](/reference/supported-technologies.md#supported-logging-frameworks):

* [`transaction.id`](ecs://reference/ecs-tracing.md)
* [`trace.id`](ecs://reference/ecs-tracing.md)
* [`error.id`](ecs://reference/ecs-error.md)

For frameworks that don’t provide an MDC like `java.util.logging` (JUL), correlation is only supported when using ECS logging library or with [Log reformatting](#log-reformatting).

For plain text logs, the pattern layout of your logging configuration needs to be modified to write the MDC values into log files. If you are using Logback or log4j, add `%X` to the format to log all MDC values or `%X{trace.id}` to only log the trace id.

When the application is logging in ECS format (by using `ecs-logging-java` or [log reformatting](#log-reformatting)) but does not provide the service fields, then the agent will automatically provide fallback values from its own configuration to provide service-level correlation:

* [`service.name`](ecs://reference/ecs-service.md) value provided [`service_name`](/reference/config-core.md#config-service-name) in agent config.
* [`service.version`](ecs://reference/ecs-service.md) value provided by [`service_version`](/reference/config-core.md#config-service-version) in agent config.
* [`service.environment`](ecs://reference/ecs-service.md) value provided by [`environment`](/reference/config-core.md#config-environment) in agent config.


## Log reformatting (experimental) [log-reformatting]

The agent can automatically reformat application logs to ECS format, without adding a dependency to `ecs-logging-java`, modifying the application logging configuration and making the application always use ECS log format. In short, it provides the benefits of ECS logging at runtime without any change to the application.

Log reformatting is controlled by the [`log_ecs_reformatting`](/reference/config-logging.md#config-log-ecs-reformatting) configuration option, and is disabled by default.

The reformatted logs will include both the [trace and service correlation](#log-correlation-ids) IDs.


## Error capturing [log-error-capturing]

The agent automatically captures exceptions sent to loggers with calls like `logger.error("message", exception)`.

When doing so, the `error.id` is added to the MDC as well for [log correlation](#log-correlation-ids) since 1.16.0.

As a result, when an exception is reported to the logger:

* The agent reports an error to APM server with the provided exception
* An `error.id` is generated and injected into logger MDC for the duration of the logger invocation
* Logger output will contain the `error.id` if the log format allows it (plaintext still requires some configuration)

Please note we capture the exception, not the message passed to the `logger.error`.

To collect the message passed to the `logger.error`, you would need to ingest the logs of the application (see [Log correlation](docs-content://solutions/observability/apm/logs.md)).


## Log sending (experimental) [log-sending]

The agent can automatically capture and send logs directly to APM Server, which allows to ingest log events without relying on filebeat. Log sending is controlled by the [`log_sending`](/reference/config-logging.md#config-log-sending) configuration option and is disabled by default.

