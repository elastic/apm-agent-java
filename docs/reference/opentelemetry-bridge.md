---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/opentelemetry-bridge.html
---

# OpenTelemetry bridge [opentelemetry-bridge]

The Elastic APM OpenTelemetry bridge allows creating Elastic APM `Transactions` and `Spans` using the OpenTelemetry API. OpenTelemetry metrics are also collected. In other words, it translates the calls to the OpenTelemetry API to Elastic APM and thus allows for reusing existing instrumentation.

::::{note}
While manual instrumentations using the OpenTelemetry API can be adapted to the Elastic APM Java agent, it’s not possible to use the instrumentations from [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) in the context of the Elastic APM Java agent.<br> However, you can use [opentelemetry-java-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation) (aka the OpenTelemetry Java agent) and send the data to APM Server. See the [OpenTelemetry integration docs](docs-content://solutions/observability/apm/use-opentelemetry-with-apm.md) for more details.
::::


The first span of a service will be converted to an Elastic APM [`Transaction`](docs-content://solutions/observability/apm/transactions.md), subsequent spans are mapped to Elastic APM [`Span`](docs-content://solutions/observability/apm/spans.md).


## Getting started [otel-getting-started]

The first step in getting started with the OpenTelemetry API bridge is to declare a dependency to the API:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>${version.opentelemetry}</version>
</dependency>
```

```groovy
compile "io.opentelemetry:opentelemetry-api:$openTelemetryVersion"
```

The minimum required OpenTelemetry version is 1.4.0.


## Initialize tracer [otel-init-tracer]

There’s no separate dependency needed for the bridge itself. The Java agent hooks into `GlobalOpenTelemetry` to return its own implementation of `OpenTelemetry` that is connected to the internal tracer of the agent.

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
Tracer tracer = openTelemetry.getTracer("");
```

To disable that behavior, and to rely on the standard discovery mechanism of `GlobalOpenTelemetry`, you can set [`disable_instrumentations`](/reference/config-core.md#config-disable-instrumentations) to `opentelemetry`.


## Add custom metadata to a span [otel-set-attribute]

If you like the spans created by the Elastic APM Java agent’s auto-instrumentation, but you want to add a custom label, you can use the OpenTelemetry API to get ahold of the current span and call `setAttribute`:

```java
Span.current().setAttribute("foo", "bar");
```


## Customize span tracing [otel-set-behavioral-attribute]

We utilize the `setAttribute()` API not only to [add custom metadata](#otel-set-attribute), but also as a way to customize some special tracing features through corresponding custom attributes listed below. Such attributes are not added to span metadata. For example:

```java
Span.current().setAttribute("co.elastic.discardable", false);
```


### `co.elastic.discardable` [otel-config-discardable]

By default, spans may be discarded, for example if [`span_min_duration` ([1.16.0])](/reference/config-core.md#config-span-min-duration) is set and the span does not exceed the configured threshold. Use this attribute to make a span non-discardable by setting it to `false`.

::::{note}
making a span non-discardable implicitly makes the entire stack of active spans non-discardable as well. Child spans can still be discarded.
::::


| Key | Value type | Default |
| --- | --- | --- |
| `co.elastic.discardable` | `boolean` | `true` |


## Create a child of the active span [otel-create-transaction-span]

This is an example for adding a custom span to the span created by the Java agent’s auto-instrumentation.

```java
// if there's an active span, it will implicitly be the parent
// in case there's no parent, the custom span will become a Elastic APM transaction
Span custom = tracer.spanBuilder("my custom span").startSpan();
// making your child the current one makes the Java agent aware of this span
// if the agent creates spans in the context of myTracedMethod() (such as outgoing requests),
// they'll be added as a child of your custom span
try (Scope scope = custom.makeCurrent()) {
    myTracedMethod();
} catch (Exception e) {
    custom.recordException(e);
    throw e;
} finally {
    custom.end();
}
```

To learn more about the OpenTelemetry API, head over do [their documentation](https://opentelemetry.io/docs/java/manual_instrumentation/).


## Metrics [otel-metrics]

::::{warning}
This functionality is in technical preview and may be changed or removed in a future release. Elastic will work to fix any issues, but features in technical preview are not subject to the support SLA of official GA features.
::::


The Elastic APM Java Agent supports collecting metrics defined via OpenTelemetry. You can either use the [OpenTelemetry API](#otel-metrics-api) or the [OpenTelemetry SDK](#otel-metrics-sdk) in case you need more customizations.

In both cases the Elastic APM Agent will respect the [`disable_metrics`](/reference/config-reporter.md#config-disable-metrics) and [`metrics_interval`](/reference/config-reporter.md#config-metrics-interval) settings for OpenTelemetry metrics.

You can use the [`custom_metrics_histogram_boundaries`](/reference/config-metrics.md#config-custom-metrics-histogram-boundaries) setting to customize histogram bucket boundaries. Alternatively you can use OpenTelemetry `Views` to define histogram buckets on a per-metric basis when providing your own `MeterProvider`. Note that  `custom_metrics_histogram_boundaries` will only work for API Usages. If you bring your own `MeterProvider` and therefore your own OpenTelemetry SDK, the setting will only work for SDK versions prior to `1.32.0`.


## API Usage [otel-metrics-api]

You can define metrics and report metric data via `GlobalOpenTelemetry`:

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

Meter myMeter = GlobalOpenTelemetry.getMeter("my_meter");
LongCounter counter = meter.counterBuilder("my_counter").build();
counter.add(42);
```

We don’t require you to setup an OpenTelemetry `MeterProvider` within `GlobalOpenTelemetry` yourself. The Elastic APM Java Agent will detect if no `MeterProvider` was configured and will provide its own automatically in this case. If you provide your own `MeterProvider` (see [Using a customized MeterProvider](#otel-metrics-sdk)), the agent will use the provided instance.


## Using a customized MeterProvider [otel-metrics-sdk]

In some cases using just the [OpenTelemetry API for metrics](#otel-metrics-api) might not be flexible enough. Some example use cases are:

* Using OpenTelemetry Views
* Exporting metrics to other tools in addition to Elastic APM (e.g. prometheus)

For these use cases you can just setup you OpenTelemetry SDK `MeterProvider`. The Elastic APM Agent will take care of installing an additional `MetricExporter` via instrumentation, which will ship the metric data to Elastic APM. This requires using OpenTelemetry version `1.16.0` or newer.

To create your own `MeterProvider`, you will need to add the OpenTelemetry Metric SDK as dependency to your project:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-metrics</artifactId>
    <version>${version.opentelemetry}</version>
</dependency>
```

```groovy
compile "io.opentelemetry:opentelemetry-sdk-metrics:$openTelemetryVersion"
```

Afterwards you can create and use your own `MeterProvider` as shown below:

```java
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;

//Elastic APM MetricReader will be registered automatically by the agent
SdkMeterProvider meterProvider = SdkMeterProvider.builder()
    .registerMetricReader(PrometheusHttpServer.create())
    .registerView(
        InstrumentSelector.builder().setName("my_histogram").build(),
        View.builder().setAggregation(Aggregation.explicitBucketHistogram(List.of(1.0, 5.0))).build()
    )
    .build();

Meter testMeter = meterProvider.get("my_meter");
DoubleHistogram my_histogram = testMeter.histogramBuilder("my_histogram").build();

my_histogram.record(0.5);
```


## Caveats [otel-caveats]

Not all features of the OpenTelemetry API are supported.


### In process context propagation [otel-propagation]

Entries that are added to the current context, `Context.current().with(...).makeCurrent()` cannot be retrieved via `Context.current().get(...)`.


### Span References [otel-references]

Spans can only have a single parent (`SpanBuilder#setParent`)


### Baggage [otel-baggage]

Baggage support has been added in version `1.41.0`. Since `1.43.0` you can automatically attach baggage as span, transaction and error attributes via the [`baggage_to_attach`](/reference/config-core.md#config-baggage-to-attach) configuration option.


### Events [otel-events]

Events are silently dropped, for example `Span.current().addEvent("my event")`.


### Annotations [otel-anntations]

[OpenTelemetry instrumentation annotations](https://opentelemetry.io/docs/instrumentation/java/automatic/annotations/) started supported since `1.45.0`.

