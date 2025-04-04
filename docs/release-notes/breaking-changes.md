---
navigation_title: "Breaking changes"
---

# Elastic APM Java Agent breaking changes

Before you upgrade, carefully review the Elastic APM Java Agent breaking changes and take the necessary steps to mitigate any issues.

To learn how to upgrade, check out [Upgrading](/reference/upgrading.md).

% ## Next version [next-version]
% **Release date:** Month day, year

% ::::{dropdown} Title of breaking change
% Description of the breaking change.
% For more information, check [PR #](PR link).
% **Impact**<br> Impact of the breaking change.
% **Action**<br> Steps for mitigating deprecation impact.
% ::::

## 1.48.0 [1.48.0]

**Release date**: March 05, 2024

* The minimum supported OpenTelemetry version has been increased to 1.4.0 - [#3535](https://github.com/elastic/apm-agent-java/pull/3535)

## 1.45.0 [1.45.0]

**Release date**: December 07, 2023

* Added support for OpenTelemetry `1.32.0`. As a result, `custom_metrics_histogram_boundaries` will not
work when you bring your own `MeterProvider` from an SDK with version `1.32.0` or newer. As a workaround,
you should manually register a corresponding View in your `MeterProvider`. Note that this change will not
affect you, if you are using the OpenTelemetry API only and not the SDK. - [#3447](https://github.com/elastic/apm-agent-java/pull/3447)

## 1.37.0 [1.37.0]

**Release date**: April 11, 2023

* The list of instrumentation groups for log-related features have been simplified to `logging`, `log-reformatting`, `log-correlation` and `log-error`,
as a consequence any agent configuration with `enable_instrumentations` or `disable_instrumentations` might require adjustments as the following instrumentation
groups no longer exist: `jul-ecs`, `jul-error`, `log4j1-correlation`, `log4j1-ecs`, `log4j1-error`, `log4j2-correlation`, `log4j2-ecs`, `log4j2-error`,
`logback-correlation`, `logback-ecs` and `slf4j-error`.

## 1.36.0 [1.36.0]

**Release date**: January 27, 2023

* Previously, agent instrumented all implementations of `javax.jms.MessageListener`, which could cause performance issues.
From now on, the instrumentation is limited to implementations that fit at least one of the following criteria:
    * have `Message` or `Listener` in their class name
    * is an anonymous inner class or a lambda
    * is within a package provided by the `jms_listener_packages` configuration.
* The `context.destination.service.resource` span field has been changed for AWS S3, SQS and DynamoDB.
As a result, the history of metrics relying on this field might break. - [#2947](https://github.com/elastic/apm-agent-java/pull/2947)

## 1.33.0 [1.33.0]

**Release date**: July 08, 2022

As of version 1.33.0, Java 7 support is deprecated and will be removed in a future release (not expected to be removed before January 2024) - [#2677](https://github.com/elastic/apm-agent-java/pull/2677)

## 1.32.0 [1.32.0]

**Release date**: June 13, 2022

* If using the public API of version < 1.32.0 and using the `@CaptureSpan` or `@Traced` annotations, upgrading the agent to 1.32.0
without upgrading the API version may cause `VerifyError`. This was fixed in version 1.33.0, which can once again be used with any
version of the public API.
* For relational databases, the agent now captures the database name and makes it part of service dependencies and service map.
For example, with a `MySQL` database, previously a single `mysql` item was shown in the map and in service dependencies,
the agent will now include the database name in the dependency, thus `mysql/my-db1`, `mysql/my-db2` will now be captured.

## 1.31.0 [1.31.0]

**Release date**: May 17, 2022

* Starting this version, when using [`log_ecs_reformatting`](/reference/config-logging.md#config-log-ecs-reformatting), the agent will automatically set the `service.version` field.
If you are using ECS-logging and set the `service.version` through a custom field, the behaviour is not strictly defined. Remove the
custom `service.name` field setting and either allow the agent to automatically discover and set it, or use the
[`service_version`](/reference/config-core.md#config-service-version) config option to set it manually.

## 1.30.0 [1.30.0]

**Release date**: March 22, 2022

* Create the JDBC spans as exit spans- [#2484](https://github.com/elastic/apm-agent-java/pull/2484)
* WebSocket requests are now captured with transaction type request instead of custom - [#2501](https://github.com/elastic/apm-agent-java/pull/2501)

## 1.29.0 [1.29.0]

**Release date**: February 09, 2022

* Changes in service name auto-discovery of jar files (see Features section)

## 1.28.3 [1.28.3]

**Release date**: December 22, 2021

* If the agent cannot discover a service name, it now uses `unknown-java-service` instead of `my-service` - [#2325](https://github.com/elastic/apm-agent-java/pull/2325)

## 1.27.0 [1.27.0]

**Release date**: November 15, 2021

* `transaction_ignore_urls` now relies on full request URL path - [#2146](https://github.com/elastic/apm-agent-java/pull/2146)
    * On a typical application server like Tomcat, deploying an `app.war` application to the non-ROOT context makes it accessible with `http://localhost:8080/app/`
    * Ignoring the whole webapp through `/app/*` was not possible until now.
    * Existing configuration may need to be updated to include the deployment context, thus for example `/static/*.js` used to
exclude known static files in all applications might be changed to `/app/static/*.js` or `*/static/*.js`.
    * It only impacts prefix patterns due to the additional context path in pattern.
    * It does not impact deployment within the `ROOT` context like Spring-boot which do not have such context path prefix.
* The metrics `transaction.duration.sum.us`, `transaction.duration.count` and `transaciton.breakdown.count` are no longer recorded - [#2194](https://github.com/elastic/apm-agent-java/pull/2194)
* Automatic hostname discovery mechanism had changed, so the resulted `host.name` and `host.hostname` in events reported
by the agent may be different. This was done in order to improve the integration with host metrics in the APM UI.

## 1.26.0 [1.26.0]

**Release date**: September 14, 2021

* If you rely on Database span subtype and use Microsoft SQL Server, the span subtype has been changed from `sqlserver`
to `mssql` to align with other agents.
* Stop collecting the field `http.request.socket.encrypted` in http requests - [#2136](https://github.com/elastic/apm-agent-java/pull/2136)

## 1.25.0 [1.25.0]

**Release date**: July 22, 2021

* If you rely on instrumentations that are in the `experimental` group, you must now set `enable_experimental_instrumentations=true` otherwise
the experimental instrumentations will be disabled by default. Up to version `1.24.0` using an empty value for `disable_instrumentations` was
the recommended way to override the default `disable_instrumentations=experimental`.

## 1.23.0 [1.23.0]

**Release date**: April 22, 2021

* There are breaking changes in the [attacher cli](/reference/setup-attach-cli.md).
  See the Features section for more information.

## 1.22.0 [1.22.0]

**Release date**: March 24, 2021

* Dots in metric names of Micrometer metrics get replaced with underscores to avoid mapping conflicts.
De-dotting be disabled via [`dedot_custom_metrics`](/reference/config-metrics.md). - [#1700](https://github.com/elastic/apm-agent-java/pull/1700)

## 1.21.0 [1.21.0]

**Release date**: February 09, 2021

* Following PR [#1650](https://github.com/elastic/apm-agent-java/pull/1650), there are two slight changes with the [`server_url`](/reference/config-reporter.md#config-server-url) and [`server_urls`](/reference/config-reporter.md#config-server-urls)
configuration options:
    1.  So far, setting `server_urls` with an empty string would allow the agent to work normally, apart from any action
        that requires communication with the APM Server, including the attempt to fetch a central configuration.
        Starting in this agent version, setting `server_urls` to empty string doesn't have any special meaning, it is
        the default expected configuration, where `server_url` will be used instead. In order to achieve the same
        behaviour, use the new [`disable_send`](/reference/config-reporter.md#config-disable-send) configuration.
    2.  Up to this version, `server_url` was used as an alias to `server_urls`, meaning that one could potentially set
        the `server_url` config with a comma-separated list of multiple APM Server addresses, and that would have been a
        valid configuration. Starting in this agent version, `server_url` is a separate configuration, and it only accepts
        Strings that represent a single valid URL. Specifically, empty strings and commas are invalid.

## 1.20.0 [1.20.0]

**Release date**: January 07, 2021

* The following public API types were `public` so far and became package-private: `NoopScope`, `ScopeImpl` and `AbstractSpanImpl`.
  If your code is using them, you will need to change that when upgrading to this version.
  Related PR: [#1532](https://github.com/elastic/apm-agent-java/pull/1532)

## 1.18.0.RC1 [1.18.0.rc1]

**Release date**: July 22, 2020

* Early Java 7 versions, prior to update 60, are not supported anymore.
  When trying to attach to a non-supported version, the agent will disable itself and not apply any instrumentations.

## 1.15.0 [1.15.0]

**Release date**: March 27, 2020

* Ordering of configuration sources has slightly changed, please review [Configuration](/reference/configuration.md):
    * `elasticapm.properties` file now has higher priority over java system properties and environment variables, +
This change allows to change dynamic options values at runtime by editing file, previously values set in java properties
or environment variables could not be overridden, even if they were dynamic.
* Renamed some configuration options related to the experimental profiler-inferred spans feature ([#1084](https://github.com/elastic/apm-agent-java/pull/1084)):
    * `profiling_spans_enabled` -> `profiling_inferred_spans_enabled`
    * `profiling_sampling_interval` -> `profiling_inferred_spans_sampling_interval`
    * `profiling_spans_min_duration` -> `profiling_inferred_spans_min_duration`
    * `profiling_included_classes` -> `profiling_inferred_spans_included_classes`
    * `profiling_excluded_classes` -> `profiling_inferred_spans_excluded_classes`
    * Removed `profiling_interval` and `profiling_duration` (both are fixed to 5s now)

## 1.9.0 [1.9.0]

**Release date**: August 22, 2019

* The `apm-agent-attach.jar` is not executable anymore.
Use `apm-agent-attach-standalone.jar` instead.

## 1.8.0 [1.8.0]

**Release date**: July 30, 2019
* The log correlation feature does not add `span.id` to the MDC anymore but only `trace.id` and `transaction.id` [#742](https://github.com/elastic/apm-agent-java/pull/742).

## 1.5.0 [1.5.0]

**Release date**: March 26, 2019

* If you didn't explicitly set the [`service_name`](/reference/config-core.md#config-service-name)
previously and you are dealing with a servlet-based application (including Spring Boot),
your `service_name` will change.
See the documentation for [`service_name`](/reference/config-core.md#config-service-name)
and the corresponding section in _Features_ for more information.
Note: this requires APM Server 7.0+. If using previous versions, nothing will change.

## 1.0.0 [1.0.0]

**Release date**: November 14, 2018

* Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
   Until the time the APM Server 6.5.0 is officially released,
   you can test with docker by pulling the APM Server image via
   `docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT`.

## 1.0.0.RC1 [1.0.0.rc1]

**Release date**: November 06, 2018

* Remove intake v1 support. This version requires APM Server 6.5.0+ which supports the intake api v2.
   Until the time the APM Server 6.5.0 is officially released,
   you can test with docker by pulling the APM Server image via
   `docker pull docker.elastic.co/apm/apm-server:6.5.0-SNAPSHOT`.
* Wildcard patterns are case insensitive by default. Prepend `(?-i)` to make the matching case sensitive.
