---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/opentracing-bridge.html
---

# OpenTracing bridge [opentracing-bridge]

::::{note}
OpenTracing is discontinued in favor of OpenTelemetry. Consider using the [OpenTelemetry bridge](/reference/opentelemetry-bridge.md) instead.<br> Latest supported OpenTracing version: 0.33 (as of agent and OpenTracing-bridge version 1.9.0)
::::


The Elastic APM OpenTracing bridge allows creating Elastic APM `Transactions` and `Spans`, using the OpenTracing API. In other words, it translates the calls to the OpenTracing API to Elastic APM and thus allows for reusing existing instrumentation.

The first span of a service will be converted to an Elastic APM [`Transaction`](docs-content://solutions/observability/apm/transactions.md), subsequent spans are mapped to Elastic APM [`Span`](docs-content://solutions/observability/apm/spans.md).


## Getting started [opentracing-getting-started]

The first step in getting started with the OpenTracing API bridge is to declare a dependency to the API:

```xml
<dependency>
    <groupId>co.elastic.apm</groupId>
    <artifactId>apm-opentracing</artifactId>
    <version>${elastic-apm.version}</version>
</dependency>
```

```groovy
compile "co.elastic.apm:apm-opentracing:$elasticApmVersion"
```

Replace the version placeholders with the [latest version from maven central](https://mvnrepository.com/artifact/co.elastic.apm/apm-opentracing/latest): ![Maven Central](https://img.shields.io/maven-central/v/co.elastic.apm/apm-opentracing.svg "")


## Initialize tracer [init-tracer]

```java
import co.elastic.apm.opentracing.ElasticApmTracer;
import io.opentracing.Tracer;

Tracer tracer = new ElasticApmTracer();
```

Since version 1.22.0, the OpenTracing bridge supports `Tracer` lookup and initialization through the ServiceLoader mechanism. An example for a system that relies on this capability is [tracer-resolver](https://github.com/opentracing-contrib/java-tracerresolver), which is used by various OpenTracing libraries, for example the [Apache Camel OpenTracing component](https://camel.apache.org/components/3.7.x/others/opentracing.md). When such is used, no code changes are required, only the addition of dependencies for the OpenTracing library and the Elastic OpenTracing bridge.


## Elastic APM specific tags [opentracing-elastic-apm-tags]

Elastic APM defines some tags which are not included in the OpenTracing API but are relevant in the context of Elastic APM.

* `type` - sets the type of the transaction/span, for example `request`, `ext` or `db`
* `subtype` - sets the subtype of the span, for example `http`, `mysql` or `jsf`
* `action` - sets the action related to a span, for example `query`, `execute` or `render`
* `user.id` - sets the user id, appears in the "User" tab in the transaction details in the Elastic APM app
* `user.email` - sets the user email, appears in the "User" tab in the transaction details in the Elastic APM app
* `user.username` - sets the user name, appears in the "User" tab in the transaction details in the Elastic APM app
* `result` - sets the result of the transaction. Overrides the default value of `success`. If the `error` tag is set to `true`, the default value is `error`. Setting `http.status_code` to `200`, for example, implicitly sets the result to `HTTP 2xx` if not explicitly set otherwise.


## Caveats [opentracing-unsupported]

Not all features of the OpenTracing API are supported.


### Context propagation [opentracing-propagation]

This bridge only supports the formats `Format.Builtin.TEXT_MAP` and `Format.Builtin.HTTP_HEADERS`. `Format.Builtin.BINARY` is currently not supported.


### Span References [opentracing-references]

Currently, this bridge only supports `child_of` references. Other references, like `follows_from` are not supported yet.


### Baggage [opentracing-baggage]

The `Span.setBaggageItem(String, String)` method is not supported. Baggage items are silently dropped.


### Logs [opentracing-logs]

Only exception logging is supported. Logging an Exception on the OpenTracing span will create an Elastic APM [`Error`](docs-content://solutions/observability/apm/errors.md). Example:

```java
Exception e = ...
span.log(
    Map.of(
        "event", "error",
        "error.object", e
    )
)
```

Other logs are silently dropped.

